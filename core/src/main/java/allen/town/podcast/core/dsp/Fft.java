package allen.town.podcast.core.dsp;

/**
 * A minimal in-place radix-2 Cooley-Tukey FFT, sized once and reused.
 *
 * <p>This exists because the ad detector needs a power spectrum a few hundred thousand times per
 * episode, and the Clear voice enhancer needs a forward and an inverse transform every few
 * milliseconds of playback, and the app is not allowed to gain a new dependency for it (F-Droid
 * build, no native libraries, no DSP jar). It owns nothing but its twiddle tables, its bit-reversal permutation and
 * a pair of scratch buffers, so a single instance can be reused for every sub-window of an episode
 * without allocating.
 *
 * <p>It is deliberately not thread safe: the scratch buffers are instance state. One instance per
 * analysis thread.
 */
public final class Fft {

    private final int size;
    private final int[] reverse;
    /** Twiddles of the stages of length 8 and up, laid out stage after stage (len/2 each). */
    private final float[] stageCos;
    private final float[] stageSin;
    private final float[] scratchRe;
    private final float[] scratchIm;

    /**
     * @param size transform length; must be a power of two and at least 2.
     */
    public Fft(int size) {
        if (size < 2 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of two >= 2, was " + size);
        }
        this.size = size;
        this.reverse = new int[size];
        float[] twiddleCos = new float[size / 2];
        float[] twiddleSin = new float[size / 2];
        this.scratchRe = new float[size];
        this.scratchIm = new float[size];

        int bits = Integer.numberOfTrailingZeros(size);
        for (int i = 0; i < size; i++) {
            reverse[i] = Integer.reverse(i) >>> (32 - bits);
        }
        // Twiddles for the whole transform: exp(-2*pi*i*j/size) for j < size/2. A stage of length
        // "len" needs every (size/len)-th of them; they are copied into one contiguous run per stage
        // so the inner loop reads them sequentially.
        for (int j = 0; j < size / 2; j++) {
            double angle = -2.0 * Math.PI * j / size;
            twiddleCos[j] = (float) Math.cos(angle);
            twiddleSin[j] = (float) Math.sin(angle);
        }
        int stageEntries = Math.max(0, size - 4);
        this.stageCos = new float[stageEntries];
        this.stageSin = new float[stageEntries];
        int offset = 0;
        for (int len = 8; len <= size; len <<= 1) {
            int stride = size / len;
            for (int k = 0; k < len / 2; k++) {
                stageCos[offset + k] = twiddleCos[k * stride];
                stageSin[offset + k] = twiddleSin[k * stride];
            }
            offset += len / 2;
        }
    }

    public int size() {
        return size;
    }

