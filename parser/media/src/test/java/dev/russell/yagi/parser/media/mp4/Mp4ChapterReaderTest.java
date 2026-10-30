package dev.russell.yagi.parser.media.mp4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import dev.russell.yagi.model.feed.Chapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Mp4ChapterReaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] TITLES = {"Intro", "Chapter One", "Chapter Two"};
    /** Chapter durations in a 1000 Hz timescale. */
    private static final long[] DURATIONS = {5_000, 60_000, 3_600_000};
    private static final long[] STARTS_MS = {0, 5_000, 65_000};

    // ------------------------------------------------------------------ tests

    @Test
    public void readsNeroChaptersVersion1() throws Exception {
        byte[] file = new Mp4Builder().withChpl(1).build();
        assertChapters(readSeekable(file), new String[]{"N-Intro", "N-Chapter One", "N-Chapter Two"},
                new long[]{0, 5_000, 65_000});
    }

    @Test
    public void readsNeroChaptersVersion0() throws Exception {
        byte[] file = new Mp4Builder().withChpl(0).build();
        assertChapters(readSeekable(file), new String[]{"N-Intro", "N-Chapter One", "N-Chapter Two"},
                new long[]{0, 5_000, 65_000});
    }

    @Test
    public void readsQuickTimeChapterTrackWithMoovAfterMdat() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void readsQuickTimeChapterTrackOneChunkPerSample() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().oneChunkPerSample().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void readsQuickTimeChapterTrackWithCo64() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().co64().oneChunkPerSample().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void readsQuickTimeChapterTrackFromForwardOnlyStreamWhenMoovFirst() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().moovFirst().build();
        assertChapters(readStream(file), TITLES, STARTS_MS);
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void prefersChapterTrackOverNero() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().withChpl(1).build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void fallsBackToNeroWhenChapterTrackUnreachable() throws Exception {
        // Forward-only stream, moov after mdat: the chapter samples are behind us.
        byte[] file = new Mp4Builder().withChapterTrack().withChpl(1).build();
        assertChapters(readStream(file), new String[]{"N-Intro", "N-Chapter One", "N-Chapter Two"},
                new long[]{0, 5_000, 65_000});
    }

    @Test
    public void unreachableChapterTrackWithoutNeroThrows() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().build();
        try {
            readStream(file);
            fail("expected IOException");
        } catch (IOException expected) {
            // backwards seek on a forward-only source
        }
    }

    @Test
    public void decodesUtf16AndAppleEncdTrailer() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().utf16().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void handles64BitAndToEndBoxSizes() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().largeMdatHeader().moovSizeZero().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void skipsLargeAudioSampleTables() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().bigAudioStsz().build();
        assertChapters(readSeekable(file), TITLES, STARTS_MS);
    }

    @Test
    public void ignoresUnreferencedTextTrack() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().noTref().build();
        assertTrue(readSeekable(file).isEmpty());
    }

    @Test
    public void noChaptersGivesEmptyList() throws Exception {
        assertTrue(readSeekable(new Mp4Builder().build()).isEmpty());
    }

    @Test
    public void nonMp4GivesEmptyList() throws Exception {
        byte[] id3 = new byte[64];
        System.arraycopy("ID3".getBytes(StandardCharsets.ISO_8859_1), 0, id3, 0, 3);
        id3[3] = 3;
        assertTrue(readSeekable(id3).isEmpty());
        assertTrue(readStream(id3).isEmpty());
        assertTrue(readSeekable(new byte[0]).isEmpty());
        assertTrue(readStream(new byte[3]).isEmpty());
    }

    @Test
    public void truncatedBeforeMoovGivesEmptyList() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().withChpl(1).build();
        byte[] cut = Arrays.copyOf(file, 40); // inside mdat
        assertTrue(readSeekable(cut).isEmpty());
        assertTrue(readStream(cut).isEmpty());
    }

    @Test
    public void truncatedInsideMoovThrowsCheckedException() throws Exception {
        byte[] file = new Mp4Builder().withChapterTrack().withChpl(1).moovFirst().build();
        // moovFirst: ftyp (20 bytes) then moov; cut in the middle of moov.
        byte[] cut = Arrays.copyOf(file, 20 + 100);
        try {
            readStream(cut);
            fail("expected exception");
        } catch (IOException | Mp4ReaderException expected) {
            // truncated
        }
        // With a known length the top-level moov is seen to overrun the file.
        assertTrue(readSeekable(cut).isEmpty());
    }

    @Test
    public void corruptChildSizeThrows() throws Exception {
        byte[] file = new Mp4Builder().withChpl(1).build();
        int moov = indexOf(file, "moov") - 4;
        // first child of moov: set its size to 4 (smaller than a header)
        ByteBuffer.wrap(file).putInt(moov + 8, 4);
        try {
            readSeekable(file);
            fail("expected Mp4ReaderException");
        } catch (Mp4ReaderException expected) {
            // corrupt
        }
    }

    @Test
    public void childOverrunningParentThrows() throws Exception {
        byte[] file = new Mp4Builder().withChpl(1).build();
        int moov = indexOf(file, "moov") - 4;
        ByteBuffer.wrap(file).putInt(moov + 8, Integer.MAX_VALUE);
        try {
            readSeekable(file);
            fail("expected Mp4ReaderException");
        } catch (Mp4ReaderException expected) {
            // corrupt
        }
    }

    @Test
    public void randomCorruptionNeverThrowsUncheckedExceptions() throws Exception {
        byte[] original = new Mp4Builder().withChapterTrack().withChpl(1).build();
        Random random = new Random(42);
        for (int round = 0; round < 3000; round++) {
            byte[] file = original.clone();
            int mutations = 1 + random.nextInt(4);
            for (int m = 0; m < mutations; m++) {
                file[random.nextInt(file.length)] = (byte) random.nextInt(256);
            }
            if (random.nextInt(4) == 0) {
                file = Arrays.copyOf(file, random.nextInt(file.length));
            }
            try {
                Mp4ChapterReader.readChapters(new StreamMp4Source(new ByteArrayInputStream(file)));
                readSeekable(file);
            } catch (IOException | Mp4ReaderException expected) {
                // checked failures are fine; anything unchecked fails the test
            }
        }
    }

    @Test
    public void streamSourceRespectsSkipBudget() throws Exception {
        byte[] file = new Mp4Builder().withChpl(1).build(); // moov after a ~100 byte mdat
        try {
            Mp4ChapterReader.readChapters(new StreamMp4Source(new ByteArrayInputStream(file), -1, 10));
            fail("expected IOException");
        } catch (IOException expected) {
            // budget exceeded
        }
    }

    @Test
    public void decodeTextVariants() {
        assertNull(Mp4ChapterReader.decodeText(new byte[]{0}));
        assertNull(Mp4ChapterReader.decodeText(new byte[]{0, 0}));
        assertEquals("ab", Mp4ChapterReader.decodeText(new byte[]{0, 2, 'a', 'b', 'c'}));
        assertEquals("ab", Mp4ChapterReader.decodeText(new byte[]{0, 100, 'a', 'b'}));
        assertEquals("x", Mp4ChapterReader.decodeText(new byte[]{0, 5, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x', ' '}));
        assertEquals("é", Mp4ChapterReader.decodeText(new byte[]{0, 4, (byte) 0xFF, (byte) 0xFE, (byte) 0xE9, 0}));
    }

    // ------------------------------------------------------------------ real ffmpeg output

    /**
     * {@code chapters.m4b} (1.8 KB) was made by ffmpeg from 10 s of silence with three chapters
     * (0 "Opening", 3000 "Middle part é", 7500 "Ending"); ffmpeg writes both a QuickTime chapter
     * track and a Nero chpl list, with moov after mdat.
     */
    private static final String[] REAL_TITLES = {"Opening", "Middle part é", "Ending"};
    private static final long[] REAL_STARTS = {0, 3_000, 7_500};

    private static byte[] fixture() throws IOException {
        try (java.io.InputStream in = Mp4ChapterReaderTest.class.getResourceAsStream("/mp4/chapters.m4b")) {
            if (in == null) {
                throw new IOException("fixture missing");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void rename(byte[] data, String from, String to) {
        int at = indexOf(data, from);
        System.arraycopy(to.getBytes(StandardCharsets.ISO_8859_1), 0, data, at, 4);
    }

    @Test
    public void readsFfmpegM4b() throws Exception {
        assertChapters(readSeekable(fixture()), REAL_TITLES, REAL_STARTS);
    }

    @Test
    public void readsFfmpegChapterTrackAlone() throws Exception {
        byte[] file = fixture();
        rename(file, "chpl", "free");
        assertChapters(readSeekable(file), REAL_TITLES, REAL_STARTS);
    }

    @Test
    public void readsFfmpegNeroChaptersAlone() throws Exception {
        byte[] file = fixture();
        rename(file, "chap", "xxxx");
        assertChapters(readSeekable(file), REAL_TITLES, REAL_STARTS);
        // forward-only: the chapter track is unreachable behind moov, chpl is used
        assertChapters(readStream(fixture()), REAL_TITLES, REAL_STARTS);
    }

    // ------------------------------------------------------------------ helpers

    private List<Chapter> readSeekable(byte[] data) throws IOException, Mp4ReaderException {
        File f = tmp.newFile();
        Files.write(f.toPath(), data);
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            return Mp4ChapterReader.readChapters(new FileChannelMp4Source(raf.getChannel()));
        }
    }

    private static List<Chapter> readStream(byte[] data) throws IOException, Mp4ReaderException {
        return Mp4ChapterReader.readChapters(new StreamMp4Source(new ByteArrayInputStream(data)));
    }

    private static void assertChapters(List<Chapter> chapters, String[] titles, long[] starts) {
        assertEquals(titles.length, chapters.size());
        for (int i = 0; i < titles.length; i++) {
            assertEquals(titles[i], chapters.get(i).getTitle());
            assertEquals(starts[i], chapters.get(i).getStart());
        }
    }

    private static int indexOf(byte[] data, String fourCc) {
        byte[] needle = fourCc.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i <= data.length - 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError(fourCc + " not found");
    }

    static byte[] box(String type, byte[]... children) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] c : children) {
            body.write(c, 0, c.length);
        }
        ByteBuffer b = ByteBuffer.allocate(8 + body.size());
        b.putInt(8 + body.size());
        b.put(type.getBytes(StandardCharsets.ISO_8859_1));
        b.put(body.toByteArray());
        return b.array();
    }

    static byte[] fullBox(String type, int version, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(4 + body.length);
        b.putInt(version << 24);
        b.put(body);
        return box(type, b.array());
    }

    static byte[] ints(long... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 4);
        for (long v : values) {
            b.putInt((int) v);
        }
        return b.array();
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** Builds minimal but structurally valid M4B files. */
    private static final class Mp4Builder {
        private int chplVersion = -1;
        private boolean chapterTrack;
        private boolean moovFirst;
        private boolean oneChunkPerSample;
        private boolean co64;
        private boolean utf16;
        private boolean largeMdatHeader;
        private boolean moovSizeZero;
        private boolean bigAudioStsz;
        private boolean noTref;

        Mp4Builder withChpl(int version) {
            chplVersion = version;
            return this;
        }

        Mp4Builder withChapterTrack() {
            chapterTrack = true;
            return this;
        }

        Mp4Builder moovFirst() {
            moovFirst = true;
            return this;
        }

        Mp4Builder oneChunkPerSample() {
            oneChunkPerSample = true;
            return this;
        }

        Mp4Builder co64() {
            co64 = true;
            return this;
        }

        Mp4Builder utf16() {
            utf16 = true;
            return this;
        }

        Mp4Builder largeMdatHeader() {
            largeMdatHeader = true;
            return this;
        }

        Mp4Builder moovSizeZero() {
            moovSizeZero = true;
            return this;
        }

        Mp4Builder bigAudioStsz() {
            bigAudioStsz = true;
            return this;
        }

        Mp4Builder noTref() {
            noTref = true;
            return this;
        }

        byte[] build() {
            byte[] ftyp = box("ftyp", "M4B ".getBytes(StandardCharsets.ISO_8859_1), ints(0),
                    "M4A ".getBytes(StandardCharsets.ISO_8859_1));
            List<byte[]> samples = new ArrayList<>();
            for (String title : TITLES) {
                samples.add(textSample(title));
            }
            byte[] audio = new byte[64]; // fake audio payload
            byte[] mdatBody = concat(audio, concat(samples.toArray(new byte[0][])));
            int mdatHeader = largeMdatHeader ? 16 : 8;

            // Offsets depend on moov's size when moov comes first; its size does not depend on them.
            byte[] moov = moov(samples, 0);
            long mdatStart = ftyp.length + (moovFirst ? moov.length : 0);
            long firstSample = mdatStart + mdatHeader + audio.length;
            moov = moov(samples, firstSample);
            if (moovSizeZero) {
                ByteBuffer.wrap(moov).putInt(0, 0);
            }

            byte[] mdat;
            if (largeMdatHeader) {
                ByteBuffer b = ByteBuffer.allocate(16 + mdatBody.length);
                b.putInt(1).put("mdat".getBytes(StandardCharsets.ISO_8859_1)).putLong(16 + mdatBody.length);
                b.put(mdatBody);
                mdat = b.array();
            } else {
                mdat = box("mdat", mdatBody);
            }
            return moovFirst ? concat(ftyp, moov, mdat) : concat(ftyp, mdat, moov);
        }

        private byte[] textSample(String title) {
            byte[] text = utf16 ? concat(new byte[]{(byte) 0xFE, (byte) 0xFF}, title.getBytes(StandardCharsets.UTF_16BE))
                    : title.getBytes(StandardCharsets.UTF_8);
            byte[] encd = box("encd", ints(0x100));
            return concat(new byte[]{(byte) (text.length >> 8), (byte) text.length}, text, encd);
        }

        private byte[] moov(List<byte[]> samples, long firstSampleOffset) {
            List<byte[]> children = new ArrayList<>();
            children.add(fullBox("mvhd", 0, new byte[96]));
            children.add(audioTrak());
            if (chapterTrack) {
                children.add(textTrak(samples, firstSampleOffset));
            }
            if (chplVersion >= 0) {
                children.add(box("udta", chpl(), new byte[4])); // trailing 32-bit zero, as some writers emit
            }
            return box("moov", children.toArray(new byte[0][]));
        }

        private byte[] chpl() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (chplVersion == 1) {
                out.write(new byte[]{0, 0, 0, 0}, 0, 4); // reserved
            }
            out.write(TITLES.length);
            for (int i = 0; i < TITLES.length; i++) {
                byte[] title = ("N-" + TITLES[i]).getBytes(StandardCharsets.UTF_8);
                ByteBuffer b = ByteBuffer.allocate(9 + title.length);
                b.putLong(STARTS_MS[i] * 10_000L).put((byte) title.length).put(title);
                out.write(b.array(), 0, b.capacity());
            }
            return fullBox("chpl", chplVersion, out.toByteArray());
        }

        private byte[] tkhd(int id) {
            return fullBox("tkhd", 0, concat(ints(0, 0, id, 0, 0), new byte[60]));
        }

        private byte[] mdhd(long timescale) {
            return fullBox("mdhd", 0, ints(0, 0, timescale, 0, 0));
        }

        private byte[] hdlr(String handler) {
            return fullBox("hdlr", 0, concat(ints(0), handler.getBytes(StandardCharsets.ISO_8859_1),
                    ints(0, 0, 0), new byte[]{0}));
        }

        private byte[] audioTrak() {
            byte[] stsz;
            if (bigAudioStsz) {
                int count = Mp4ChapterReader.MAX_LEAF_BOX / 4 + 10;
                stsz = fullBox("stsz", 0, concat(ints(0, count), new byte[count * 4]));
            } else {
                stsz = fullBox("stsz", 0, ints(64, 1));
            }
            byte[] stbl = box("stbl",
                    fullBox("stsd", 0, ints(0)),
                    fullBox("stts", 0, ints(1, 1, 1024)),
                    fullBox("stsc", 0, ints(1, 1, 1, 1)),
                    stsz,
                    fullBox("stco", 0, ints(1, 0)));
            byte[] mdia = box("mdia", mdhd(44_100), hdlr("soun"), box("minf", stbl));
            if (noTref) {
                return box("trak", tkhd(1), mdia);
            }
            return box("trak", tkhd(1), box("tref", box("chap", ints(2))), mdia);
        }

        private byte[] textTrak(List<byte[]> samples, long firstOffset) {
            int n = samples.size();
            long[] stts = new long[2 + 2 * n];
            stts[1] = n;
            for (int i = 0; i < n; i++) {
                stts[2 + 2 * i] = 1;
                stts[3 + 2 * i] = DURATIONS[i];
            }
            long[] stsz = new long[3 + n];
            stsz[2] = n;
            for (int i = 0; i < n; i++) {
                stsz[3 + i] = samples.get(i).length;
            }
            byte[] stsc;
            long[] chunkOffsets;
            if (oneChunkPerSample) {
                stsc = fullBox("stsc", 0, ints(1, 1, 1, 1));
                chunkOffsets = new long[n];
                long off = firstOffset;
                for (int i = 0; i < n; i++) {
                    chunkOffsets[i] = off;
                    off += samples.get(i).length;
                }
            } else {
                stsc = fullBox("stsc", 0, ints(1, 1, n, 1));
                chunkOffsets = new long[]{firstOffset};
            }
            byte[] chunkBox;
            if (co64) {
                ByteBuffer b = ByteBuffer.allocate(4 + 8 * chunkOffsets.length);
                b.putInt(chunkOffsets.length);
                for (long o : chunkOffsets) {
                    b.putLong(o);
                }
                chunkBox = fullBox("co64", 0, b.array());
            } else {
                long[] values = new long[1 + chunkOffsets.length];
                values[0] = chunkOffsets.length;
                System.arraycopy(chunkOffsets, 0, values, 1, chunkOffsets.length);
                chunkBox = fullBox("stco", 0, ints(values));
            }
            byte[] stbl = box("stbl",
                    fullBox("stsd", 0, ints(0)),
                    fullBox("stts", 0, Arrays.copyOfRange(ints(stts), 4, stts.length * 4)),
                    stsc,
                    fullBox("stsz", 0, Arrays.copyOfRange(ints(stsz), 4, stsz.length * 4)),
                    chunkBox);
            return box("trak", tkhd(2),
                    box("mdia", mdhd(1000), hdlr("text"), box("minf", stbl)));
        }
    }
}
