package dev.russell.yagi.core.service.download.mp4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.ParsableByteArray;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Builds small interleaved audio+video MP4 files, cuts the audio out with the plan the way the
 * downloader does (range reads of the source, header first, then each fetch in order) and has
 * ExoPlayer's own {@link Mp4Extractor} read the result: it must find exactly one track, the
 * audio, with every audio sample byte for byte. Robolectric only because the extractor uses
 * {@code android.util.Pair}.
 */
@RunWith(RobolectricTestRunner.class)
public class AudioOnlyMp4PlanTest {

    @Test
    public void interleavedFileKeepsEveryAudioSample() throws Exception {
        Source source = new Source(false, false, 40, 3, 30_000, new Random(1));
        AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(source.ftyp, source.moov);
        byte[] output = assemble(plan, source.file);

        assertEquals(plan.getOutputSize(), output.length);
        assertTrue(output.length < source.file.length / 5);
        assertArrayEquals(source.audioSamples(), extractOnlyTrack(output));
    }

    @Test
    public void co64OffsetsAndMoovAtTheEndAreHandled() throws Exception {
        Source source = new Source(true, true, 25, 2, 10_000, new Random(2));
        AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(source.ftyp, source.moov);
        assertArrayEquals(source.audioSamples(), extractOnlyTrack(assemble(plan, source.file)));
    }

    @Test
    public void chunksCloseTogetherShareOneFetch() throws Exception {
        // Video chunks smaller than the gap limit: the audio can be read in very few requests.
        Source source = new Source(false, false, 60, 4, 2_000, new Random(3));
        AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(source.ftyp, source.moov);
        assertTrue(plan.getFetches().size() < 5);
        assertArrayEquals(source.audioSamples(), extractOnlyTrack(assemble(plan, source.file)));
    }

    @Test
    public void largeChunksAreSplitAcrossFetches() throws Exception {
        // Audio stored in a few big chunks of about 2 MB: no single request may hold one whole.
        Source source = new Source(false, false, 3, 2500, 200_000, new Random(7));
        AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(source.ftyp, source.moov);
        for (AudioOnlyMp4Plan.Fetch fetch : plan.getFetches()) {
            assertTrue(fetch.length <= AudioOnlyMp4Plan.MAX_FETCH_LENGTH);
        }
        assertTrue(plan.getFetches().size() > 3);
        assertArrayEquals(source.audioSamples(), extractOnlyTrack(assemble(plan, source.file)));
    }

    @Test
    public void resumesAfterTheLastCompleteFetch() throws Exception {
        Source source = new Source(false, false, 30, 3, 100_000, new Random(4));
        AudioOnlyMp4Plan plan = AudioOnlyMp4Plan.create(source.ftyp, source.moov);
        byte[] complete = assemble(plan, source.file);

        long partial = plan.outputPositionBefore(7) + 5; // a few bytes into fetch 7
        int resume = plan.resumeIndex(partial);
        assertEquals(7, resume);
        assertEquals(plan.getHeaderLength(), plan.outputPositionBefore(0));
        assertEquals(0, plan.resumeIndex(plan.getHeaderLength()));
        assertEquals(plan.getFetches().size(), plan.resumeIndex(complete.length));

        ByteArrayOutputStream resumed = new ByteArrayOutputStream();
        resumed.write(complete, 0, (int) plan.outputPositionBefore(resume));
        writeFetches(plan, source.file, resume, resumed);
        assertArrayEquals(complete, resumed.toByteArray());
    }

    @Test
    public void rejectsFilesItCannotCut() throws Exception {
        Source source = new Source(false, false, 5, 2, 1_000, new Random(5));
        assertUnsupported(null, withoutAudio(source.moov));
        assertUnsupported(source.ftyp, append(source.moov, box("mvex", new byte[8])));
        assertUnsupported(source.ftyp, Arrays.copyOf(source.moov, source.moov.length - 3));
    }

