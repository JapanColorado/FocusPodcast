package dev.russell.yagi.core.dsp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * The noise floor tracker on its own: the minimum-statistics estimate, bias-corrected, has to land
 * on the true noise power, otherwise every strength setting is off by the same error.
 */
public class SpectralDenoiserTest {

    @Test
    public void noiseEstimateMatchesWhiteNoisePower() {
        for (int rate : new int[]{44100, 48000}) {
            SpectralDenoiser denoiser = new SpectralDenoiser(rate, 1);
            denoiser.setStrength(9f, 1f);
            Random random = new Random(3);
            float sigma = 0.02f;
            float[] buffer = new float[rate * 6];
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = (float) random.nextGaussian() * sigma;
            }
            denoiser.process(buffer, buffer.length);
            int n = denoiser.frameSize();
            double expected = sigma * sigma * n / 2.0;
            double sum = 0;
            int count = 0;
            for (int k = 4; k < n / 2 - 4; k++) {
                sum += denoiser.noiseEstimate(k);
                count++;
            }
            double ratio = sum / count / expected;
            System.out.println("noise estimate / true power at " + rate + " Hz: " + ratio);
            assertTrue("estimate/true = " + ratio, ratio > 0.7 && ratio < 1.4);
        }
    }

    @Test
    public void frameSizeIsAboutTwentyMilliseconds() {
        org.junit.Assert.assertEquals(1024, SpectralDenoiser.frameSizeFor(48000));
        org.junit.Assert.assertEquals(1024, SpectralDenoiser.frameSizeFor(44100));
        org.junit.Assert.assertEquals(512, SpectralDenoiser.frameSizeFor(22050));
        org.junit.Assert.assertEquals(256, SpectralDenoiser.frameSizeFor(16000));
        org.junit.Assert.assertEquals(128, SpectralDenoiser.frameSizeFor(8000));
    }
}
