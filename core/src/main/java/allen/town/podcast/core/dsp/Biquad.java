package allen.town.podcast.core.dsp;

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

    void clear() {
        java.util.Arrays.fill(z1, 0f);
        java.util.Arrays.fill(z2, 0f);
    }
}
