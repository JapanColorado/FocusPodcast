package allen.town.podcast.core.adskip;

/**
 * A minimal in-place radix-2 Cooley-Tukey FFT, sized once and reused.
 *
 * <p>This exists because the ad detector needs a power spectrum a few hundred thousand times per
 * episode and the app is not allowed to gain a new dependency for it (F-Droid build, no native
 * libraries, no DSP jar). It owns nothing but its twiddle tables, its bit-reversal permutation and
 * a pair of scratch buffers, so a single instance can be reused for every sub-window of an episode
 * without allocating.
 *
 * <p>It is deliberately not thread safe: the scratch buffers are instance state. One instance per
 * analysis thread.
 */
final class Fft {

    private final int size;
    private final int[] reverse;
    private final float[] twiddleCos;
    private final float[] twiddleSin;
    private final float[] scratchRe;
    private final float[] scratchIm;

    /**
     * @param size transform length; must be a power of two and at least 2.
     */
    Fft(int size) {
        if (size < 2 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of two >= 2, was " + size);
        }
        this.size = size;
        this.reverse = new int[size];
        this.twiddleCos = new float[size / 2];
        this.twiddleSin = new float[size / 2];
        this.scratchRe = new float[size];
        this.scratchIm = new float[size];

        int bits = Integer.numberOfTrailingZeros(size);
        for (int i = 0; i < size; i++) {
            reverse[i] = Integer.reverse(i) >>> (32 - bits);
        }
        // Twiddles for the whole transform: exp(-2*pi*i*j/size) for j < size/2. A stage of length
        // "len" reads them with a stride of size/len, so one table serves every stage.
        for (int j = 0; j < size / 2; j++) {
            double angle = -2.0 * Math.PI * j / size;
            twiddleCos[j] = (float) Math.cos(angle);
            twiddleSin[j] = (float) Math.sin(angle);
        }
    }

    int size() {
        return size;
    }

    /**
     * In-place forward complex transform. Both arrays must have exactly {@link #size()} entries.
     */
    void forward(float[] re, float[] im) {
        if (re.length != size || im.length != size) {
            throw new IllegalArgumentException("arrays must be of length " + size);
        }
        // Decimation in time: permute into bit-reversed order first, then combine in place.
        for (int i = 0; i < size; i++) {
            int j = reverse[i];
            if (i < j) {
                float tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                float ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= size; len <<= 1) {
            int half = len >> 1;
            int stride = size / len;
            for (int base = 0; base < size; base += len) {
                int twiddle = 0;
                for (int k = 0; k < half; k++) {
                    float wr = twiddleCos[twiddle];
                    float wi = twiddleSin[twiddle];
                    twiddle += stride;

                    int a = base + k;
                    int b = a + half;
                    float vr = re[b] * wr - im[b] * wi;
                    float vi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - vr;
                    im[b] = im[a] - vi;
                    re[a] = re[a] + vr;
                    im[a] = im[a] + vi;
                }
            }
        }
    }

    /**
     * Power spectrum |X[k]|^2 of a real signal, for k = 0 .. size/2 inclusive.
     *
     * @param samples source buffer, read from {@code offset} for {@link #size()} values.
     * @param out     destination of length {@code size/2 + 1}.
     */
    void powerSpectrum(float[] samples, int offset, float[] out) {
        if (out.length != size / 2 + 1) {
            throw new IllegalArgumentException("out must be of length " + (size / 2 + 1));
        }
        System.arraycopy(samples, offset, scratchRe, 0, size);
        java.util.Arrays.fill(scratchIm, 0f);
        forward(scratchRe, scratchIm);
        for (int k = 0; k <= size / 2; k++) {
            out[k] = scratchRe[k] * scratchRe[k] + scratchIm[k] * scratchIm[k];
        }
    }

    /** Allocating convenience wrapper; used by tests and one-off callers. */
    float[] powerSpectrum(float[] samples) {
        float[] out = new float[size / 2 + 1];
        powerSpectrum(samples, 0, out);
        return out;
    }

    /** Periodic Hann window of the given length, the shape used for every analysis sub-window. */
    static float[] hann(int length) {
        float[] w = new float[length];
        for (int i = 0; i < length; i++) {
            w[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / length));
        }
        return w;
    }
}
