package allen.town.podcast.core.dsp;

/**
 * A split-band de-esser on interleaved float samples: a Linkwitz-Riley crossover at
 * {@link #CROSSOVER_HZ} splits the signal, and the upper band is turned down while it is louder
 * than the speech body (300 Hz to 4 kHz) by more than {@code thresholdDb}, by exactly the excess,
 * capped at {@code maxCutDb}. Sibilants are the only speech sounds whose energy sits mostly above
 * 4.5 kHz, so vowels and the air of a voice pass untouched and only the "s", "sh" and "t" bursts
 * that jump out of the mix are tamed. Because the two crossover halves sum back to an allpass,
 * the output equals the input (up to a phase shift) whenever nothing is being cut.
 *
 * <p>The cut takes effect within one {@link #BLOCK} and recovers over {@link #RELEASE_SECONDS},
 * with the gain ramped across each block so it never steps. The detector is shared by all
 * channels, so the stereo image does not wander. No latency. Not thread safe.
 */
final class DeEsser {
    static final double CROSSOVER_HZ = 4500;
    private static final double BODY_LOW_HZ = 300;
    private static final double BODY_HIGH_HZ = 4000;
    private static final int BLOCK = 32;
    private static final double DETECTOR_SECONDS = 0.005;
    private static final double RELEASE_SECONDS = 0.06;
    /** Below this level (per frame, both bands) there is nothing worth de-essing. */
    private static final float QUIET_POWER = 1e-8f; // -80 dBFS
    private static final double BUTTERWORTH_Q = Math.sqrt(0.5);

    private final int channels;
    private final Biquad low1;
    private final Biquad low2;
    private final Biquad high1;
    private final Biquad high2;
    private final Biquad bodyHigh;
    private final Biquad bodyLow;
    private final float detectorCoefficient;
    private final float releasePerBlock;

    private float[] low = new float[0];
    private float[] body = new float[0];
    private float sibilantPower;
    private float bodyPower;
    private float cutDb;
    private float gain = 1f;
    private float thresholdDb;
    private float maxCutDb;

    DeEsser(int sampleRate, int channels) {
        this.channels = channels;
        low1 = new Biquad(channels);
        low2 = new Biquad(channels);
        high1 = new Biquad(channels);
        high2 = new Biquad(channels);
        low1.setLowPass(sampleRate, CROSSOVER_HZ, BUTTERWORTH_Q);
        low2.setLowPass(sampleRate, CROSSOVER_HZ, BUTTERWORTH_Q);
        high1.setHighPass(sampleRate, CROSSOVER_HZ, BUTTERWORTH_Q);
        high2.setHighPass(sampleRate, CROSSOVER_HZ, BUTTERWORTH_Q);
        bodyHigh = new Biquad(1);
        bodyLow = new Biquad(1);
        bodyHigh.setHighPass(sampleRate, BODY_LOW_HZ, BUTTERWORTH_Q);
        bodyLow.setLowPass(sampleRate, BODY_HIGH_HZ, BUTTERWORTH_Q);
        detectorCoefficient = (float) (1 - Math.exp(-BLOCK / (DETECTOR_SECONDS * sampleRate)));
        releasePerBlock = (float) Math.exp(-BLOCK / (RELEASE_SECONDS * sampleRate));
    }

    /**
     * @param thresholdDb sibilant band level, relative to the speech body, above which it is cut
     * @param maxCutDb    the deepest cut; 0 switches the de-esser off
     */
    void setParameters(double thresholdDb, double maxCutDb) {
        this.thresholdDb = (float) thresholdDb;
        this.maxCutDb = (float) Math.max(0, maxCutDb);
    }

    boolean isEnabled() {
        return maxCutDb > 0;
    }

    /** Processes {@code frames} interleaved frames of {@code buffer} in place. */
    void process(float[] buffer, int frames) {
        if (!isEnabled()) {
            return;
        }
        int samples = frames * channels;
        if (low.length < samples) {
            low = new float[samples];
            body = new float[frames];
        }
        for (int f = 0; f < frames; f++) {
            float sum = 0f;
            for (int ch = 0; ch < channels; ch++) {
                sum += buffer[f * channels + ch];
            }
            body[f] = sum / channels;
        }
        bodyHigh.process(body, frames);
        bodyLow.process(body, frames);
        System.arraycopy(buffer, 0, low, 0, samples);
        low1.process(low, frames);
        low2.process(low, frames);
        // buffer becomes the high band; the output is rebuilt as low + gain * high below
        high1.process(buffer, frames);
        high2.process(buffer, frames);
        // fourth-order Linkwitz-Riley halves are in phase, so low + high is an allpass
        for (int start = 0; start < frames; start += BLOCK) {
            int length = Math.min(BLOCK, frames - start);
            float sibilant = 0f;
            float speech = 0f;
            for (int f = start; f < start + length; f++) {
                for (int ch = 0; ch < channels; ch++) {
                    float h = buffer[f * channels + ch];
                    sibilant += h * h;
                }
                speech += body[f] * body[f];
            }
            sibilantPower += detectorCoefficient * (sibilant / (length * channels) - sibilantPower);
            bodyPower += detectorCoefficient * (speech / length - bodyPower);
            float wanted = 0f;
            if (sibilantPower > QUIET_POWER) {
                double excess = 10 * Math.log10(sibilantPower / Math.max(bodyPower, 1e-12f)) - thresholdDb;
                wanted = (float) Math.min(maxCutDb, Math.max(0, excess));
            }
            cutDb = Math.max(wanted, cutDb * releasePerBlock);
            float target = (float) Math.pow(10, -cutDb / 20);
            float step = (target - gain) / length;
            for (int f = start; f < start + length; f++) {
                gain += step;
                for (int ch = 0; ch < channels; ch++) {
                    int i = f * channels + ch;
                    buffer[i] = low[i] + gain * buffer[i];
                }
            }
            gain = target;
        }
    }

    /** The deepest cut currently applied, in dB; for tests. */
    float currentCutDb() {
        return cutDb;
    }

    /** Drops the signal history (a seek); keeps nothing worth keeping, so same as {@link #reset}. */
    void reset() {
        low1.clear();
        low2.clear();
        high1.clear();
        high2.clear();
        bodyHigh.clear();
        bodyLow.clear();
        sibilantPower = 0f;
        bodyPower = 0f;
        cutDb = 0f;
        gain = 1f;
    }
}
