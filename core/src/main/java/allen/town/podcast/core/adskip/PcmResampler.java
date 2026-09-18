package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;

/**
 * Downmixes interleaved PCM to mono and resamples it to the analysis rate, in a streaming fashion.
 *
 * <p>Split out of {@link PcmDecoder} so that the only arithmetic in the decoder is buffer index
 * bookkeeping: this class is plain Java and testable on the JVM, the decoder is not.
 *
 * <p>The resampler is a box filter, not a windowed-sinc: each output sample is the mean of the
 * input samples that fall into its interval. That is aliasing-prone in the strict sense, but the
 * features computed downstream are band energies and level statistics averaged over a full second,
 * and a box filter preserves those faithfully while costing one add per input sample. When the
 * source rate is below the target the same loop degenerates to sample-and-hold, which is fine for
 * the same reason.
 */
final class PcmResampler {

    private static final int OUTPUT_CHUNK = 4096;

    private final int channels;
    private final double ratio;
    private final PcmDecoder.Sink sink;
    private final float[] output = new float[OUTPUT_CHUNK];

    private int outputCount;
    private double accumulator;
    private int accumulated;
    private double phase;
    private float lastEmitted;

    /** Leftover interleaved values from a previous write that did not complete a frame. */
    private final float[] partialFrame;
    private int partialCount;

    PcmResampler(int sourceRate, int channels, int targetRate, @NonNull PcmDecoder.Sink sink) {
        if (sourceRate <= 0) {
            throw new IllegalArgumentException("source rate must be positive");
        }
        this.channels = Math.max(1, channels);
        this.ratio = (double) targetRate / sourceRate;
        this.sink = sink;
        this.partialFrame = new float[this.channels];
    }

    /**
     * Consumes {@code count} interleaved values (frames times channels, not necessarily a whole
     * number of frames) and pushes whatever mono output they complete to the sink.
     */
    void write(@NonNull float[] interleaved, int count) {
        int index = 0;
        while (index < count) {
            if (partialCount > 0 || count - index < channels) {
                int take = Math.min(channels - partialCount, count - index);
                System.arraycopy(interleaved, index, partialFrame, partialCount, take);
                partialCount += take;
                index += take;
                if (partialCount == channels) {
                    pushFrame(partialFrame, 0);
                    partialCount = 0;
                }
                continue;
            }
            pushFrame(interleaved, index);
            index += channels;
        }
    }

    /** Flushes buffered output. Any incomplete trailing frame is discarded. */
    void flush() {
        if (outputCount > 0) {
            sink.onPcm(output, 0, outputCount);
            outputCount = 0;
        }
    }

    private void pushFrame(float[] source, int offset) {
        double sum = 0;
        for (int c = 0; c < channels; c++) {
            sum += source[offset + c];
        }
        float mono = (float) (sum / channels);

        accumulator += mono;
        accumulated++;
        phase += ratio;
        while (phase >= 1.0) {
            if (accumulated > 0) {
                lastEmitted = (float) (accumulator / accumulated);
            }
            emit(lastEmitted);
            phase -= 1.0;
            accumulator = 0;
            accumulated = 0;
        }
    }

    private void emit(float sample) {
        output[outputCount++] = sample;
        if (outputCount == OUTPUT_CHUNK) {
            sink.onPcm(output, 0, outputCount);
            outputCount = 0;
        }
    }
}
