package allen.town.podcast.parser.media.mp4;

import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import allen.town.podcast.model.feed.Chapter;

/**
 * Reads chapters from MP4/M4A/M4B files.
 *
 * <p>Two formats are understood:
 * <ul>
 *   <li>QuickTime/iTunes chapter tracks: a text track referenced through {@code tref/chap},
 *   whose samples are the chapter titles and whose sample times are the chapter starts.
 *   This is what Apple tools and most m4b tools write, so it wins when both are present.</li>
 *   <li>Nero chapters: {@code moov/udta/chpl}, a flat list of start (100 ns units) and title.</li>
 * </ul>
 *
 * <p>Only the {@code moov} box is parsed; top-level boxes before it (usually {@code mdat})
 * are skipped with {@link Mp4Source#seek}. Of the sample tables only the small ones are kept
 * in memory (an audio track's tables are skipped when large), so a long audiobook does not
 * cost tens of megabytes. Chapter-track samples are read in ascending file offset, which lets
 * a forward-only source work when they come after {@code moov} (fast-start files).
 */
public final class Mp4ChapterReader {

    /** Largest leaf box (sample table, chpl, ...) kept in memory. */
    static final int MAX_LEAF_BOX = 1024 * 1024;
    /** Upper bound on the number of chapters returned. */
    static final int MAX_CHAPTERS = 10_000;
    /** Largest chapter-track sample read (2-byte length + text). */
    private static final int MAX_SAMPLE = 2 + 0xFFFF;
    private static final int BOX_HEADER = 8;

    private static final int FTYP = fourCc("ftyp");
    private static final int MOOV = fourCc("moov");
    private static final int MDAT = fourCc("mdat");
    private static final int FREE = fourCc("free");
    private static final int SKIP = fourCc("skip");
    private static final int WIDE = fourCc("wide");
    private static final int PDIN = fourCc("pdin");
    private static final int UUID = fourCc("uuid");
    private static final int TRAK = fourCc("trak");
    private static final int TKHD = fourCc("tkhd");
    private static final int TREF = fourCc("tref");
    private static final int CHAP = fourCc("chap");
    private static final int MDIA = fourCc("mdia");
    private static final int MDHD = fourCc("mdhd");
    private static final int HDLR = fourCc("hdlr");
    private static final int MINF = fourCc("minf");
    private static final int STBL = fourCc("stbl");
    private static final int STTS = fourCc("stts");
    private static final int STSZ = fourCc("stsz");
    private static final int STSC = fourCc("stsc");
    private static final int STCO = fourCc("stco");
    private static final int CO64 = fourCc("co64");
    private static final int UDTA = fourCc("udta");
    private static final int CHPL = fourCc("chpl");
    private static final int TEXT = fourCc("text");
    private static final int SBTL = fourCc("sbtl");

    private final Mp4Source source;
    private final List<Track> tracks = new ArrayList<>();
    private List<Chapter> neroChapters = Collections.emptyList();

    private Mp4ChapterReader(Mp4Source source) {
        this.source = source;
    }

    /**
     * Reads the chapters of the MP4 file in {@code source}, starting at its current position.
     *
     * @return the chapters in file order, or an empty list if the data is not MP4, has no
     *         {@code moov} box (e.g. a truncated download) or has no chapters
     * @throws Mp4ReaderException if the box structure inside {@code moov} is corrupt
     * @throws IOException        on read errors, including unexpected end of data inside {@code moov}
     */
    public static List<Chapter> readChapters(Mp4Source source) throws IOException, Mp4ReaderException {
        return new Mp4ChapterReader(source).read();
    }

    private List<Chapter> read() throws IOException, Mp4ReaderException {
        if (!findAndParseMoov()) {
            return Collections.emptyList();
        }
        Track chapterTrack = findChapterTrack();
        if (chapterTrack != null) {
            try {
                List<Chapter> chapters = readTrackChapters(chapterTrack);
                if (!chapters.isEmpty()) {
                    return chapters;
                }
            } catch (IOException | Mp4ReaderException e) {
                if (neroChapters.isEmpty()) {
                    throw e;
                }
                // The Nero list below is a complete substitute for an unreadable chapter track.
            }
        }
        return neroChapters;
    }