    @Test
    public void topLevelHeadersAreValidated() throws Exception {
        Source source = new Source(false, true, 5, 2, 1_000, new Random(6));
        Mp4TopLevelBox ftyp = Mp4TopLevelBox.parse(source.file, 16, 0, source.file.length);
        assertEquals("ftyp", ftyp.type);
        byte[] header = Arrays.copyOfRange(source.file, (int) ftyp.end(), (int) ftyp.end() + 16);
        Mp4TopLevelBox mdat = Mp4TopLevelBox.parse(header, 16, ftyp.end(), source.file.length);
        assertEquals("mdat", mdat.type);
        assertEquals(16, mdat.headerLength);

        byte[] html = "<!DOCTYPE html><html>".getBytes(StandardCharsets.ISO_8859_1);
        try {
            Mp4TopLevelBox.parse(html, html.length, 0, 1000);
            fail("HTML accepted as MP4");
        } catch (UnsupportedMp4Exception expected) {
            // expected
        }
        try {
            Mp4TopLevelBox.parse(header, 16, ftyp.end(), mdat.end() - 1);
            fail("Box running past the end of the file accepted");
        } catch (UnsupportedMp4Exception expected) {
            // expected
        }
    }

    private static void assertUnsupported(@Nullable byte[] ftyp, byte[] moov) {
        try {
            AudioOnlyMp4Plan.create(ftyp, moov);
            fail("Expected UnsupportedMp4Exception");
        } catch (UnsupportedMp4Exception expected) {
            // expected
        }
    }

