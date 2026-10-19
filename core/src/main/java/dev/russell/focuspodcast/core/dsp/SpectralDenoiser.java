package dev.russell.focuspodcast.core.dsp;

import java.util.Arrays;

/**
 * Stationary-noise reduction by short-time spectral gain, streaming and sample-synchronous: every
 * call writes exactly as many frames as it reads, delayed by {@link #latencyFrames()}.
 *
 * <p>Analysis: frames of {@code n} samples (about 20 ms), hop {@code n/2}, square-root periodic
 * Hann window on analysis and on synthesis, so that with all gains at 1 the overlap-add
 * reconstructs the input exactly. Two real channels share one complex FFT (left in the real part,
 * right in the imaginary part); since the same real, symmetric gain is applied to both, the pair
 * never has to be separated, which halves the transform cost for stereo.
 *
 * <p>Noise floor: minimum statistics (Martin 2001, simplified). The per-bin power, averaged over
 * channels and smoothed over about 40 ms, is tracked for its minimum over a window of about
 * 1.5 s split into sub-windows; that minimum times a bias correction is the noise estimate. There
 * is no "learn the noise" step: speech has pauses between words and phrases, which is where the
 * minimum comes from, and the estimate follows a changing background within a window length.
 *
 * <p>Gain: Wiener gain on the decision-directed a-priori SNR (Ephraim and Malah 1984) with a
 * smoothing factor of 0.98, which is what keeps the residual noise from turning into "musical
 * noise" (isolated bins popping in and out), and a floor that caps the attenuation, so a mistaken
 * estimate can never remove more than the floor allows and the residual stays natural.
 *
 * <p>Not thread safe; one instance per audio stream.
 */
final class SpectralDenoiser {
    /** Decision-directed smoothing; 0.98 is the classic value for ~10 ms hops. */
    private static final float DD_ALPHA = 0.98f;
    /** Time constant of the power smoothing feeding the minimum tracker. */
    private static final double SMOOTHING_SECONDS = 0.04;
    /** Length of the minimum-statistics search window, and the number of sub-windows in it. */
    private static final double MIN_WINDOW_SECONDS = 1.5;
    private static final int SUB_WINDOWS = 6;
    /**
     * Ratio of the mean noise power to the minimum of its smoothed periodogram over the search
     * window, for the smoothing and window above. Measured on white noise (see the tests).
     */
    private static final float MIN_BIAS = 2.7f;
    private static final float TINY = 1e-20f;

    private final int channels;
    private final int pairs;
    private final int n;
    private final int hop;
    private final int bins;
    private final Fft fft;
    /** For a channel left without a partner (mono, or the last of an odd count). */
    private final RealFft realFft;
    private final float[] window;

    private final float[][] inFifo;
    private final float[][] overlap;
    private final float[][] ready;
    private final float[][] re;
    private final float[][] im;
    private final float[] frame;
    private final float[] spectrumRe;
    private final float[] spectrumIm;
    private int fill;

    private final float[] power;
    private final float[] smoothed;
    private final float[] currentMin;
    private final float[][] subMin;
    private final float[] minOfSubs;
    private final float[] gain;
    private final float[] previousCleanSnr;
    private final float smoothing;
    private final int subWindowFrames;
    private final int warmupFrames;
    private int framesInSub;
    private int subIndex;
    private int subsFilled;
    private int framesSinceReset;
    private boolean ddPrimed;

    private float gainFloor = 1f;
    private float overSubtraction = 1f;

    SpectralDenoiser(int sampleRate, int channels) {
        this.channels = channels;
        this.pairs = (channels + 1) / 2;
        this.n = frameSizeFor(sampleRate);
        this.hop = n / 2;
        this.bins = n / 2 + 1;
        this.fft = new Fft(n);
        this.realFft = new RealFft(n);
        this.window = Fft.hann(n);
        for (int i = 0; i < n; i++) {
            window[i] = (float) Math.sqrt(window[i]);
        }
        inFifo = new float[channels][n];
        overlap = new float[channels][n];
        ready = new float[channels][hop];
        re = new float[pairs][n];
        im = new float[pairs][n];
        frame = new float[n];
        spectrumRe = new float[bins];
        spectrumIm = new float[bins];

        power = new float[bins];
        smoothed = new float[bins];
        currentMin = new float[bins];
        subMin = new float[SUB_WINDOWS][bins];
        minOfSubs = new float[bins];
        gain = new float[bins];
        previousCleanSnr = new float[bins];
        double hopSeconds = (double) hop / sampleRate;
        smoothing = (float) Math.exp(-hopSeconds / SMOOTHING_SECONDS);
        subWindowFrames = Math.max(1,
                (int) Math.round(MIN_WINDOW_SECONDS / hopSeconds / SUB_WINDOWS));
        warmupFrames = subWindowFrames * SUB_WINDOWS;
        reset();
    }

