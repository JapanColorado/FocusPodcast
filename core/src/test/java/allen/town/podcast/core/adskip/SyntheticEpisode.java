package allen.town.podcast.core.adskip;

import java.util.List;
import java.util.Random;

/**
 * Builds a fake episode and renders it straight into an {@link AudioFeatureExtractor}, so that
 * {@link AdDetectorTest} can work on a realistic feature profile without shipping an audio fixture
 * and without ever materialising twenty minutes of PCM (that would be 76 MB in a unit test).
 *
 * <p>Two voices:
 * <ul>
 *   <li><b>host speech</b> -- band-limited noise (250 Hz to 3 kHz, two poles each side) with a 4 Hz
 *       amplitude modulation standing in for syllables, plus randomly placed breathing pauses. Left
 *       at a moderate level with plenty of headroom, so its crest factor is high.</li>
 *   <li><b>ad</b> -- a different band (450 Hz to 4.5 kHz, i.e. a different voice and mic chain),
 *       modulated faster and more shallowly, driven hard into a clipper so it is compressed and its
 *       crest factor collapses, then mixed with a sustained six-tone music bed weighted towards the
 *       bass. The result is louder, more tonal, bassier and flatter in dynamics: exactly the four
 *       things the detector looks for.</li>
 * </ul>
 *
 * <p>Nothing here is tuned to the detector's thresholds; the two signals are built from the
 * description of what podcast ads sound like, and the detector has to find them from that.
 */
final class SyntheticEpisode {

    static final int RATE = AudioFeatureExtractor.SAMPLE_RATE;

    /** Target RMS of the host voice after its envelope, roughly -22 dBFS. */
    private static final double SPEECH_RMS = 0.08;

    /** Target RMS of the ad voice before it is driven into the clipper. */
    private static final double AD_VOICE_RMS = 0.30;

    /** Clipping ceiling for the ad voice. */
    private static final double AD_CLIP = 0.45;

    /** How hard the ad voice is driven into that ceiling. */
    private static final double AD_DRIVE = 2.6;

    /** Target RMS of the music bed. */
    private static final double MUSIC_RMS = 0.20;

    /** An E-minor-ish pad: the bed is deliberately bass-heavy, as podcast ad beds are. */
    private static final double[] TONE_HZ = {82.41, 110.0, 164.81, 220.0, 329.63, 659.26};
    private static final double[] TONE_AMP = {1.0, 0.85, 0.7, 0.55, 0.4, 0.25};

    /** A whisper of broadband room tone under everything, so no band is ever truly empty. */
    private static final double ROOM_NOISE = 0.002;

    private final Random random;
    private final Pole[] speechChain = {new Pole(250), new Pole(250), new Pole(3000),
            new Pole(3000)};
    private final Pole[] adChain = {new Pole(450), new Pole(450), new Pole(4500), new Pole(4500)};
    private final Pole gateSmoother = new Pole(30);
    private final double speechGain;
    private final double adGain;
    private final double musicGain;

    private boolean talking = true;
    private int gateCountdown;

    private SyntheticEpisode(long seed) {
        this.random = new Random(seed);
        // Self-calibrate: the filter chains have an unknown insertion loss, so measure it once on
        // a throwaway chain rather than hard-coding a magic gain.
        this.speechGain = SPEECH_RMS / (measure(new Pole[]{new Pole(250), new Pole(250),
                new Pole(3000), new Pole(3000)}) * 0.70);
        this.adGain = AD_VOICE_RMS / measure(new Pole[]{new Pole(450), new Pole(450),
                new Pole(4500), new Pole(4500)});
        double toneRms = 0;
        for (double amp : TONE_AMP) {
            toneRms += amp * amp / 2.0;
        }
        this.musicGain = MUSIC_RMS / Math.sqrt(toneRms);
    }

    /**
     * Renders an episode and returns its feature profile.
     *
     * @param durationMs total length.
     * @param adRanges   half-open {start, end} millisecond ranges that should sound like ads.
     * @param seed       makes the whole thing reproducible.
     */
    static List<FeatureFrame> render(long durationMs, long[][] adRanges, long seed) {
        SyntheticEpisode generator = new SyntheticEpisode(seed);
        AudioFeatureExtractor extractor = new AudioFeatureExtractor();
        long totalSamples = durationMs * RATE / 1000;
        float[] chunk = new float[RATE];
        long produced = 0;
        while (produced < totalSamples) {
            int count = (int) Math.min(chunk.length, totalSamples - produced);
            for (int i = 0; i < count; i++) {
                long n = produced + i;
                chunk[i] = generator.sample(n, inRange(adRanges, n * 1000L / RATE));
            }
            extractor.accept(chunk, 0, count);
            produced += count;
        }
        return extractor.finish();
    }

    static boolean inRange(long[][] ranges, long ms) {
        for (long[] range : ranges) {
            if (ms >= range[0] && ms < range[1]) {
                return true;
            }
        }
        return false;
    }

    private float sample(long n, boolean ad) {
        double t = n / (double) RATE;
        double room = random.nextGaussian() * ROOM_NOISE;
        return (float) (ad ? adSample(t, room) : speechSample(t, room));
    }

    private double speechSample(double t, double room) {
        double voice = filter(speechChain, random.nextGaussian()) * speechGain;
        // Syllable-rate modulation plus breathing pauses; both leave the peaks far above the RMS.
        double syllables = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 4.0 * t));
        double envelope = gateSmoother.lp((float) gate()) * syllables;
        return voice * envelope + room;
    }

    private double adSample(double t, double room) {
        double voice = filter(adChain, random.nextGaussian()) * adGain;
        double envelope = 0.78 + 0.22 * Math.sin(2 * Math.PI * 5.5 * t);
        double driven = voice * envelope * AD_DRIVE;
        double clipped = Math.max(-AD_CLIP, Math.min(AD_CLIP, driven));

        double bed = 0;
        for (int i = 0; i < TONE_HZ.length; i++) {
            bed += TONE_AMP[i] * Math.sin(2 * Math.PI * TONE_HZ[i] * t);
        }
        bed *= musicGain * (0.9 + 0.1 * Math.sin(2 * Math.PI * 0.22 * t));

        return Math.max(-0.95, Math.min(0.95, clipped + bed + room));
    }

    private double gate() {
        if (gateCountdown <= 0) {
            talking = !talking;
            gateCountdown = talking
                    ? (int) (RATE * (3.0 + random.nextDouble() * 4.0))
                    : (int) (RATE * (0.4 + random.nextDouble() * 0.6));
        }
        gateCountdown--;
        return talking ? 1.0 : 0.0;
    }

    private static double filter(Pole[] chain, double x) {
        double y = x;
        y = chain[0].hp((float) y);
        y = chain[1].hp((float) y);
        y = chain[2].lp((float) y);
        y = chain[3].lp((float) y);
        return y;
    }

    /** RMS of two seconds of unit-variance noise through a copy of the chain. */
    private static double measure(Pole[] chain) {
        Random calibration = new Random(12345L);
        double sum = 0;
        int count = RATE * 2;
        for (int i = 0; i < count; i++) {
            double y = filter(chain, calibration.nextGaussian());
            sum += y * y;
        }
        return Math.sqrt(sum / count);
    }

    /** A one-pole filter usable as either a low-pass or, by subtraction, a high-pass. */
    private static final class Pole {
        private final float a;
        private float y;

        Pole(double cutoffHz) {
            this.a = (float) (1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / RATE));
        }

        float lp(float x) {
            y += a * (x - y);
            return y;
        }

        float hp(float x) {
            return x - lp(x);
        }
    }
}
