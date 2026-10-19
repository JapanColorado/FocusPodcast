package dev.russell.focuspodcast.core.dsp;

/**
 * A second-order IIR section (RBJ "Audio EQ Cookbook" designs) run in transposed direct form II
 * over interleaved float samples, with independent state per channel. Not thread safe.
 */
final class Biquad {
    /** State this small is inaudible; flushing it avoids denormal slowdowns in long silences. */
    private static final float DENORMAL = 1e-20f;

    private final int channels;
    private final float[] z1;
    private final float[] z2;
    private float b0 = 1f;
    private float b1;
    private float b2;
    private float a1;
    private float a2;

    Biquad(int channels) {
        this.channels = channels;
        this.z1 = new float[channels];
        this.z2 = new float[channels];
    }

    /** Butterworth-style high-pass at {@code f0} Hz with quality {@code q}. */
    void setHighPass(double sampleRate, double f0, double q) {
        if (f0 <= 0 || f0 >= 0.45 * sampleRate) {
            setIdentity();
            return;
        }
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * q);
        double a0 = 1 + alpha;
        set((1 + cos) / 2 / a0, -(1 + cos) / a0, (1 + cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
    }

    /** Peaking EQ: {@code gainDb} at {@code f0} Hz, bandwidth set by {@code q}. */
    void setPeaking(double sampleRate, double f0, double q, double gainDb) {
        if (gainDb == 0 || f0 <= 0 || f0 >= 0.45 * sampleRate) {
            setIdentity();
            return;
        }
        double amp = Math.pow(10, gainDb / 40);
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * q);
        double a0 = 1 + alpha / amp;
        set((1 + alpha * amp) / a0, -2 * cos / a0, (1 - alpha * amp) / a0, -2 * cos / a0, (1 - alpha / amp) / a0);
    }

    /** Butterworth-style low-pass at {@code f0} Hz with quality {@code q}. */
    void setLowPass(double sampleRate, double f0, double q) {
        if (f0 <= 0 || f0 >= 0.45 * sampleRate) {
            setIdentity();
            return;
        }
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double cos = Math.cos(w0);
        double alpha = Math.sin(w0) / (2 * q);
        double a0 = 1 + alpha;
        set((1 - cos) / 2 / a0, (1 - cos) / a0, (1 - cos) / 2 / a0, -2 * cos / a0, (1 - alpha) / a0);
    }

    /** Low shelf: {@code gainDb} below about {@code f0} Hz, slope set by {@code q}. */
    void setLowShelf(double sampleRate, double f0, double q, double gainDb) {
        if (gainDb == 0 || f0 <= 0 || f0 >= 0.45 * sampleRate) {
            setIdentity();
            return;
        }
        double amp = Math.pow(10, gainDb / 40);
        double w0 = 2 * Math.PI * f0 / sampleRate;
        double cos = Math.cos(w0);
        double twoSqrtAAlpha = 2 * Math.sqrt(amp) * Math.sin(w0) / (2 * q);
        double a0 = (amp + 1) + (amp - 1) * cos + twoSqrtAAlpha;
        set(amp * ((amp + 1) - (amp - 1) * cos + twoSqrtAAlpha) / a0,
                2 * amp * ((amp - 1) - (amp + 1) * cos) / a0,
                amp * ((amp + 1) - (amp - 1) * cos - twoSqrtAAlpha) / a0,
                -2 * ((amp - 1) + (amp + 1) * cos) / a0,
                ((amp + 1) + (amp - 1) * cos - twoSqrtAAlpha) / a0);
    }

    void setIdentity() {
        set(1, 0, 0, 0, 0);
    }

    private void set(double b0, double b1, double b2, double a1, double a2) {
        this.b0 = (float) b0;
        this.b1 = (float) b1;
        this.b2 = (float) b2;
        this.a1 = (float) a1;
        this.a2 = (float) a2;
    }

    boolean isIdentity() {
        return b0 == 1f && b1 == 0f && b2 == 0f && a1 == 0f && a2 == 0f;
    }

    /** Filters {@code frames} interleaved frames of {@code buffer} in place. */
    void process(float[] buffer, int frames) {
        if (isIdentity()) {
            return;
        }
        for (int ch = 0; ch < channels; ch++) {
            float s1 = z1[ch];
            float s2 = z2[ch];
            for (int i = ch, end = frames * channels; i < end; i += channels) {
                float x = buffer[i];
                float y = b0 * x + s1;
                s1 = b1 * x - a1 * y + s2;
                s2 = b2 * x - a2 * y;
                buffer[i] = y;
            }
            z1[ch] = Math.abs(s1) < DENORMAL ? 0f : s1;
            z2[ch] = Math.abs(s2) < DENORMAL ? 0f : s2;
        }
    }

    /**
     * Runs three sections back to back over {@code buffer} in one pass, with the same arithmetic
     * as calling {@link #process} on each in turn. All three must have the same channel count.
     */
    static void processCascade(float[] buffer, int frames, Biquad first, Biquad second, Biquad third) {
        int channels = first.channels;
        float ab0 = first.b0, ab1 = first.b1, ab2 = first.b2, aa1 = first.a1, aa2 = first.a2;
        float bb0 = second.b0, bb1 = second.b1, bb2 = second.b2, ba1 = second.a1, ba2 = second.a2;
        float cb0 = third.b0, cb1 = third.b1, cb2 = third.b2, ca1 = third.a1, ca2 = third.a2;
        for (int ch = 0; ch < channels; ch++) {
            float a1s = first.z1[ch];
            float a2s = first.z2[ch];
            float b1s = second.z1[ch];
            float b2s = second.z2[ch];
            float c1s = third.z1[ch];
            float c2s = third.z2[ch];
            for (int i = ch, end = frames * channels; i < end; i += channels) {
                float x = buffer[i];
                float y = ab0 * x + a1s;
                a1s = ab1 * x - aa1 * y + a2s;
                a2s = ab2 * x - aa2 * y;
                x = y;
                y = bb0 * x + b1s;
                b1s = bb1 * x - ba1 * y + b2s;
                b2s = bb2 * x - ba2 * y;
                x = y;
                y = cb0 * x + c1s;
                c1s = cb1 * x - ca1 * y + c2s;
                c2s = cb2 * x - ca2 * y;
                buffer[i] = y;
            }
            first.z1[ch] = Math.abs(a1s) < DENORMAL ? 0f : a1s;
            first.z2[ch] = Math.abs(a2s) < DENORMAL ? 0f : a2s;
            second.z1[ch] = Math.abs(b1s) < DENORMAL ? 0f : b1s;
            second.z2[ch] = Math.abs(b2s) < DENORMAL ? 0f : b2s;
            third.z1[ch] = Math.abs(c1s) < DENORMAL ? 0f : c1s;
            third.z2[ch] = Math.abs(c2s) < DENORMAL ? 0f : c2s;
        }
    }

    void clear() {
        java.util.Arrays.fill(z1, 0f);
        java.util.Arrays.fill(z2, 0f);
    }
}