    /**
     * In-place forward complex transform. Both arrays must have exactly {@link #size()} entries.
     */
    public void forward(float[] re, float[] im) {
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
        // Stage of length 2: the only twiddle is 1.
        for (int a = 0; a + 1 < size; a += 2) {
            float br = re[a + 1];
            float bi = im[a + 1];
            re[a + 1] = re[a] - br;
            im[a + 1] = im[a] - bi;
            re[a] += br;
            im[a] += bi;
        }
        // Stage of length 4: twiddles 1 and -i, so no multiplications.
        if (size >= 4) {
            for (int a = 0; a < size; a += 4) {
                float br = re[a + 2];
                float bi = im[a + 2];
                re[a + 2] = re[a] - br;
                im[a + 2] = im[a] - bi;
                re[a] += br;
                im[a] += bi;
                // (re + i im) * -i = im - i re
                float cr = im[a + 3];
                float ci = -re[a + 3];
                re[a + 3] = re[a + 1] - cr;
                im[a + 3] = im[a + 1] - ci;
                re[a + 1] += cr;
                im[a + 1] += ci;
            }
        }
        // Remaining stages read their twiddles from a contiguous per-stage table, two stages per
        // pass over the data where possible (same arithmetic, half the loads and stores).
        int stageOffset = 0;
        int len = 8;
        while (len <= size) {
            int half = len >> 1;
            if (len * 2 <= size) {
                int nextOffset = stageOffset + half;
                for (int base = 0; base < size; base += 2 * len) {
                    for (int k = 0; k < half; k++) {
                        int i0 = base + k;
                        int i1 = i0 + half;
                        int i2 = i0 + len;
                        int i3 = i2 + half;
                        // stage len: (i0, i1) and (i2, i3), twiddle W_len^k
                        float wr = stageCos[stageOffset + k];
                        float wi = stageSin[stageOffset + k];
                        float r1 = re[i1];
                        float m1 = im[i1];
                        float vr = r1 * wr - m1 * wi;
                        float vi = r1 * wi + m1 * wr;
                        float r0 = re[i0];
                        float m0 = im[i0];
                        float u1r = r0 - vr;
                        float u1i = m0 - vi;
                        float u0r = r0 + vr;
                        float u0i = m0 + vi;
                        float r3 = re[i3];
                        float m3 = im[i3];
                        vr = r3 * wr - m3 * wi;
                        vi = r3 * wi + m3 * wr;
                        float r2 = re[i2];
                        float m2 = im[i2];
                        float u3r = r2 - vr;
                        float u3i = m2 - vi;
                        float u2r = r2 + vr;
                        float u2i = m2 + vi;
                        // stage 2*len: (i0, i2) with W_2len^k, (i1, i3) with W_2len^(k+half)
                        wr = stageCos[nextOffset + k];
                        wi = stageSin[nextOffset + k];
                        vr = u2r * wr - u2i * wi;
                        vi = u2r * wi + u2i * wr;
                        re[i2] = u0r - vr;
                        im[i2] = u0i - vi;
                        re[i0] = u0r + vr;
                        im[i0] = u0i + vi;
                        wr = stageCos[nextOffset + k + half];
                        wi = stageSin[nextOffset + k + half];
                        vr = u3r * wr - u3i * wi;
                        vi = u3r * wi + u3i * wr;
                        re[i3] = u1r - vr;
                        im[i3] = u1i - vi;
                        re[i1] = u1r + vr;
                        im[i1] = u1i + vi;
                    }
                }
                stageOffset = nextOffset + len;
                len <<= 2;
            } else {
                for (int base = 0; base < size; base += len) {
                    for (int k = 0; k < half; k++) {
                        float wr = stageCos[stageOffset + k];
                        float wi = stageSin[stageOffset + k];
                        int a = base + k;
                        int b = a + half;
                        float rb = re[b];
                        float ib = im[b];
                        float vr = rb * wr - ib * wi;
                        float vi = rb * wi + ib * wr;
                        float ra = re[a];
                        float ia = im[a];
                        re[b] = ra - vr;
                        im[b] = ia - vi;
                        re[a] = ra + vr;
                        im[a] = ia + vi;
                    }
                }
                stageOffset += half;
                len <<= 1;
            }
        }
    }

    /**
     * In-place inverse complex transform, scaled by 1/size so that {@code inverse(forward(x))}
     * returns {@code x}. Uses the identity IFFT(X) = swap(FFT(swap(X))) / size, where swap
     * exchanges the real and imaginary parts, so it costs one forward transform and a scale.
     */
    public void inverse(float[] re, float[] im) {
        if (re.length != size || im.length != size) {
            throw new IllegalArgumentException("arrays must be of length " + size);
        }
        forward(im, re);
        float scale = 1f / size;
        for (int i = 0; i < size; i++) {
            re[i] *= scale;
            im[i] *= scale;
        }
    }

    /**
     * Power spectrum |X[k]|^2 of a real signal, for k = 0 .. size/2 inclusive.
     *
     * @param samples source buffer, read from {@code offset} for {@link #size()} values.
     * @param out     destination of length {@code size/2 + 1}.
     */
    public void powerSpectrum(float[] samples, int offset, float[] out) {
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
    public float[] powerSpectrum(float[] samples) {
        float[] out = new float[size / 2 + 1];
        powerSpectrum(samples, 0, out);
        return out;
    }

    /** Periodic Hann window of the given length, the shape used for every analysis sub-window. */
    public static float[] hann(int length) {
        float[] w = new float[length];
        for (int i = 0; i < length; i++) {
            w[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / length));
        }
        return w;
    }
}
