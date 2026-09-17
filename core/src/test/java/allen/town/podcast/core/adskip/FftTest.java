package allen.town.podcast.core.adskip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Sanity checks for the hand-written radix-2 FFT. A pure sine sitting exactly on bin k must put
 * essentially all of its energy in bin k; if the twiddle table or the bit-reversal permutation is
 * wrong, the peak lands somewhere else.
 */
public class FftTest {

    private static final int SIZE = 1024;

    private static float[] sineAtBin(int bin, int size) {
        float[] x = new float[size];
        for (int i = 0; i < size; i++) {
            x[i] = (float) Math.sin(2.0 * Math.PI * bin * i / size);
        }
        return x;
    }

    private static int argMax(float[] spectrum) {
        int best = 0;
        for (int k = 1; k < spectrum.length; k++) {
            if (spectrum[k] > spectrum[best]) {
                best = k;
            }
        }
        return best;
    }

    @Test
    public void sineOnABinPeaksAtThatBin() {
        Fft fft = new Fft(SIZE);
        for (int bin : new int[]{1, 2, 17, 64, 200, 511}) {
            float[] spectrum = fft.powerSpectrum(sineAtBin(bin, SIZE));
            assertEquals("peak for bin " + bin, bin, argMax(spectrum));
        }
    }

    @Test
    public void energyIsConcentratedInTheSingleBin() {
        Fft fft = new Fft(SIZE);
        float[] spectrum = fft.powerSpectrum(sineAtBin(64, SIZE));
        double total = 0;
        for (int k = 1; k < spectrum.length; k++) {
            total += spectrum[k];
        }
        assertTrue("expected almost all energy in bin 64, got " + (spectrum[64] / total),
                spectrum[64] / total > 0.99);
    }

    @Test
    public void dcSignalLandsInBinZero() {
        Fft fft = new Fft(SIZE);
        float[] x = new float[SIZE];
        java.util.Arrays.fill(x, 0.5f);
        float[] spectrum = fft.powerSpectrum(x);
        assertEquals(0, argMax(spectrum));
        assertEquals(Math.pow(0.5 * SIZE, 2), spectrum[0], 1.0);
    }

    @Test
    public void spectrumLengthIsHalfSizePlusOne() {
        Fft fft = new Fft(256);
        assertEquals(129, fft.powerSpectrum(new float[256]).length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPowerOfTwo() {
        new Fft(1000);
    }
}
