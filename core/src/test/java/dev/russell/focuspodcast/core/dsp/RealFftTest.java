package dev.russell.focuspodcast.core.dsp;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import org.junit.Test;

public class RealFftTest {

    @Test
    public void forwardMatchesTheComplexTransform() {
        for (int n : new int[]{4, 8, 64, 1024, 2048}) {
            Random random = new Random(n);
            float[] x = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = (float) random.nextGaussian();
            }
            float[] re = x.clone();
            float[] im = new float[n];
            new Fft(n).forward(re, im);

            float[] outRe = new float[n / 2 + 1];
            float[] outIm = new float[n / 2 + 1];
            new RealFft(n).forward(x, outRe, outIm);
            float tolerance = 1e-3f * (float) Math.sqrt(n);
            for (int k = 0; k <= n / 2; k++) {
                assertEquals("re n=" + n + " k=" + k, re[k], outRe[k], tolerance);
                assertEquals("im n=" + n + " k=" + k, im[k], outIm[k], tolerance);
            }
        }
    }

    @Test
    public void inverseUndoesForward() {
        for (int n : new int[]{4, 16, 1024}) {
            Random random = new Random(7 * n);
            float[] x = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = (float) random.nextGaussian();
            }
            RealFft fft = new RealFft(n);
            float[] re = new float[n / 2 + 1];
            float[] im = new float[n / 2 + 1];
            fft.forward(x, re, im);
            float[] back = new float[n];
            fft.inverse(re, im, back);
            for (int i = 0; i < n; i++) {
                assertEquals("n=" + n + " i=" + i, x[i], back[i], 1e-4f);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSizesThatAreNotAPowerOfTwo() {
        new RealFft(12);
    }
}
