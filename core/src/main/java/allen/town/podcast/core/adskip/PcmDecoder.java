package allen.town.podcast.core.adskip;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Decodes a downloaded episode file to mono {@value #TARGET_SAMPLE_RATE} Hz float PCM and streams
 * it to a {@link Sink}.
 *
 * <p>This is the one class in the ad-skip package that touches the Android media stack, so it is
 * kept as thin as possible: it owns the {@link MediaExtractor}/{@link MediaCodec} lifecycle and
 * nothing else. Downmixing and rate conversion live in {@link PcmResampler} and all feature
 * arithmetic lives in {@link AudioFeatureExtractor}, both of which are unit tested on the JVM. This
 * class cannot be; it is verified on device.
 *
 * <p>It streams rather than returning a buffer on purpose. A three hour episode at 16 kHz as float
 * is about 700 MB, which no phone will give us; the sink sees at most a few kilobytes at a time and
 * the decoder never holds more than one codec buffer.
 *
 * <p>Handles 16-bit integer and float output encodings, any channel count, and end of stream. If
 * the file has no audio track, the device has no decoder for it, or the codec stops producing
 * output, a {@link DecodeException} is thrown; nothing is swallowed.
 */
public final class PcmDecoder {

    /** Everything downstream assumes this rate. */
    public static final int TARGET_SAMPLE_RATE = AudioFeatureExtractor.SAMPLE_RATE;

    private static final long IO_TIMEOUT_US = 10_000L;

    /**
     * Consecutive dequeue attempts that may make no progress before the decode is declared stuck.
     * At 10 ms per attempt this is about 20 s of a codec producing nothing.
     */
    private static final int MAX_STALLED_ATTEMPTS = 2000;

    /** Receives mono float PCM at {@link #TARGET_SAMPLE_RATE}, in chunks of arbitrary size. */
    public interface Sink {
        void onPcm(@NonNull float[] samples, int offset, int length);
    }

    private PcmDecoder() {
    }

    /**
     * Decodes {@code path} into {@code sink}.
     *
     * @param cancelled polled between codec buffers; when it reports true the decode returns
     *                  cleanly and the sink simply sees a truncated stream.
     * @throws DecodeException if the file cannot be opened, has no audio track, has no available
     *                         decoder, or the decoder fails or stalls.
     */
    public static void decode(@Nullable String path, @NonNull Sink sink,
                              @Nullable Cancellation cancelled) throws DecodeException {
        if (path == null || path.isEmpty()) {
            throw new DecodeException("no file path given");
        }
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) {
            throw new DecodeException("not a readable file: " + path);
        }
        Cancellation cancel = cancelled != null ? cancelled : Cancellation.NEVER;

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            try {
                extractor.setDataSource(path);
            } catch (IOException | IllegalArgumentException e) {
                throw new DecodeException("cannot open " + path, e);
            }

            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new DecodeException("no audio track in " + path);
            }
            MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new DecodeException("audio track has no mime type");
            }
            extractor.selectTrack(trackIndex);

            try {
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(inputFormat, null, null, 0);
                codec.start();
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                throw new DecodeException("no usable decoder for " + mime, e);
            }

            drain(extractor, codec, inputFormat, sink, cancel);
        } catch (MediaCodec.CodecException e) {
            throw new DecodeException("decoder failed on " + path, e);
        } catch (IllegalStateException e) {
            throw new DecodeException("decoder in a bad state for " + path, e);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (IllegalStateException e) {
                    // Already stopped or never started; releasing below is what matters.
                    android.util.Log.w("PcmDecoder", "codec.stop() failed", e);
                }
                codec.release();
            }
            extractor.release();
        }
    }

    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private static void drain(MediaExtractor extractor, MediaCodec codec, MediaFormat inputFormat,
                              Sink sink, Cancellation cancel) throws DecodeException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        PcmResampler resampler = newResampler(inputFormat, sink);
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        float[] scratch = new float[8192];

        boolean inputDone = false;
        boolean outputDone = false;
        int stalled = 0;

        while (!outputDone) {
            if (cancel.isCancelled()) {
                break;
            }
            boolean progressed = false;

            if (!inputDone) {
                int inputIndex = codec.dequeueInputBuffer(IO_TIMEOUT_US);
                if (inputIndex >= 0) {
                    progressed = true;
                    ByteBuffer input = codec.getInputBuffer(inputIndex);
                    int read = input == null ? -1 : extractor.readSampleData(input, 0);
                    if (read < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, read, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outputIndex = codec.dequeueOutputBuffer(info, IO_TIMEOUT_US);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                progressed = true;
                MediaFormat outputFormat = codec.getOutputFormat();
                resampler.flush();
                resampler = newResampler(outputFormat, sink);
                encoding = pcmEncodingOf(outputFormat);
            } else if (outputIndex >= 0) {
                progressed = true;
                if (info.size > 0
                        && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    ByteBuffer output = codec.getOutputBuffer(outputIndex);
                    if (output != null) {
                        output.position(info.offset);
                        output.limit(info.offset + info.size);
                        scratch = convert(output, encoding, scratch, resampler);
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
            }

            stalled = progressed ? 0 : stalled + 1;
            if (stalled > MAX_STALLED_ATTEMPTS) {
                throw new DecodeException("decoder stopped making progress");
            }
        }
        resampler.flush();
    }

    private static PcmResampler newResampler(MediaFormat format, Sink sink) {
        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : TARGET_SAMPLE_RATE;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        return new PcmResampler(Math.max(1, sampleRate), channels, TARGET_SAMPLE_RATE, sink);
    }

    private static int pcmEncodingOf(MediaFormat format) {
        // KEY_PCM_ENCODING only exists from API 24; below that everything is 16-bit integer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            return format.getInteger(MediaFormat.KEY_PCM_ENCODING);
        }
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    /**
     * Converts one codec output buffer to float and hands it to the resampler.
     *
     * @return the scratch array, grown if it had to be.
     */
    private static float[] convert(ByteBuffer buffer, int encoding, float[] scratch,
                                   PcmResampler resampler) throws DecodeException {
        ByteBuffer ordered = buffer.slice().order(ByteOrder.nativeOrder());
        float[] out = scratch;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            FloatBuffer floats = ordered.asFloatBuffer();
            int count = floats.remaining();
            if (out.length < count) {
                out = new float[count];
            }
            floats.get(out, 0, count);
            resampler.write(out, count);
        } else if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            ShortBuffer shorts = ordered.asShortBuffer();
            int count = shorts.remaining();
            if (out.length < count) {
                out = new float[count];
            }
            for (int i = 0; i < count; i++) {
                out[i] = shorts.get(i) / 32768f;
            }
            resampler.write(out, count);
        } else {
            throw new DecodeException("unsupported PCM encoding " + encoding);
        }
        return out;
    }
}