    /** The power of two closest to 20 ms, at least 64. */
    static int frameSizeFor(int sampleRate) {
        double target = Math.max(64, sampleRate * 0.02);
        int lower = Integer.highestOneBit((int) target);
        int upper = lower << 1;
        return (target - lower) <= (upper - target) ? lower : upper;
    }

    int latencyFrames() {
        return n;
    }

    /**
     * @param maxReductionDb the most a bin is ever attenuated
     * @param overSubtraction factor on the noise estimate; above 1 trades speech detail for a
     *                        cleaner background
     */
    void setStrength(float maxReductionDb, float overSubtraction) {
        this.gainFloor = (float) Math.pow(10, -maxReductionDb / 20.0);
        this.overSubtraction = overSubtraction;
    }

    /** Processes {@code frames} interleaved frames of {@code buffer} in place. */
    void process(float[] buffer, int frames) {
        int done = 0;
        while (done < frames) {
            int length = Math.min(hop - fill, frames - done);
            int slot = n - hop + fill;
            for (int ch = 0; ch < channels; ch++) {
                float[] in = inFifo[ch];
                float[] out = ready[ch];
                for (int f = 0, i = done * channels + ch; f < length; f++, i += channels) {
                    in[slot + f] = buffer[i];
                    buffer[i] = out[fill + f];
                }
            }
            fill += length;
            done += length;
            if (fill == hop) {
                processFrame();
                fill = 0;
            }
        }
    }

    private boolean isUnpaired(int pair) {
        return 2 * pair + 1 >= channels;
    }

    private void processFrame() {
        // |X_left|^2 + |X_right|^2 = (|Z[k]|^2 + |Z[n-k]|^2) / 2 for Z = left + i*right; an
        // unpaired channel's real transform counts as |X|^2 = (|X[k]|^2 + |X[n-k]|^2) / 2 as well.
        float perChannel = 0.5f / channels;
        Arrays.fill(power, 0f);
        for (int p = 0; p < pairs; p++) {
            float[] left = inFifo[2 * p];
            if (isUnpaired(p)) {
                for (int i = 0; i < n; i++) {
                    frame[i] = left[i] * window[i];
                }
                realFft.forward(frame, spectrumRe, spectrumIm);
                float scale = 2f * perChannel;
                for (int k = 0; k < bins; k++) {
                    power[k] += (spectrumRe[k] * spectrumRe[k] + spectrumIm[k] * spectrumIm[k]) * scale;
                }
                continue;
            }
            float[] pr = re[p];
            float[] pi = im[p];
            float[] right = inFifo[2 * p + 1];
            for (int i = 0; i < n; i++) {
                float w = window[i];
                pr[i] = left[i] * w;
                pi[i] = right[i] * w;
            }
            fft.forward(pr, pi);
            for (int k = 0; k < bins; k++) {
                int mirror = (n - k) & (n - 1);
                power[k] += (pr[k] * pr[k] + pi[k] * pi[k]
                        + pr[mirror] * pr[mirror] + pi[mirror] * pi[mirror]) * perChannel;
            }
        }

        updateNoiseFloor();
        computeGains();

        for (int p = 0; p < pairs; p++) {
            float[] left = overlap[2 * p];
            if (isUnpaired(p)) {
                for (int k = 0; k < bins; k++) {
                    float g = gain[k];
                    spectrumRe[k] *= g;
                    spectrumIm[k] *= g;
                }
                realFft.inverse(spectrumRe, spectrumIm, frame);
                for (int i = 0; i < n; i++) {
                    left[i] += frame[i] * window[i];
                }
                continue;
            }
            float[] pr = re[p];
            float[] pi = im[p];
            for (int k = 0; k < bins; k++) {
                float g = gain[k];
                pr[k] *= g;
                pi[k] *= g;
                if (k != 0 && k != n / 2) {
                    pr[n - k] *= g;
                    pi[n - k] *= g;
                }
            }
            fft.inverse(pr, pi);
            float[] right = overlap[2 * p + 1];
            for (int i = 0; i < n; i++) {
                float w = window[i];
                left[i] += pr[i] * w;
                right[i] += pi[i] * w;
            }
        }

        for (int ch = 0; ch < channels; ch++) {
            float[] acc = overlap[ch];
            System.arraycopy(acc, 0, ready[ch], 0, hop);
            System.arraycopy(acc, hop, acc, 0, n - hop);
            Arrays.fill(acc, n - hop, n, 0f);
            float[] in = inFifo[ch];
            System.arraycopy(in, hop, in, 0, n - hop);
        }
    }