    /** What the downloader writes: the header, then each fetch's kept bytes. */
    private static byte[] assemble(AudioOnlyMp4Plan plan, byte[] file) {
        assertTrue(plan.getRequiredSourceSize() <= file.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = plan.getHeader();
        out.write(header, 0, header.length);
        writeFetches(plan, file, 0, out);
        return out.toByteArray();
    }

    private static void writeFetches(AudioOnlyMp4Plan plan, byte[] file, int from, ByteArrayOutputStream out) {
        List<AudioOnlyMp4Plan.Fetch> fetches = plan.getFetches();
        for (int i = from; i < fetches.size(); i++) {
            AudioOnlyMp4Plan.Fetch fetch = fetches.get(i);
            byte[] fetched = Arrays.copyOfRange(file, (int) fetch.offset, (int) fetch.offset + fetch.length);
            byte[] kept = fetch.extract(fetched);
            out.write(kept, 0, kept.length);
        }
    }

    /** Runs ExoPlayer's MP4 extractor over {@code file}; returns the one track's sample bytes. */
    private static byte[] extractOnlyTrack(byte[] file) throws IOException {
        Mp4Extractor extractor = new Mp4Extractor();
        CollectingOutput output = new CollectingOutput();
        extractor.init(output);
        PositionHolder seek = new PositionHolder();
        long position = 0;
        int result = Extractor.RESULT_CONTINUE;
        while (result != Extractor.RESULT_END_OF_INPUT) {
            DefaultExtractorInput input = new DefaultExtractorInput(
                    new ArrayReader(file, (int) position), position, file.length);
            do {
                result = extractor.read(input, seek);
            } while (result == Extractor.RESULT_CONTINUE);
            if (result == Extractor.RESULT_SEEK) {
                position = seek.position;
            }
        }
        assertEquals(1, output.tracks.size());
        CollectingTrack track = output.tracks.get(0);
        assertEquals(C.TRACK_TYPE_AUDIO, track.type);
        return track.data.toByteArray();
    }

    private static byte[] withoutAudio(byte[] moov) throws UnsupportedMp4Exception {
        Mp4Box moovBox = Mp4Box.read(moov, 0, moov.length);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean skipped = false;
        for (Mp4Box child : moovBox.children(moov)) {
            if (child.type.equals("trak") && !skipped && Mp4Box.readType(moov, child.end - 4).equals("AUDI")) {
                skipped = true;
                continue;
            }
            body.write(moov, child.start, child.size());
        }
        assertTrue(skipped);
        return box("moov", body.toByteArray());
    }

    private static byte[] append(byte[] moov, byte[] extra) {
        byte[] body = Arrays.copyOfRange(moov, 8, moov.length);
        return box("moov", concat(body, extra));
    }

    // ---- building test files ----

    /**
     * An MP4 with a video track and an AC-3 audio track,
     * their chunks interleaved: video, audio, video, audio, ...
     */
    private static final class Source {
        final byte[] ftyp;
        final byte[] moov;
        final byte[] file;
        private final List<byte[]> audio = new ArrayList<>();

        Source(boolean co64, boolean moovAtEnd, int chunkPairs, int audioSamplesPerChunk,
               int videoChunkSize, Random random) {
            ftyp = box("ftyp", concat(ascii("isom"), u32(512), ascii("isomiso2mp41")));
            List<byte[]> videoChunks = new ArrayList<>();
            List<int[]> audioSizes = new ArrayList<>();
            for (int i = 0; i < chunkPairs; i++) {
                byte[] video = new byte[videoChunkSize];
                random.nextBytes(video);
                videoChunks.add(video);
                int[] sizes = new int[audioSamplesPerChunk];
                for (int s = 0; s < audioSamplesPerChunk; s++) {
                    byte[] sample = new byte[4 * (100 + random.nextInt(200))];
                    random.nextBytes(sample);
                    audio.add(sample);
                    sizes[s] = sample.length;
                }
                audioSizes.add(sizes);
            }
            // The moov's size does not depend on the offsets, so build it once to measure it.
            long[] zeros = new long[chunkPairs];
            int moovSize = buildMoov(co64, zeros, zeros, audioSizes, videoChunkSize, chunkPairs).length;
            long mdatStart = moovAtEnd ? ftyp.length : ftyp.length + moovSize;
            long position = mdatStart + 16;
            long[] videoOffsets = new long[chunkPairs];
            long[] audioOffsets = new long[chunkPairs];
            ByteArrayOutputStream media = new ByteArrayOutputStream();
            int sample = 0;
            for (int i = 0; i < chunkPairs; i++) {
                videoOffsets[i] = position;
                media.write(videoChunks.get(i), 0, videoChunkSize);
                position += videoChunkSize;
                audioOffsets[i] = position;
                for (int s = 0; s < audioSamplesPerChunk; s++) {
                    byte[] bytes = audio.get(sample++);
                    media.write(bytes, 0, bytes.length);
                    position += bytes.length;
                }
            }
            moov = buildMoov(co64, videoOffsets, audioOffsets, audioSizes, videoChunkSize, chunkPairs);
            byte[] mdat = concat(u32(1), ascii("mdat"), u64(16L + media.size()), media.toByteArray());
            file = moovAtEnd ? concat(ftyp, mdat, moov) : concat(ftyp, moov, mdat);
        }

        byte[] audioSamples() {
            return concat(audio.toArray(new byte[0][]));
        }

        private static byte[] buildMoov(boolean co64, long[] videoOffsets, long[] audioOffsets,
                                        List<int[]> audioSizes, int videoChunkSize, int chunkPairs) {
            int audioSampleCount = 0;
            ByteArrayOutputStream sizes = new ByteArrayOutputStream();
            for (int[] chunk : audioSizes) {
                for (int size : chunk) {
                    write(sizes, u32(size));
                    audioSampleCount++;
                }
            }
            byte[] mvhd = fullBox("mvhd", concat(u32(0), u32(0), u32(1000), u32(1000),
                    u32(0x00010000), new byte[2 + 10 + 36 + 24], u32(3)));
            byte[] videoTrak = trak(1, "vide", box("avc1", new byte[78]),
                    concat(u32(1), u32(1), u32(1), u32(1)),
                    concat(u32(videoChunkSize), u32(chunkPairs)),
                    chunkOffsets(co64, videoOffsets), chunkPairs, null);
            int samplesPerChunk = audioSizes.get(0).length;
            // AC-3 rather than raw PCM: the extractor hands its samples over whole, where it
            // would re-cut raw PCM into frames.
            byte[] ac3 = box("ac-3", concat(new byte[6], u16(1), new byte[8],
                    u16(2), u16(16), u16(0), u16(0), u32(48000L << 16),
                    box("dac3", new byte[]{0x10, 0x3D, (byte) 0xE0})));
            byte[] audioTrak = trak(2, "soun", ac3,
                    concat(u32(1), u32(1), u32(samplesPerChunk), u32(1)),
                    concat(u32(0), u32(audioSampleCount), sizes.toByteArray()),
                    chunkOffsets(co64, audioOffsets), audioSampleCount,
                    // a reference to the video track, which the plan has to drop with it
                    box("tref", box("chap", u32(1))));
            // A marker box at the end of the audio track, so a test can tell the tracks apart.
            audioTrak = box("trak", concat(Arrays.copyOfRange(audioTrak, 8, audioTrak.length),
                    box("AUDI", new byte[0])));
            return box("moov", concat(mvhd, videoTrak, audioTrak, box("udta", new byte[0])));
        }

        private static byte[] trak(int id, String handler, byte[] sampleEntry, byte[] stsc,
                                   byte[] stsz, byte[] stco, int sampleCount, @Nullable byte[] tref) {
            byte[] tkhd = fullBoxWithFlags("tkhd", 0x000007, concat(u32(0), u32(0), u32(id), u32(0),
                    u32(1000), new byte[8], u16(0), u16(0), u16(0x0100), u16(0),
                    u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0), u32(0), u32(0),
                    u32(0x40000000), u32(0), u32(0)));
            byte[] mdhd = fullBox("mdhd", concat(u32(0), u32(0), u32(44100), u32(sampleCount), u16(0x55c4), u16(0)));
            byte[] hdlr = fullBox("hdlr", concat(u32(0), ascii(handler), new byte[12], new byte[]{0}));
            byte[] dref = fullBox("dref", concat(u32(1), fullBoxWithFlags("url ", 1, new byte[0])));
            byte[] stbl = box("stbl", concat(
                    fullBox("stsd", concat(u32(1), sampleEntry)),
                    fullBox("stts", concat(u32(1), u32(sampleCount), u32(1))),
                    fullBox("stsc", stsc),
                    fullBox("stsz", stsz),
                    stco));
            byte[] smhd = fullBox("smhd", new byte[4]);
            byte[] minf = box("minf", concat(smhd, box("dinf", dref), stbl));
            byte[] mdia = box("mdia", concat(mdhd, hdlr, minf));
            return box("trak", tref != null ? concat(tkhd, tref, mdia) : concat(tkhd, mdia));
        }

        private static byte[] chunkOffsets(boolean co64, long[] offsets) {
            ByteArrayOutputStream table = new ByteArrayOutputStream();
            write(table, u32(offsets.length));
            for (long offset : offsets) {
                write(table, co64 ? u64(offset) : u32(offset));
            }
            return fullBox(co64 ? "co64" : "stco", table.toByteArray());
        }
    }