    // ---------------------------------------------------------------- box walking

    private static final class Box {
        final int type;
        final long end;

        Box(int type, long end) {
            this.type = type;
            this.end = end;
        }
    }

    /**
     * Reads a box header at the current position.
     *
     * @param parentEnd end of the enclosing box (or of the file); used for size 0, and -1 if unknown
     */
    private Box readBoxHeader(long parentEnd) throws IOException, Mp4ReaderException {
        long start = source.position();
        byte[] header = new byte[BOX_HEADER];
        source.readFully(header, 0, BOX_HEADER);
        ByteBuffer buf = ByteBuffer.wrap(header);
        long size = buf.getInt() & 0xFFFFFFFFL;
        int type = buf.getInt();
        long end;
        if (size == 1) {
            byte[] large = new byte[8];
            source.readFully(large, 0, 8);
            size = ByteBuffer.wrap(large).getLong();
            if (size < BOX_HEADER + 8) {
                throw new Mp4ReaderException("invalid 64-bit box size " + size + " for " + fourCcName(type));
            }
            end = start + size;
        } else if (size == 0) {
            end = parentEnd >= 0 ? parentEnd : Long.MAX_VALUE;
        } else if (size < BOX_HEADER) {
            throw new Mp4ReaderException("invalid box size " + size + " for " + fourCcName(type));
        } else {
            end = start + size;
        }
        if (end < start) {
            throw new Mp4ReaderException("box size overflow for " + fourCcName(type));
        }
        return new Box(type, end);
    }

    /** Reads a child header and checks that it fits in its parent. */
    private Box readChildHeader(long parentEnd) throws IOException, Mp4ReaderException {
        Box box = readBoxHeader(parentEnd);
        if (box.end > parentEnd || source.position() > box.end) {
            throw new Mp4ReaderException("box " + fourCcName(box.type) + " overruns its parent");
        }
        return box;
    }

    /** True while another child header fits before {@code end} (trailing padding is ignored). */
    private boolean hasChild(long end) {
        return end - source.position() >= BOX_HEADER;
    }

    private boolean findAndParseMoov() throws IOException, Mp4ReaderException {
        long length = source.length();
        boolean first = true;
        while (length < 0 || length - source.position() >= BOX_HEADER) {
            Box box;
            try {
                box = readBoxHeader(length);
            } catch (EOFException e) {
                return false; // ran out of data before moov: no chapters to find
            }
            if (first) {
                if (!isTopLevelType(box.type)) {
                    return false; // not an MP4 file
                }
                first = false;
            }
            if (length >= 0 && box.end > length) {
                return false; // truncated file
            }
            if (box.type == MOOV) {
                parseMoov(box.end);
                return true;
            }
            if (box.end == Long.MAX_VALUE) {
                return false; // last box extends to the end and is not moov
            }
            try {
                source.seek(box.end);
            } catch (EOFException e) {
                return false; // truncated stream
            }
        }
        return false;
    }

    private static boolean isTopLevelType(int type) {
        return type == FTYP || type == MOOV || type == MDAT || type == FREE || type == SKIP
                || type == WIDE || type == PDIN || type == UUID;
    }