    private void updateNoiseFloor() {
        if (framesSinceReset == 0) {
            System.arraycopy(power, 0, smoothed, 0, bins);
        } else {
            float keep = smoothing;
            float take = 1f - keep;
            for (int k = 0; k < bins; k++) {
                smoothed[k] = keep * smoothed[k] + take * power[k];
            }
        }
        for (int k = 0; k < bins; k++) {
            if (smoothed[k] < currentMin[k]) {
                currentMin[k] = smoothed[k];
            }
        }
        framesInSub++;
        if (framesInSub >= subWindowFrames) {
            System.arraycopy(currentMin, 0, subMin[subIndex], 0, bins);
            subIndex = (subIndex + 1) % SUB_WINDOWS;
            subsFilled = Math.min(SUB_WINDOWS, subsFilled + 1);
            Arrays.fill(minOfSubs, Float.MAX_VALUE);
            for (int s = 0; s < subsFilled; s++) {
                float[] m = subMin[s];
                for (int k = 0; k < bins; k++) {
                    if (m[k] < minOfSubs[k]) {
                        minOfSubs[k] = m[k];
                    }
                }
            }
            System.arraycopy(smoothed, 0, currentMin, 0, bins);
            framesInSub = 0;
        }
        if (framesSinceReset < warmupFrames) {
            framesSinceReset++;
        }
    }

    private void computeGains() {
        // Until a whole search window has been seen, the minimum may still sit on speech, so the
        // allowed attenuation ramps in rather than starting at full depth.
        float floor = gainFloor;
        if (framesSinceReset < warmupFrames) {
            float progress = (float) framesSinceReset / warmupFrames;
            floor = 1f - (1f - gainFloor) * progress;
        }
        float scale = MIN_BIAS * overSubtraction;
        for (int k = 0; k < bins; k++) {
            float noise = Math.min(currentMin[k], minOfSubs[k]) * scale + TINY;
            float posteriori = power[k] / noise;
            float instant = Math.max(posteriori - 1f, 0f);
            float priori = ddPrimed
                    ? DD_ALPHA * previousCleanSnr[k] + (1f - DD_ALPHA) * instant
                    : instant;
            float g = priori / (1f + priori);
            if (g < floor) {
                g = floor;
            }
            gain[k] = g;
            previousCleanSnr[k] = g * g * posteriori;
        }
        ddPrimed = true;
    }

    /**
     * The current noise power estimate of {@code bin} (bias-corrected, before over-subtraction),
     * in the same units as the windowed power spectrum: white noise of variance s^2 reads about
     * {@code s^2 * n / 2}. For tests.
     */
    float noiseEstimate(int bin) {
        return Math.min(currentMin[bin], minOfSubs[bin]) * MIN_BIAS;
    }

    int frameSize() {
        return n;
    }

    /**
     * Drops the audio in flight (after a seek nothing of the old position may leak out) but keeps
     * the learned noise floor: a seek stays within the same recording.
     */
    void flushSignal() {
        for (int ch = 0; ch < channels; ch++) {
            Arrays.fill(inFifo[ch], 0f);
            Arrays.fill(overlap[ch], 0f);
            Arrays.fill(ready[ch], 0f);
        }
        fill = 0;
    }

    /** Forgets everything, including the noise floor. */
    void reset() {
        flushSignal();
        Arrays.fill(smoothed, 0f);
        Arrays.fill(currentMin, Float.MAX_VALUE);
        Arrays.fill(minOfSubs, Float.MAX_VALUE);
        Arrays.fill(previousCleanSnr, 0f);
        Arrays.fill(gain, 1f);
        framesInSub = 0;
        subIndex = 0;
        subsFilled = 0;
        framesSinceReset = 0;
        ddPrimed = false;
    }
}