    private static byte[] box(String type, byte[] content) {
        return concat(u32(8L + content.length), ascii(type), content);
    }

    private static byte[] fullBox(String type, byte[] content) {
        return fullBoxWithFlags(type, 0, content);
    }

    private static byte[] fullBoxWithFlags(String type, int flags, byte[] content) {
        return box(type, concat(u32(flags & 0xFFFFFF), content));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] u16(int value) {
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    private static byte[] u32(long value) {
        byte[] bytes = new byte[4];
        Mp4Box.writeUInt32(bytes, 0, value);
        return bytes;
    }

    private static byte[] u64(long value) {
        byte[] bytes = new byte[8];
        Mp4Box.writeUInt64(bytes, 0, value);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            write(out, part);
        }
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    // ---- a minimal ExtractorOutput ----

    private static final class ArrayReader implements DataReader {
        private final byte[] data;
        private int position;

        ArrayReader(byte[] data, int position) {
            this.data = data;
            this.position = position;
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) {
            if (position >= data.length) {
                return C.RESULT_END_OF_INPUT;
            }
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            return count;
        }
    }

    private static final class CollectingOutput implements ExtractorOutput {
        final List<CollectingTrack> tracks = new ArrayList<>();

        @NonNull
        @Override
        public TrackOutput track(int id, int type) {
            CollectingTrack track = new CollectingTrack(type);
            tracks.add(track);
            return track;
        }

        @Override
        public void endTracks() {
            // nothing to do
        }

        @Override
        public void seekMap(@NonNull SeekMap seekMap) {
            // not needed
        }
    }

    private static final class CollectingTrack implements TrackOutput {
        final int type;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        CollectingTrack(int type) {
            this.type = type;
        }

        @Override
        public void format(@NonNull Format format) {
            // not needed
        }

        @Override
        public int sampleData(@NonNull DataReader input, int length, boolean allowEndOfInput,
                              int sampleDataPart) throws IOException {
            byte[] buffer = new byte[length];
            int read = input.read(buffer, 0, length);
            if (read == C.RESULT_END_OF_INPUT) {
                return C.RESULT_END_OF_INPUT;
            }
            data.write(buffer, 0, read);
            return read;
        }

        @Override
        public void sampleData(@NonNull ParsableByteArray bytes, int length, int sampleDataPart) {
            byte[] buffer = new byte[length];
            bytes.readBytes(buffer, 0, length);
            data.write(buffer, 0, length);
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset,
                                   @Nullable CryptoData cryptoData) {
            // not needed
        }
    }
}
