package dev.russell.yagi.core.dsp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** {@link DeEsser} and {@link VoiceEnhancer} on synthetic signals, mono and stereo. */
public class DeEsserTest {
    private static final int[] RATES = {44100, 48000};
    private static final int[] CHANNELS = {1, 2};

    @Test
    public void voiceWithoutSibilantsPassesAtTheSameLevel() {
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                float[] x = interleave(tone(rate, 2, 800, 0.2), channels);
                float[] y = x.clone();
                DeEsser deEsser = deEsser(rate, channels);
                process(deEsser, y, channels, 512);
                double change = rmsDb(y, rate * channels / 2) - rmsDb(x, rate * channels / 2);
                assertEquals(describe(rate, channels), 0, change, 0.1);
                assertEquals(describe(rate, channels), 0, deEsser.currentCutDb(), 1e-6);
            }
        }
    }

    @Test
    public void loudSibilantsAreCutButNoMoreThanTheCap() {
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                // a 400 Hz "vowel" with high-passed noise 12 dB above it: way past the threshold
                double[] vowel = tone(rate, 2, 400, 0.05);
                double[] hiss = highNoise(rate, 2, 0.2, 3);
                float[] x = interleave(add(vowel, hiss), channels);
                float[] y = x.clone();
                process(deEsser(rate, channels), y, channels, 1000);
                int from = rate * channels / 2;
                double cut = rmsDb(highBand(x, rate, channels), from) - rmsDb(highBand(y, rate, channels), from);
                String what = describe(rate, channels) + " cut " + cut;
                assertTrue(what, cut > 6);
                assertTrue(what, cut < 8.5);
            }
        }
    }

    @Test
    public void silenceStaysSilent() {
        float[] x = new float[48000 * 2];
        process(deEsser(48000, 2), x, 2, 333);
        for (float v : x) {
            assertEquals(0f, v, 0f);
        }
    }

    @Test
    public void enhancerWarmsTheLowEndAndNeverClips() {
        for (int rate : RATES) {
            double[] low = tone(rate, 2, 100, 0.1);
            float[] y = interleave(low, 1);
            VoiceEnhancer enhancer = new VoiceEnhancer(rate, 1);
            enhancer.process(y, y.length);
            int lat = enhancer.latencyFrames();
            double gain = rmsDb(y, rate) - rmsDb(interleave(low, 1), rate - lat);
            assertTrue(describe(rate, 1) + " 100 Hz gain " + gain, gain > 3 && gain < 4.5);

            Random random = new Random(7);
            float[] loud = new float[rate * 2];
            for (int i = 0; i < loud.length; i++) {
                loud[i] = (float) Math.max(-1, Math.min(1, random.nextGaussian() * 0.6));
            }
            VoiceEnhancer second = new VoiceEnhancer(rate, 1);
            second.process(loud, loud.length);
            for (float v : loud) {
                assertTrue("peak " + v, Math.abs(v) <= Leveler.CEILING + 1e-3f);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private static DeEsser deEsser(int rate, int channels) {
        DeEsser deEsser = new DeEsser(rate, channels);
        deEsser.setParameters(0, 8);
        return deEsser;
    }

    private static void process(DeEsser deEsser, float[] buffer, int channels, int chunkFrames) {
        int frames = buffer.length / channels;
        float[] chunk = new float[chunkFrames * channels];
        for (int start = 0; start < frames; start += chunkFrames) {
            int length = Math.min(chunkFrames, frames - start);
            System.arraycopy(buffer, start * channels, chunk, 0, length * channels);
            deEsser.process(chunk, length);
            System.arraycopy(chunk, 0, buffer, start * channels, length * channels);
        }
    }

    private static float[] highBand(float[] x, int rate, int channels) {
        float[] y = x.clone();
        Biquad first = new Biquad(channels);
        Biquad second = new Biquad(channels);
        first.setHighPass(rate, 6000, Math.sqrt(0.5));
        second.setHighPass(rate, 6000, Math.sqrt(0.5));
        first.process(y, y.length / channels);
        second.process(y, y.length / channels);
        return y;
    }

    private static double[] tone(int rate, double seconds, double frequency, double amplitude) {
        double[] x = new double[(int) (seconds * rate)];
        for (int i = 0; i < x.length; i++) {
            x[i] = amplitude * Math.sin(2 * Math.PI * frequency * i / rate);
        }
        return x;
    }

    /** White noise high-passed well above the crossover. */
    private static double[] highNoise(int rate, double seconds, double sigma, long seed) {
        Random random = new Random(seed);
        float[] x = new float[(int) (seconds * rate)];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (random.nextGaussian() * sigma);
        }
        x = highBand(x, rate, 1);
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = x[i];
        }
        return out;
    }

    private static double[] add(double[] a, double[] b) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    private static float[] interleave(double[] mono, int channels) {
        float[] out = new float[mono.length * channels];
        for (int i = 0; i < mono.length; i++) {
            for (int ch = 0; ch < channels; ch++) {
                out[i * channels + ch] = (float) mono[i];
            }
        }
        return out;
    }

    private static double rmsDb(float[] x, int from) {
        double sum = 0;
        for (int i = from; i < x.length; i++) {
            sum += (double) x[i] * x[i];
        }
        return 10 * Math.log10(sum / (x.length - from) + 1e-30);
    }

    private static String describe(int rate, int channels) {
        return rate + " Hz x " + channels;
    }
}
