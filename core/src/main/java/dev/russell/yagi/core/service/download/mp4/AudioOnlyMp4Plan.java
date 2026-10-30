package dev.russell.yagi.core.service.download.mp4;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plans an audio-only copy of a video MP4 that is fetched with byte-range requests instead of
 * downloading the whole file.
 *
 * <p>A progressive MP4 keeps its sample tables in the {@code moov} box and the media itself in
 * {@code mdat}, with audio and video chunks interleaved. Given the {@code ftyp} and {@code moov}
 * boxes, this picks the audio track and describes a new file:
 * <pre>
 *   ftyp | moov (audio track only, chunk offsets rewritten) | mdat (audio chunks, in order)
 * </pre>
 * Nothing is decoded or re-encoded: the audio chunks are copied byte for byte, so the result has
 * the original audio quality. On a typical video podcast the audio is well under 1% of the file.
 *
 * <p>The source chunks are grouped into {@link Fetch}es, one range request each: neighbouring
 * chunks separated by a small gap are fetched together and the gap is dropped, which matters for
 * files whose audio comes in many small pieces. Fetches are written to the output in order, so a
 * partly written file can be resumed after the last complete fetch ({@link #resumeIndex}).
 *
 * <p>Fragmented MP4 ({@code mvex}), compressed movie headers ({@code cmov}), audio stored in
 * another file (an external data reference) and compact sample sizes ({@code stz2}) are rejected
 * with {@link UnsupportedMp4Exception}, as is a file without an audio track.
 */
public final class AudioOnlyMp4Plan {
    /** Neighbouring chunks closer than this are fetched in one request, gap included. */
    static final long MAX_GAP = 64 * 1024;
    /** A fetch is not grown past this, so a fetch in flight never holds much memory. */
    static final long MAX_FETCH_LENGTH = 1024 * 1024;
    /** The header of the output's mdat box: the 64-bit size form, so it never overflows. */
    private static final int MDAT_HEADER_LENGTH = 16;

    /** One range request: {@code length} bytes from {@code offset} of the source file. */
    public static final class Fetch {
        public final long offset;
        public final int length;
        /** Pairs of (start within this fetch, length) to copy to the output, in order. */
        private final int[] keep;

        Fetch(long offset, int length, int[] keep) {
            this.offset = offset;
            this.length = length;
            this.keep = keep;
        }

        /** How many bytes of this fetch end up in the output. */
        public int outputLength() {
            int total = 0;
            for (int i = 1; i < keep.length; i += 2) {
                total += keep[i];
            }
            return total;
        }

        /** Copies the parts of {@code fetched} (this fetch's bytes) that belong in the output. */
        public byte[] extract(@NonNull byte[] fetched) {
            if (fetched.length != length) {
                throw new IllegalArgumentException("Expected " + length + " bytes, got " + fetched.length);
            }
            byte[] out = new byte[outputLength()];
            int position = 0;
            for (int i = 0; i < keep.length; i += 2) {
                System.arraycopy(fetched, keep[i], out, position, keep[i + 1]);
                position += keep[i + 1];
            }
            return out;
        }
    }

    private final byte[] header;
    private final List<Fetch> fetches;
    /** Output position after each fetch has been written. */
    private final long[] fetchEnds;
    private final long lastSourceByte;

    private AudioOnlyMp4Plan(byte[] header, List<Fetch> fetches, long lastSourceByte) {
        this.header = header;
        this.fetches = Collections.unmodifiableList(fetches);
        this.fetchEnds = new long[fetches.size()];
        long position = header.length;
        for (int i = 0; i < fetches.size(); i++) {
            position += fetches.get(i).outputLength();
            fetchEnds[i] = position;
        }
        this.lastSourceByte = lastSourceByte;
    }

    /** The output's first bytes: ftyp, the rewritten moov and the mdat header. */
    @NonNull
    public byte[] getHeader() {
        return header.clone();
    }

    public int getHeaderLength() {
        return header.length;
    }

    @NonNull
    public List<Fetch> getFetches() {
        return fetches;
    }

    /** The size of the finished output file. */
    public long getOutputSize() {
        return fetchEnds.length == 0 ? header.length : fetchEnds[fetchEnds.length - 1];
    }

    /** One past the last source byte any fetch reads; must not exceed the source file's size. */
    public long getRequiredSourceSize() {
        return lastSourceByte;
    }

    /**
     * The index of the first fetch still to do when the output file already holds
     * {@code existingLength} bytes whose header matches {@link #getHeader()}: everything after
     * the last complete fetch is rewritten. The caller truncates the file to
     * {@link #outputPositionBefore} of the returned index.
     */
    public int resumeIndex(long existingLength) {
        int index = 0;
        while (index < fetchEnds.length && fetchEnds[index] <= existingLength) {
            index++;
        }
        return index;
    }

    /** The output position at which fetch {@code index} starts. */
    public long outputPositionBefore(int index) {
        return index == 0 ? header.length : fetchEnds[index - 1];
    }

    /**
     * @param ftyp the source's {@code ftyp} box, or null if it has none (an old QuickTime file)
     * @param moov the source's complete {@code moov} box
     */
    @NonNull
    public static AudioOnlyMp4Plan create(@Nullable byte[] ftyp, @NonNull byte[] moov)
            throws UnsupportedMp4Exception {
        Mp4Box moovBox = Mp4Box.read(moov, 0, moov.length);
        if (!moovBox.type.equals("moov")) {
            throw new UnsupportedMp4Exception("Expected moov, got '" + moovBox.type + "'");
        }
        List<Mp4Box> moovChildren = moovBox.children(moov);
        Mp4Box audioTrak = null;
        Mp4Box firstAudioTrak = null;
        for (Mp4Box child : moovChildren) {
            if (child.type.equals("mvex")) {
                throw new UnsupportedMp4Exception("Fragmented MP4");
            } else if (child.type.equals("cmov")) {
                throw new UnsupportedMp4Exception("Compressed movie header");
            } else if (child.type.equals("trak") && isAudioTrak(moov, child)) {
                if (firstAudioTrak == null) {
                    firstAudioTrak = child;
                }
                if (audioTrak == null && isTrackEnabled(moov, child)) {
                    audioTrak = child;
                }
            }
        }
        if (audioTrak == null) {
            audioTrak = firstAudioTrak;
        }
        if (audioTrak == null) {
            throw new UnsupportedMp4Exception("No audio track");
        }

        Mp4Box minf = audioTrak.requireChild(moov, "mdia").requireChild(moov, "minf");
        checkSelfContained(moov, minf.requireChild(moov, "dinf").requireChild(moov, "dref"));
        Mp4Box stbl = minf.requireChild(moov, "stbl");
        long[] chunkOffsets = readChunkOffsets(moov, stbl);
        long[] chunkLengths = readChunkLengths(moov, stbl, chunkOffsets.length);

        byte[] fileType = ftyp != null ? ftyp : quickTimeFileType();
        byte[] newMoov = buildMoov(moov, moovBox, moovChildren, audioTrak);
        int headerLength = fileType.length + newMoov.length + MDAT_HEADER_LENGTH;

        long[] newOffsets = new long[chunkOffsets.length];
        long position = headerLength;
        long audioBytes = 0;
        for (int i = 0; i < chunkOffsets.length; i++) {
            newOffsets[i] = position;
            position += chunkLengths[i];
            audioBytes += chunkLengths[i];
        }
        writeChunkOffsets(newMoov, newOffsets);

        byte[] header = new byte[headerLength];
        System.arraycopy(fileType, 0, header, 0, fileType.length);
        System.arraycopy(newMoov, 0, header, fileType.length, newMoov.length);
        int mdat = fileType.length + newMoov.length;
        Mp4Box.writeUInt32(header, mdat, 1);
        header[mdat + 4] = 'm';
        header[mdat + 5] = 'd';
        header[mdat + 6] = 'a';
        header[mdat + 7] = 't';
        Mp4Box.writeUInt64(header, mdat + 8, MDAT_HEADER_LENGTH + audioBytes);

        long lastSourceByte = 0;
        for (int i = 0; i < chunkOffsets.length; i++) {
            lastSourceByte = Math.max(lastSourceByte, chunkOffsets[i] + chunkLengths[i]);
        }
        return new AudioOnlyMp4Plan(header, groupIntoFetches(chunkOffsets, chunkLengths), lastSourceByte);
    }

    private static boolean isAudioTrak(byte[] moov, Mp4Box trak) throws UnsupportedMp4Exception {
        Mp4Box mdia = trak.child(moov, "mdia");
        Mp4Box hdlr = mdia != null ? mdia.child(moov, "hdlr") : null;
        if (hdlr == null || hdlr.size() < hdlr.headerLength + 12) {
            return false;
        }
        // version/flags (4), pre_defined (4), handler_type (4)
        return Mp4Box.readType(moov, hdlr.contentStart() + 8).equals("soun");
    }

    private static boolean isTrackEnabled(byte[] moov, Mp4Box trak) throws UnsupportedMp4Exception {
        Mp4Box tkhd = trak.child(moov, "tkhd");
        if (tkhd == null || tkhd.size() < tkhd.headerLength + 4) {
            return true;
        }
        return (moov[tkhd.contentStart() + 3] & 0x01) != 0;
    }

    /** Every data reference must say "in this file"; audio stored elsewhere cannot be fetched. */
    private static void checkSelfContained(byte[] moov, Mp4Box dref) throws UnsupportedMp4Exception {
        // version/flags (4), entry_count (4), then one full box per entry
        for (Mp4Box entry : dref.children(moov, 8)) {
            if (entry.size() < entry.headerLength + 4
                    || (moov[entry.contentStart() + 3] & 0x01) == 0) {
                throw new UnsupportedMp4Exception("Audio data is stored outside the file");
            }
        }
    }

    private static long[] readChunkOffsets(byte[] moov, Mp4Box stbl) throws UnsupportedMp4Exception {
        Mp4Box box = stbl.child(moov, "stco");
        boolean large = false;
        if (box == null) {
            box = stbl.requireChild(moov, "co64");
            large = true;
        }
        int entrySize = large ? 8 : 4;
        long count = readCount(moov, box, entrySize);
        long[] offsets = new long[(int) count];
        int position = box.contentStart() + 8;
        for (int i = 0; i < count; i++) {
            offsets[i] = large ? Mp4Box.readUInt64(moov, position) : Mp4Box.readUInt32(moov, position);
            position += entrySize;
        }
        return offsets;
    }

    private static void writeChunkOffsets(byte[] newMoov, long[] offsets) throws UnsupportedMp4Exception {
        Mp4Box moov = Mp4Box.read(newMoov, 0, newMoov.length);
        Mp4Box stbl = moov.requireChild(newMoov, "trak").requireChild(newMoov, "mdia")
                .requireChild(newMoov, "minf").requireChild(newMoov, "stbl");
        Mp4Box box = stbl.child(newMoov, "stco");
        boolean large = box == null;
        if (large) {
            box = stbl.requireChild(newMoov, "co64");
        }
        int position = box.contentStart() + 8;
        for (long offset : offsets) {
            if (large) {
                Mp4Box.writeUInt64(newMoov, position, offset);
                position += 8;
            } else {
                if (offset > 0xFFFFFFFFL) {
                    throw new UnsupportedMp4Exception("Audio-only file too large for 32-bit offsets");
                }
                Mp4Box.writeUInt32(newMoov, position, offset);
                position += 4;
            }
        }
    }

    /** The byte length of every chunk, from the sample-to-chunk and sample size tables. */
    private static long[] readChunkLengths(byte[] moov, Mp4Box stbl, int chunkCount)
            throws UnsupportedMp4Exception {
        Mp4Box stsz = stbl.child(moov, "stsz");
        if (stsz == null) {
            throw new UnsupportedMp4Exception(stbl.child(moov, "stz2") != null
                    ? "Compact sample sizes (stz2)" : "No sample size table");
        }
        if (stsz.size() < stsz.headerLength + 12) {
            throw new UnsupportedMp4Exception("Truncated stsz");
        }
        long fixedSize = Mp4Box.readUInt32(moov, stsz.contentStart() + 4);
        long sampleCount = Mp4Box.readUInt32(moov, stsz.contentStart() + 8);
        if (fixedSize == 0 && stsz.size() < stsz.headerLength + 12 + 4 * sampleCount) {
            throw new UnsupportedMp4Exception("Truncated stsz table");
        }

        Mp4Box stsc = stbl.requireChild(moov, "stsc");
        long entryCount = readCount(moov, stsc, 12);
        long[] lengths = new long[chunkCount];
        long sample = 0;
        int position = stsc.contentStart() + 8;
        for (int entry = 0; entry < entryCount; entry++) {
            long firstChunk = Mp4Box.readUInt32(moov, position);
            long samplesPerChunk = Mp4Box.readUInt32(moov, position + 4);
            long nextFirstChunk = entry + 1 < entryCount
                    ? Mp4Box.readUInt32(moov, position + 12) : chunkCount + 1L;
            position += 12;
            // The first entry has to start at chunk 1, or the chunks before it would have no samples.
            if (firstChunk < 1 || (entry == 0 && firstChunk != 1)
                    || nextFirstChunk <= firstChunk || nextFirstChunk > chunkCount + 1L) {
                throw new UnsupportedMp4Exception("Bad sample-to-chunk entry " + entry);
            }
            for (long chunk = firstChunk; chunk < nextFirstChunk; chunk++) {
                if (sample + samplesPerChunk > sampleCount) {
                    throw new UnsupportedMp4Exception("Sample-to-chunk table runs past the samples");
                }
                long length;
                if (fixedSize != 0) {
                    try {
                        length = Math.multiplyExact(fixedSize, samplesPerChunk);
                    } catch (ArithmeticException e) {
                        throw new UnsupportedMp4Exception("Chunk " + chunk + " is too large");
                    }
                } else {
                    length = 0;
                    int sizePosition = stsz.contentStart() + 12 + (int) (4 * sample);
                    for (long i = 0; i < samplesPerChunk; i++) {
                        length += Mp4Box.readUInt32(moov, sizePosition);
                        sizePosition += 4;
                    }
                }
                if (length > Integer.MAX_VALUE) {
                    throw new UnsupportedMp4Exception("Chunk " + chunk + " is too large");
                }
                lengths[(int) chunk - 1] = length;
                sample += samplesPerChunk;
            }
        }
        if (sample != sampleCount || (entryCount == 0 && chunkCount > 0)) {
            throw new UnsupportedMp4Exception("Sample-to-chunk table covers " + sample
                    + " of " + sampleCount + " samples");
        }
        return lengths;
    }

    /** The entry count of a full box whose entries are {@code entrySize} bytes, checked. */
    private static long readCount(byte[] moov, Mp4Box box, int entrySize) throws UnsupportedMp4Exception {
        if (box.size() < box.headerLength + 8) {
            throw new UnsupportedMp4Exception("Truncated '" + box.type + "'");
        }
        long count = Mp4Box.readUInt32(moov, box.contentStart() + 4);
        if (box.size() < box.headerLength + 8 + count * entrySize) {
            throw new UnsupportedMp4Exception("Truncated '" + box.type + "' table");
        }
        return count;
    }

    /**
     * The moov box with every track except the audio track left out. The audio track's
     * {@code tref} (references to tracks that are no longer there, such as a chapter text track)
     * goes too; everything else is copied unchanged, so the box sizes inside the track hold.
     */
    private static byte[] buildMoov(byte[] moov, Mp4Box moovBox, List<Mp4Box> moovChildren, Mp4Box audioTrak)
            throws UnsupportedMp4Exception {
        ByteArrayOutputStream trak = new ByteArrayOutputStream();
        for (Mp4Box child : audioTrak.children(moov)) {
            if (!child.type.equals("tref")) {
                trak.write(moov, child.start, child.size());
            }
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Mp4Box child : moovChildren) {
            if (child == audioTrak) {
                byte[] trakBytes = trak.toByteArray();
                writeBoxHeader(body, "trak", trakBytes.length);
                body.write(trakBytes, 0, trakBytes.length);
            } else if (!child.type.equals("trak")) {
                body.write(moov, child.start, child.size());
            }
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeBoxHeader(result, moovBox.type, body.size());
        byte[] bodyBytes = body.toByteArray();
        result.write(bodyBytes, 0, bodyBytes.length);
        return result.toByteArray();
    }

    private static void writeBoxHeader(ByteArrayOutputStream out, String type, int contentLength) {
        byte[] header = new byte[8];
        Mp4Box.writeUInt32(header, 0, 8L + contentLength);
        for (int i = 0; i < 4; i++) {
            header[4 + i] = (byte) type.charAt(i);
        }
        out.write(header, 0, header.length);
    }

    /** An ftyp for a source that has none, which only old QuickTime files lack. */
    private static byte[] quickTimeFileType() {
        byte[] ftyp = new byte[20];
        Mp4Box.writeUInt32(ftyp, 0, ftyp.length);
        byte[] text = "ftypqt  \0\0\0\0qt  ".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(text, 0, ftyp, 4, text.length);
        return ftyp;
    }

    /**
     * Groups the chunks into fetches of at most {@link #MAX_FETCH_LENGTH} bytes: a larger chunk
     * (audio stored in few big pieces) is split over several fetches, and neighbouring chunks no
     * more than {@link #MAX_GAP} apart share one.
     */
    private static List<Fetch> groupIntoFetches(long[] offsets, long[] lengths) {
        List<long[]> pieces = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            for (long done = 0; done < lengths[i]; done += MAX_FETCH_LENGTH) {
                pieces.add(new long[]{offsets[i] + done, Math.min(MAX_FETCH_LENGTH, lengths[i] - done)});
            }
        }
        List<Fetch> fetches = new ArrayList<>();
        int i = 0;
        while (i < pieces.size()) {
            long start = pieces.get(i)[0];
            long end = start + pieces.get(i)[1];
            List<Integer> keep = new ArrayList<>();
            keep.add(0);
            keep.add((int) pieces.get(i)[1]);
            i++;
            while (i < pieces.size()) {
                long next = pieces.get(i)[0];
                long nextEnd = next + pieces.get(i)[1];
                if (next < end || next - end > MAX_GAP || nextEnd - start > MAX_FETCH_LENGTH) {
                    break;
                }
                keep.add((int) (next - start));
                keep.add((int) pieces.get(i)[1]);
                end = nextEnd;
                i++;
            }
            int[] keepArray = new int[keep.size()];
            for (int k = 0; k < keepArray.length; k++) {
                keepArray[k] = keep.get(k);
            }
            fetches.add(new Fetch(start, (int) (end - start), keepArray));
        }
        return fetches;
    }
}
