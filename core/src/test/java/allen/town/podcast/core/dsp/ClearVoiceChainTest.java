package allen.town.podcast.core.dsp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import allen.town.podcast.model.playback.ClearVoiceLevel;

/**
 * The Clear voice chain end to end on synthetic signals, for mono and stereo at 44.1 and 48 kHz.
 *
 * <p>Note on the test tone: a noise reducer without a noise profile cannot tell a tone that never
 * stops from a hum, and should not, so "speech" here is a 1 kHz tone gated on and off like
 * phrases (0.4 s on, 0.4 s off). The noise floor tracker then sees the background in the gaps,
 * exactly as it does between words in a real recording.
 */
public class ClearVoiceChainTest {
    private static final int[] RATES = {44100, 48000};
    private static final int[] CHANNELS = {1, 2};
    private static final double ON_SECONDS = 0.4;
    private static final double OFF_SECONDS = 0.4;
    private static final double EDGE_SECONDS = 0.06;

    // ------------------------------------------------------------ quality

    @Test
    public void gatedToneInNoiseComesOutCleanerAndAtTheSameLevel() {
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                for (int level : new int[]{ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
                    double seconds = 8;
                    // a tone at the leveler's target level, so its level should survive as is
                    double[] tone = gatedTone(rate, seconds, 1000, 0.14);
                    double[] noisy = add(tone, noise(rate, seconds, 0.028, 11));
                    short[] in = toPcm(noisy, channels);
                    short[] out = run(chain(rate, channels, level), in, 1024);
                    assertEquals(in.length, out.length);

                    double[] x = channel(in, channels, 0);
                    double[] y = channel(out, channels, 0);
                    double from = 3.2; // past the noise tracker's warm-up
                    double snrIn = gatedSnrDb(x, rate, from);
                    double snrOut = gatedSnrDb(y, rate, from);
                    double toneIn = gatedToneDb(x, rate, from);
                    double toneOut = gatedToneDb(y, rate, from);
                    String what = describe(rate, channels, level) + " snr " + fmt(snrIn) + " -> "
                            + fmt(snrOut) + " dB, tone " + fmt(toneIn) + " -> " + fmt(toneOut) + " dB";
                    System.out.println(what);
                    double wantedGain = level == ClearVoiceLevel.STRONG ? 10 : 5;
                    assertTrue(what, snrOut - snrIn >= wantedGain);
                    assertTrue(what, Math.abs(toneOut - toneIn) <= 4);
                }
            }
        }
    }

    @Test
    public void steadyNoiseIsTurnedDown() {
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                for (int level : new int[]{ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
                    double seconds = 6;
                    short[] in = toPcm(noise(rate, seconds, 0.03, 5), channels);
                    short[] out = run(chain(rate, channels, level), in, 4096);
                    int from = 3 * rate;
                    double reduction = rmsDb(channel(in, channels, 0), from)
                            - rmsDb(channel(out, channels, 0), from);
                    String what = describe(rate, channels, level) + " noise reduced by " + fmt(reduction) + " dB";
                    System.out.println(what);
                    double wanted = level == ClearVoiceLevel.STRONG ? 12 : 6;
                    assertTrue(what, reduction >= wanted);
                }
            }
        }
    }

    @Test
    public void silenceStaysExactlySilent() {
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                for (int level : new int[]{ClearVoiceLevel.OFF, ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
                    short[] in = new short[3 * rate * channels];
                    short[] out = run(chain(rate, channels, level), in, 1000);
                    assertArrayEquals(describe(rate, channels, level), in, out);
                }
            }
        }
    }

    @Test
    public void outputNeverReachesFullScale() {
        int ceiling = (int) Math.ceil(Leveler.CEILING * 32768) + 1;
        for (int rate : RATES) {
            for (int channels : CHANNELS) {
                for (int level : new int[]{ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
                    double seconds = 6;
                    double[] signal = new double[(int) (seconds * rate)];
                    Random random = new Random(1);
                    for (int i = 0; i < signal.length; i++) {
                        double t = (double) i / rate;
                        if (t < 2) {
                            // quiet speech, so the AGC winds its boost all the way up ...
                            signal[i] = 0.01 * Math.sin(2 * Math.PI * 440 * t) * ((int) (t / 0.3) % 2);
                        } else if (t < 4) {
                            // ... then a full-scale square wave hits it
                            signal[i] = Math.sin(2 * Math.PI * 150 * t) >= 0 ? 1 : -1;
                        } else {
                            signal[i] = Math.max(-1, Math.min(1, random.nextGaussian() * 0.8));
                        }
                    }
                    short[] out = run(chain(rate, channels, level), toPcm(signal, channels), 777);
                    int peak = 0;
                    for (short s : out) {
                        peak = Math.max(peak, Math.abs(s));
                    }
                    assertTrue(describe(rate, channels, level) + " peak " + peak, peak <= ceiling);
                }
            }
        }
    }

    @Test
    public void levelingBringsAQuietAndALoudSpeakerCloser() {
        int rate = 48000;
        double segment = 8;
        double[] loud = speechLike(rate, segment, 0.25, 21);
        double[] quiet = speechLike(rate, segment, 0.025, 22);
        double[] signal = concat(loud, quiet, loud, quiet);
        signal = add(signal, noise(rate, segment * 4, 0.0005, 23));
        for (int level : new int[]{ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
            short[] in = toPcm(signal, 2);
            short[] out = run(chain(rate, 2, level), in, 2048);
            double[] x = channel(in, 2, 0);
            double[] y = channel(out, 2, 0);
            int seg = (int) (segment * rate);
            // the second half of the second loud and quiet segments
            double inGap = rmsDb(x, 2 * seg + seg / 2, 3 * seg) - rmsDb(x, 3 * seg + seg / 2, 4 * seg);
            double outGap = rmsDb(y, 2 * seg + seg / 2, 3 * seg) - rmsDb(y, 3 * seg + seg / 2, 4 * seg);
            String what = "level " + level + ": loud-quiet gap " + fmt(inGap) + " -> " + fmt(outGap) + " dB";
            System.out.println(what);
            double wanted = level == ClearVoiceLevel.STRONG ? 12 : 6;
            assertTrue(what, inGap - outGap >= wanted);
        }
    }

    // --------------------------------------------------------- mechanics

    @Test
    public void offIsBitExactPassThrough() {
        Random random = new Random(9);
        for (int channels : CHANNELS) {
            short[] in = new short[48000 * channels];
            for (int i = 0; i < in.length; i++) {
                in[i] = (short) random.nextInt();
            }
            ClearVoiceChain chain = chain(48000, channels, ClearVoiceLevel.OFF);
            assertTrue(chain.isBypassed());
            assertArrayEquals(in, run(chain, in, 1234));
        }
    }

    @Test
    public void outputIsAlignedWithTheInput() {
        int rate = 48000;
        double[] signal = new double[rate * 2];
        int onset = rate + 123;
        for (int i = onset; i < signal.length; i++) {
            signal[i] = 0.3 * Math.sin(2 * Math.PI * 700.0 * (i - onset) / rate);
        }
        for (int level : new int[]{ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG}) {
            short[] out = run(chain(rate, 1, level), toPcm(signal, 1), 512);
            int first = -1;
            for (int i = 0; i < out.length; i++) {
                if (Math.abs(out[i]) > 300) {
                    first = i;
                    break;
                }
            }
            assertTrue("onset at " + first + " instead of " + onset,
                    first >= onset - 8 && first <= onset + 32);
        }
    }

    @Test
    public void switchingLevelsMidStreamLosesAndRepeatsNothing() {
        int rate = 44100;
        int channels = 2;
        short[] in = toPcm(add(gatedTone(rate, 6, 500, 0.2), noise(rate, 6, 0.01, 3)), channels);
        ClearVoiceChain chain = new ClearVoiceChain(rate, channels);
        int[] schedule = {ClearVoiceLevel.OFF, ClearVoiceLevel.LIGHT, ClearVoiceLevel.STRONG,
                ClearVoiceLevel.OFF, ClearVoiceLevel.OFF, ClearVoiceLevel.STRONG, ClearVoiceLevel.LIGHT};
        int buffer = 4410 * channels;
        short[] out = new short[in.length];
        short[] tmp = new short[chain.maxOutputSamples(buffer)];
        int written = 0;
        int step = 0;
        for (int pos = 0; pos < in.length; pos += buffer, step++) {
            chain.setLevel(schedule[(step / 5) % schedule.length]);
            int length = Math.min(buffer, in.length - pos);
            int n = chain.process(Arrays.copyOfRange(in, pos, pos + length), length, tmp);
            System.arraycopy(tmp, 0, out, written, n);
            written += n;
        }
        written += chain.drain(Arrays.copyOfRange(out, written, out.length));
        assertEquals(in.length, written);
    }

    @Test
    public void flushDropsTheAudioInFlight() {
        int rate = 48000;
        ClearVoiceChain chain = chain(rate, 2, ClearVoiceLevel.STRONG);
        short[] noisy = toPcm(noise(rate, 2, 0.2, 4), 2);
        short[] tmp = new short[chain.maxOutputSamples(noisy.length)];
        chain.process(noisy, noisy.length, tmp);
        chain.flush();
        short[] silence = new short[rate * 2];
        assertArrayEquals(silence, run(chain, silence, 1024));
    }

    @Test
    public void resetBehavesLikeANewChain() {
        int rate = 44100;
        short[] first = toPcm(add(gatedTone(rate, 3, 1000, 0.3), noise(rate, 3, 0.05, 6)), 1);
        short[] second = toPcm(add(gatedTone(rate, 3, 600, 0.05), noise(rate, 3, 0.01, 7)), 1);
        ClearVoiceChain used = chain(rate, 1, ClearVoiceLevel.STRONG);
        run(used, first, 1024);
        used.reset();
        assertArrayEquals(run(chain(rate, 1, ClearVoiceLevel.STRONG), second, 1024), run(used, second, 1024));
    }

    @Test
    public void strongIsCheapEnoughForRealTimeAtDoubleSpeed() {
        int rate = 48000;
        int channels = 2;
        double seconds = 60;
        short[] in = toPcm(add(speechLike(rate, seconds, 0.1, 31), noise(rate, seconds, 0.01, 32)), channels);
        // warm up the JIT on a short run first
        run(chain(rate, channels, ClearVoiceLevel.STRONG), Arrays.copyOf(in, rate * channels * 5), 4096);
        long start = System.nanoTime();
        run(chain(rate, channels, ClearVoiceLevel.STRONG), in, 4096);
        double elapsed = (System.nanoTime() - start) / 1e9;
        System.out.println("60 s of 48 kHz stereo at Strong took " + fmt(elapsed) + " s");
        assertTrue("took " + elapsed + " s", elapsed < 6);
    }

    // ------------------------------------------------------------ helpers

    private static ClearVoiceChain chain(int rate, int channels, int level) {
        ClearVoiceChain chain = new ClearVoiceChain(rate, channels);
        chain.setLevel(level);
        return chain;
    }

    /** Feeds {@code in} in buffers of {@code bufferFrames}, then drains; returns everything out. */
    static short[] run(ClearVoiceChain chain, short[] in, int bufferFrames) {
        int channels = chain.getChannels();
        int buffer = bufferFrames * channels;
        short[] out = new short[chain.maxOutputSamples(in.length)];
        short[] chunk = new short[buffer];
        short[] tmp = new short[chain.maxOutputSamples(buffer)];
        int written = 0;
        for (int pos = 0; pos < in.length; pos += buffer) {
            int length = Math.min(buffer, in.length - pos);
            System.arraycopy(in, pos, chunk, 0, length);
            int n = chain.process(chunk, length, tmp);
            System.arraycopy(tmp, 0, out, written, n);
            written += n;
        }
        int n = chain.drain(tmp);
        System.arraycopy(tmp, 0, out, written, n);
        written += n;
        return Arrays.copyOf(out, written);
    }

    private static double[] gatedTone(int rate, double seconds, double frequency, double amplitude) {
        double[] x = new double[(int) (seconds * rate)];
        double period = ON_SECONDS + OFF_SECONDS;
        double ramp = 0.005;
        for (int i = 0; i < x.length; i++) {
            double t = (double) i / rate;
            double phase = t % period;
            double gate = 0;
            if (phase < ON_SECONDS) {
                gate = Math.min(1, Math.min(phase / ramp, (ON_SECONDS - phase) / ramp));
            }
            x[i] = amplitude * gate * Math.sin(2 * Math.PI * frequency * t);
        }
        return x;
    }

    /** Noise shaped and gated roughly like speech: phrases with pauses and a syllable rhythm. */
    private static double[] speechLike(int rate, double seconds, double rms, long seed) {
        Random random = new Random(seed);
        double[] x = new double[(int) (seconds * rate)];
        double low = 0;
        double sumSquares = 0;
        int voiced = 0;
        for (int i = 0; i < x.length; i++) {
            double t = (double) i / rate;
            low += 0.2 * (random.nextGaussian() - low);
            boolean phrase = t % 2.0 < 1.6;
            double syllable = 0.3 + 0.7 * Math.abs(Math.sin(2 * Math.PI * 3 * t));
            x[i] = phrase ? low * syllable : 0;
            if (phrase) {
                sumSquares += x[i] * x[i];
                voiced++;
            }
        }
        double scale = rms / Math.sqrt(sumSquares / Math.max(1, voiced));
        for (int i = 0; i < x.length; i++) {
            x[i] *= scale;
        }
        return x;
    }

    private static double[] noise(int rate, double seconds, double sigma, long seed) {
        Random random = new Random(seed);
        double[] x = new double[(int) (seconds * rate)];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextGaussian() * sigma;
        }
        return x;
    }

    private static double[] add(double[] a, double[] b) {
        double[] sum = new double[Math.min(a.length, b.length)];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = a[i] + b[i];
        }
        return sum;
    }

    private static double[] concat(double[]... parts) {
        int length = 0;
        for (double[] part : parts) {
            length += part.length;
        }
        double[] all = new double[length];
        int pos = 0;
        for (double[] part : parts) {
            System.arraycopy(part, 0, all, pos, part.length);
            pos += part.length;
        }
        return all;
    }

    /** Interleaves {@code signal} into every channel (stereo gets a slightly different right). */
    private static short[] toPcm(double[] signal, int channels) {
        short[] pcm = new short[signal.length * channels];
        for (int i = 0; i < signal.length; i++) {
            for (int ch = 0; ch < channels; ch++) {
                double v = signal[i] * (ch == 0 ? 1.0 : 0.9);
                pcm[i * channels + ch] = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32768)));
            }
        }
        return pcm;
    }

    private static double[] channel(short[] pcm, int channels, int ch) {
        double[] x = new double[pcm.length / channels];
        for (int i = 0; i < x.length; i++) {
            x[i] = pcm[i * channels + ch] / 32768.0;
        }
        return x;
    }

    private static double rmsDb(double[] x, int from) {
        return rmsDb(x, from, x.length);
    }

    private static double rmsDb(double[] x, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += x[i] * x[i];
        }
        return 10 * Math.log10(sum / (to - from) + 1e-20);
    }

    /** SNR of the gated tone: power in the "on" cores against power in the "off" cores. */
    private static double gatedSnrDb(double[] x, int rate, double fromSeconds) {
        double[] on = new double[2];
        double[] off = new double[2];
        accumulateGated(x, rate, fromSeconds, on, off);
        double pOn = on[0] / on[1];
        double pOff = off[0] / off[1];
        return 10 * Math.log10(Math.max(pOn - pOff, 1e-20) / pOff);
    }

    private static void accumulateGated(double[] x, int rate, double fromSeconds, double[] on, double[] off) {
        double period = ON_SECONDS + OFF_SECONDS;
        for (int i = (int) (fromSeconds * rate); i < x.length; i++) {
            double phase = ((double) i / rate) % period;
            double[] bucket;
            if (phase > EDGE_SECONDS && phase < ON_SECONDS - EDGE_SECONDS) {
                bucket = on;
            } else if (phase > ON_SECONDS + EDGE_SECONDS && phase < period - EDGE_SECONDS) {
                bucket = off;
            } else {
                continue;
            }
            bucket[0] += x[i] * x[i];
            bucket[1]++;
        }
    }

    /** Level of the 1 kHz component in the "on" cores (Goertzel per core, averaged power). */
    private static double gatedToneDb(double[] x, int rate, double fromSeconds) {
        double period = ON_SECONDS + OFF_SECONDS;
        int coreLength = (int) ((ON_SECONDS - 2 * EDGE_SECONDS) * rate);
        double sum = 0;
        int cores = 0;
        for (double start = Math.ceil(fromSeconds / period) * period + EDGE_SECONDS;
             (int) (start * rate) + coreLength <= x.length; start += period) {
            int from = (int) Math.round(start * rate);
            double coeff = 2 * Math.cos(2 * Math.PI * 1000.0 / rate);
            double s1 = 0;
            double s2 = 0;
            for (int i = from; i < from + coreLength; i++) {
                double s0 = x[i] + coeff * s1 - s2;
                s2 = s1;
                s1 = s0;
            }
            double power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
            double amplitude = 2 * Math.sqrt(power) / coreLength;
            sum += amplitude * amplitude;
            cores++;
        }
        return 10 * Math.log10(sum / cores);
    }

    private static String describe(int rate, int channels, int level) {
        return rate + " Hz x" + channels + " level " + level;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
