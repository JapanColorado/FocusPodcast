package dev.russell.yagi.core.dsp;

/**
 * Transform of a real signal of length {@code n} through one complex {@link Fft} of length
 * {@code n/2}: even samples go in the real part, odd samples in the imaginary part, and a
 * post-pass untangles the two half spectra. About half the work of a full complex transform with
 * a zero imaginary part, which is what a mono stream otherwise costs {@link SpectralDenoiser}.
 *
 * <p>Not thread safe: the scratch buffers are instance state.
 */
final class RealFft {
    private final int n;
    private final int half;
    private final Fft fft;
    /** exp(-2*pi*i*k/n) for k <= n/2. */
    private final float[] cos;
    private final float[] sin;
    private final float[] zr;
    private final float[] zi;

    /** @param n transform length; a power of two, at least 4. */
    RealFft(int n) {
        if (n < 4 || Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("real FFT size must be a power of two >= 4, was " + n);
        }
        this.n = n;
        this.half = n / 2;
        this.fft = new Fft(half);
        this.cos = new float[half + 1];
        this.sin = new float[half + 1];
        for (int k = 0; k <= half; k++) {
            double angle = -2.0 * Math.PI * k / n;
            cos[k] = (float) Math.cos(angle);
            sin[k] = (float) Math.sin(angle);
        }
        this.zr = new float[half];
        this.zi = new float[half];
    }

    int size() {
        return n;
    }

    /**
     * Spectrum X[k], k = 0 .. n/2, of the {@code n} real samples in {@code x} (left untouched).
     * Matches a complex forward transform of {@code x} with a zero imaginary part.
     */
    void forward(float[] x, float[] outRe, float[] outIm) {
        for (int i = 0; i < half; i++) {
            zr[i] = x[2 * i];
            zi[i] = x[2 * i + 1];
        }
        fft.forward(zr, zi);
        for (int k = 0; k <= half; k++) {
            int a = k == half ? 0 : k;
            int b = k == 0 ? 0 : half - k;
            float zkr = zr[a];
            float zki = zi[a];
            float zmr = zr[b];
            float zmi = -zi[b];
            // even part (Z[k] + conj Z[h-k]) / 2, odd part (Z[k] - conj Z[h-k]) / 2i
            float er = 0.5f * (zkr + zmr);
            float ei = 0.5f * (zki + zmi);
            float or = 0.5f * (zki - zmi);
            float oi = -0.5f * (zkr - zmr);
            float wr = cos[k];
            float wi = sin[k];
            outRe[k] = er + wr * or - wi * oi;
            outIm[k] = ei + wr * oi + wi * or;
        }
    }

    /**
     * The {@code n} real samples whose spectrum is X[k], k = 0 .. n/2 (the inputs are left
     * untouched), scaled so that {@code inverse(forward(x)) == x}.
     */
    void inverse(float[] inRe, float[] inIm, float[] x) {
        for (int k = 0; k < half; k++) {
            int m = half - k;
            float xr = inRe[k];
            float xi = inIm[k];
            float cr = inRe[m];
            float ci = -inIm[m];
            float er = 0.5f * (xr + cr);
            float ei = 0.5f * (xi + ci);
            float dr = 0.5f * (xr - cr);
            float di = 0.5f * (xi - ci);
            // odd part = d * exp(+2*pi*i*k/n)
            float wr = cos[k];
            float wi = sin[k];
            float or = dr * wr + di * wi;
            float oi = di * wr - dr * wi;
            zr[k] = er - oi;
            zi[k] = ei + or;
        }
        fft.inverse(zr, zi);
        for (int i = 0; i < half; i++) {
            x[2 * i] = zr[i];
            x[2 * i + 1] = zi[i];
        }
    }
}