    private void parseMoov(long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == TRAK) {
                Track track = new Track();
                parseTrak(track, box.end);
                tracks.add(track);
            } else if (box.type == UDTA) {
                parseUdta(box.end);
            }
            source.seek(box.end);
        }
    }

    private void parseUdta(long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == CHPL) {
                byte[] payload = readPayload(box);
                if (payload != null) {
                    neroChapters = parseChpl(payload);
                }
            }
            source.seek(box.end);
        }
    }

    private void parseTrak(Track track, long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == TKHD) {
                byte[] payload = readPayload(box);
                if (payload != null) {
                    track.id = parseTkhd(payload);
                }
            } else if (box.type == TREF) {
                parseTref(track, box.end);
            } else if (box.type == MDIA) {
                parseMdia(track, box.end);
            }
            source.seek(box.end);
        }
    }

    private void parseTref(Track track, long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == CHAP) {
                byte[] payload = readPayload(box);
                if (payload != null) {
                    ByteBuffer buf = ByteBuffer.wrap(payload);
                    while (buf.remaining() >= 4) {
                        track.chapterRefs.add(buf.getInt());
                    }
                }
            }
            source.seek(box.end);
        }
    }

    private void parseMdia(Track track, long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == MDHD) {
                byte[] payload = readPayload(box);
                if (payload != null) {
                    track.timescale = parseMdhd(payload);
                }
            } else if (box.type == HDLR) {
                byte[] payload = readPayload(box);
                if (payload != null && payload.length >= 12) {
                    track.handler = ByteBuffer.wrap(payload, 8, 4).getInt();
                }
            } else if (box.type == MINF) {
                parseMinf(track, box.end);
            }
            source.seek(box.end);
        }
    }

    private void parseMinf(Track track, long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == STBL) {
                parseStbl(track, box.end);
            }
            source.seek(box.end);
        }
    }

    private void parseStbl(Track track, long end) throws IOException, Mp4ReaderException {
        while (hasChild(end)) {
            Box box = readChildHeader(end);
            if (box.type == STTS) {
                track.stts = readPayload(box);
            } else if (box.type == STSZ) {
                track.stsz = readPayload(box);
            } else if (box.type == STSC) {
                track.stsc = readPayload(box);
            } else if (box.type == STCO) {
                track.chunkOffsets = readPayload(box);
                track.chunkOffsets64 = false;
            } else if (box.type == CO64) {
                track.chunkOffsets = readPayload(box);
                track.chunkOffsets64 = true;
            }
            source.seek(box.end);
        }
    }

    /** Reads the rest of {@code box}, or returns null (leaving the position unchanged) if it is too large. */
    private byte[] readPayload(Box box) throws IOException {
        long len = box.end - source.position();
        if (len > MAX_LEAF_BOX) {
            return null;
        }
        byte[] payload = new byte[(int) len];
        source.readFully(payload, 0, payload.length);
        return payload;
    }

    // ---------------------------------------------------------------- leaf boxes

    private static int parseTkhd(byte[] payload) throws Mp4ReaderException {
        try {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            int version = buf.get() & 0xFF;
            buf.position(version == 1 ? 4 + 8 + 8 : 4 + 4 + 4);
            return buf.getInt();
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new Mp4ReaderException("truncated tkhd");
        }
    }

    private static long parseMdhd(byte[] payload) throws Mp4ReaderException {
        try {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            int version = buf.get() & 0xFF;
            buf.position(version == 1 ? 4 + 8 + 8 : 4 + 4 + 4);
            return buf.getInt() & 0xFFFFFFFFL;
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new Mp4ReaderException("truncated mdhd");
        }
    }

    /**
     * Nero chapter list: version (1), flags (3), [version 1: 1 reserved byte + 32-bit count;
     * version 0: 8-bit count], then per chapter a 64-bit start in 100 ns units, an 8-bit title
     * length and the UTF-8 title. ffmpeg writes the version 1 count as 4 zero bytes and an 8-bit
     * count, which reads identically for fewer than 256 chapters; either way the list is parsed
     * only as far as the payload goes.
     */
    static List<Chapter> parseChpl(byte[] payload) {
        if (payload.length < 5) {
            return Collections.emptyList();
        }
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int version = buf.get() & 0xFF;
        buf.position(4);
        long count;
        if (version >= 1) {
            if (buf.remaining() < 5) {
                return Collections.emptyList();
            }
            buf.get();
            count = buf.getInt() & 0xFFFFFFFFL;
        } else {
            count = buf.get() & 0xFF;
        }
        List<Chapter> chapters = new ArrayList<>();
        for (long i = 0; i < count && chapters.size() < MAX_CHAPTERS && buf.remaining() >= 9; i++) {
            long start = buf.getLong();
            int titleLength = buf.get() & 0xFF;
            if (buf.remaining() < titleLength) {
                break;
            }
            String title = new String(payload, buf.position(), titleLength, StandardCharsets.UTF_8).trim();
            buf.position(buf.position() + titleLength);
            Chapter chapter = new Chapter(start / 10_000L, title.isEmpty() ? null : title, null, null);
            chapter.setChapterId(String.valueOf(i + 1));
            chapters.add(chapter);
        }
        return chapters;
    }

    // ---------------------------------------------------------------- chapter track

    private static final class Track {
        int id = -1;
        int handler;
        long timescale;
        final List<Integer> chapterRefs = new ArrayList<>();
        byte[] stts;
        byte[] stsz;
        byte[] stsc;
        byte[] chunkOffsets;
        boolean chunkOffsets64;

        boolean hasTables() {
            return stts != null && stsz != null && stsc != null && chunkOffsets != null;
        }
    }

    private Track findChapterTrack() {
        Set<Integer> referenced = new HashSet<>();
        for (Track track : tracks) {
            referenced.addAll(track.chapterRefs);
        }
        for (Track track : tracks) {
            if (referenced.contains(track.id) && (track.handler == TEXT || track.handler == SBTL)
                    && track.hasTables()) {
                return track;
            }
        }
        return null;
    }

    private List<Chapter> readTrackChapters(Track track) throws IOException, Mp4ReaderException {
        if (track.timescale <= 0) {
            throw new Mp4ReaderException("chapter track has no timescale");
        }
        try {
            long[] sizes = parseStsz(track.stsz);
            long[] starts = parseStts(track.stts, sizes.length);
            long[] offsets = parseSampleOffsets(track, sizes);
            int count = Math.min(Math.min(sizes.length, starts.length), offsets.length);

            String[] titles = new String[count];
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Long.compare(offsets[a], offsets[b]));
            for (int index : order) {
                titles[index] = readSampleText(offsets[index], sizes[index]);
            }

            List<Chapter> chapters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Chapter chapter = new Chapter(starts[i] * 1000L / track.timescale, titles[i], null, null);
                chapter.setChapterId(String.valueOf(i + 1));
                chapters.add(chapter);
            }
            return chapters;
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new Mp4ReaderException("truncated chapter track sample table");
        }
    }

    /** Sample sizes, capped at {@link #MAX_CHAPTERS}. */
    private static long[] parseStsz(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.position(4);
        long sampleSize = buf.getInt() & 0xFFFFFFFFL;
        long sampleCount = buf.getInt() & 0xFFFFFFFFL;
        int count = (int) Math.min(sampleCount, MAX_CHAPTERS);
        long[] sizes = new long[count];
        if (sampleSize != 0) {
            Arrays.fill(sizes, sampleSize);
        } else {
            count = Math.min(count, buf.remaining() / 4);
            sizes = new long[count];
            for (int i = 0; i < count; i++) {
                sizes[i] = buf.getInt() & 0xFFFFFFFFL;
            }
        }
        return sizes;
    }

    /** Start time (in timescale units) of each of the first {@code max} samples. */
    private static long[] parseStts(byte[] payload, int max) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.position(4);
        long entries = buf.getInt() & 0xFFFFFFFFL;
        long[] starts = new long[max];
        int sample = 0;
        long time = 0;
        for (long e = 0; e < entries && sample < max && buf.remaining() >= 8; e++) {
            long count = buf.getInt() & 0xFFFFFFFFL;
            long delta = buf.getInt() & 0xFFFFFFFFL;
            for (long j = 0; j < count && sample < max; j++) {
                starts[sample++] = time;
                time += delta;
            }
        }
        return sample == max ? starts : Arrays.copyOf(starts, sample);
    }

    /** File offset of each sample, from the sample-to-chunk table and the chunk offsets. */
    private static long[] parseSampleOffsets(Track track, long[] sizes) throws Mp4ReaderException {
        ByteBuffer chunkBuf = ByteBuffer.wrap(track.chunkOffsets);
        chunkBuf.position(4);
        long chunkCount = chunkBuf.getInt() & 0xFFFFFFFFL;
        int entrySize = track.chunkOffsets64 ? 8 : 4;
        chunkCount = Math.min(chunkCount, chunkBuf.remaining() / entrySize);
        long[] chunkOffsets = new long[(int) chunkCount];
        for (int i = 0; i < chunkOffsets.length; i++) {
            chunkOffsets[i] = track.chunkOffsets64 ? chunkBuf.getLong() : chunkBuf.getInt() & 0xFFFFFFFFL;
        }

        ByteBuffer stscBuf = ByteBuffer.wrap(track.stsc);
        stscBuf.position(4);
        long entryCount = stscBuf.getInt() & 0xFFFFFFFFL;
        entryCount = Math.min(entryCount, stscBuf.remaining() / 12);
        long[] firstChunks = new long[(int) entryCount];
        long[] samplesPerChunk = new long[(int) entryCount];
        for (int i = 0; i < entryCount; i++) {
            firstChunks[i] = stscBuf.getInt() & 0xFFFFFFFFL;
            samplesPerChunk[i] = stscBuf.getInt() & 0xFFFFFFFFL;
            stscBuf.getInt(); // sample description index
        }

        long[] offsets = new long[sizes.length];
        int sample = 0;
        for (int i = 0; i < firstChunks.length && sample < sizes.length; i++) {
            long lastChunk = i + 1 < firstChunks.length ? firstChunks[i + 1] - 1 : chunkOffsets.length;
            for (long chunk = firstChunks[i]; chunk <= lastChunk && sample < sizes.length; chunk++) {
                if (chunk < 1 || chunk > chunkOffsets.length) {
                    throw new Mp4ReaderException("stsc references missing chunk " + chunk);
                }
                long offset = chunkOffsets[(int) (chunk - 1)];
                for (long k = 0; k < samplesPerChunk[i] && sample < sizes.length; k++) {
                    offsets[sample] = offset;
                    offset += sizes[sample];
                    sample++;
                }
            }
        }
        return sample == offsets.length ? offsets : Arrays.copyOf(offsets, sample);
    }

    private String readSampleText(long offset, long size) throws IOException {
        if (size < 2) {
            return null;
        }
        if (source.length() >= 0 && offset + 2 > source.length()) {
            throw new EOFException("chapter sample beyond end of file");
        }
        int len = (int) Math.min(size, MAX_SAMPLE);
        if (source.length() >= 0) {
            len = (int) Math.min(len, source.length() - offset);
        }
        byte[] sample = new byte[len];
        source.seek(offset);
        source.readFully(sample, 0, len);
        return decodeText(sample);
    }

    /** A text sample: 16-bit length, then UTF-8 or (with a BOM) UTF-16 text, then optional boxes. */
    static String decodeText(byte[] sample) {
        if (sample.length < 2) {
            return null;
        }
        int textLength = Math.min(((sample[0] & 0xFF) << 8) | (sample[1] & 0xFF), sample.length - 2);
        if (textLength <= 0) {
            return null;
        }
        int start = 2;
        Charset charset = StandardCharsets.UTF_8;
        if (textLength >= 2 && (sample[2] & 0xFF) == 0xFE && (sample[3] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            start += 2;
        } else if (textLength >= 2 && (sample[2] & 0xFF) == 0xFF && (sample[3] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            start += 2;
        } else if (textLength >= 3 && (sample[2] & 0xFF) == 0xEF && (sample[3] & 0xFF) == 0xBB
                && (sample[4] & 0xFF) == 0xBF) {
            start += 3;
        }
        String text = new String(sample, start, textLength - (start - 2), charset).trim();
        return text.isEmpty() ? null : text;
    }

    // ---------------------------------------------------------------- helpers

    private static int fourCc(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.ISO_8859_1);
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    private static String fourCcName(int type) {
        byte[] bytes = ByteBuffer.allocate(4).putInt(type).array();
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
