package allen.town.podcast.core.dsp;

import java.util.Arrays;

/**
 * Evens out speech loudness and guarantees the result never clips. Three gains multiply:
 *
 * <ol>
 *   <li>A slow, speech-gated AGC that moves the average speech level toward a target, within a
 *       boost/cut range and at a limited slew rate, so a quiet guest is brought up and a loud host
 *       down over a second or two. It only listens while someone is talking (the short-term level
 *       is well above its own tracked floor), so pauses and background noise are never pumped
 *       up.</li>
 *   <li>A fast RMS compressor above the target, which catches what the AGC is too slow for
 *       (laughter, a shout, the first words after a speaker change).</li>
 *   <li>A look-ahead peak limiter with a ceiling of -1 dBFS, followed by a hard clamp at the
 *       ceiling as the last line of defence.</li>
 * </ol>
 *
 * <p>The AGC and compressor are computed once per 32-frame block (about 0.7 ms) and interpolated
 * across it, so the per-sample cost is a multiply plus the limiter bookkeeping. Channels are
 * linked: one gain for all of them, so the stereo image does not wander.
 *
 * <p>Streaming and sample-synchronous like {@link SpectralDenoiser}: exactly as many frames out as
 * in, delayed by {@link #latencyFrames()} (the limiter's look-ahead). Not thread safe.
 */
final class Leveler {
    static final float CEILING = 0.891f; // -1 dBFS
    private static final int BLOCK = 32;
    private static final double LOOKAHEAD_SECONDS = 0.003;
    private static final double ATTACK_SECONDS = 0.010;
    private static final double RELEASE_SECONDS = 0.150;
    private static final double LIMITER_RELEASE_SECONDS = 0.080;
    /** How fast the speech floor estimate may rise, in dB per second. */
    private static final double FLOOR_RISE_DB_PER_SECOND = 2.0;
    /** Speech is "on" this far above the floor. */
    private static final double SPEECH_ABOVE_FLOOR_DB = 10.0;
    /** And never below this absolute level. */
    private static final double SPEECH_MIN_DB = -65.0;
    private static final double SILENCE_DB = -120.0;
    private static final double DIGITAL_SILENCE_DB = -90.0;

    private final int channels;
    private final int sampleRate;
    private final int lookahead;

    // parameters
    private double targetDb = -20;
    private double maxBoostDb;
    private double maxCutDb;
    private double levelSeconds = 1.0;
    private double slewDbPerSecond = 3;
    private double thresholdDb = -14;
    private double ratio = 2;

    // detector / AGC state
    private boolean primed;
    private double envelopeDb;
    private double floorDb;
    private double speechPower;
    private boolean speechSeen;
    private double agcDb;
    private float blockGain = 1f;

    // limiter state
    private final float[] delay;
    private int delayPos;
    private final float[] dequeValue;
    private final long[] dequeIndex;
    private int dequeHead;
    private int dequeSize;
    private long sampleIndex;
    private float limiterGain = 1f;
    private final float limiterAttack;
    private final float limiterRelease;

    Leveler(int sampleRate, int channels) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.lookahead = Math.max(1, (int) Math.round(LOOKAHEAD_SECONDS * sampleRate));
        this.delay = new float[lookahead * channels];
        this.dequeValue = new float[lookahead + 2];
        this.dequeIndex = new long[lookahead + 2];
        // Reach ~98% of a new limit within the look-ahead, release smoothly.
        this.limiterAttack = (float) (1 - Math.exp(-4.0 / lookahead));
        this.limiterRelease = (float) (1 - Math.exp(-1.0 / (LIMITER_RELEASE_SECONDS * sampleRate)));
        reset();
    }

    int latencyFrames() {
        return lookahead;
    }

    /**
     * @param targetDb        speech level (RMS, dBFS) the AGC aims for
     * @param maxBoostDb      most the AGC raises quiet speech
     * @param maxCutDb        most the AGC lowers loud speech
     * @param levelSeconds    averaging time of the speech level the AGC follows
     * @param slewDbPerSecond how fast the AGC gain may move
     * @param thresholdAboveTargetDb where the compressor starts, relative to the target
     * @param ratio           compressor ratio above its threshold
     */
    void setParameters(double targetDb, double maxBoostDb, double maxCutDb, double levelSeconds,
                       double slewDbPerSecond, double thresholdAboveTargetDb, double ratio) {
        this.targetDb = targetDb;
        this.maxBoostDb = maxBoostDb;
        this.maxCutDb = maxCutDb;
        this.levelSeconds = levelSeconds;
        this.slewDbPerSecond = slewDbPerSecond;
        this.thresholdDb = targetDb + thresholdAboveTargetDb;
        this.ratio = ratio;
        if (agcDb > maxBoostDb) {
            agcDb = maxBoostDb;
        } else if (agcDb < -maxCutDb) {
            agcDb = -maxCutDb;
        }
    }

    /** Processes {@code frames} interleaved frames of {@code buffer} in place. */
    void process(float[] buffer, int frames) {
        for (int start = 0; start < frames; start += BLOCK) {
            int length = Math.min(BLOCK, frames - start);
            float target = computeBlockGain(buffer, start, length);
            float step = (target - blockGain) / length;
            float g = blockGain;
            int index = start * channels;
            for (int f = 0; f < length; f++) {
                g += step;
                limitFrame(buffer, index, g);
                index += channels;
            }
            blockGain = target;
        }
    }

    private float computeBlockGain(float[] buffer, int startFrame, int length) {
        double sum = 0;
        for (int i = startFrame * channels, end = (startFrame + length) * channels; i < end; i++) {
            float v = buffer[i];
            sum += v * v;
        }
        double blockPower = sum / ((double) length * channels);
        double levelDb = blockPower > 1e-12 ? 10 * Math.log10(blockPower) : SILENCE_DB;
        double blockSeconds = (double) length / sampleRate;

        // Digital silence (the stages' start-up, gaps cut to zero in editing) says nothing about
        // the background: following it would drop the floor to -120 dB and make the noise that
        // comes after look like speech for a minute while the floor creeps back up.
        boolean informative = levelDb > DIGITAL_SILENCE_DB;
        if (informative) {
            if (!primed) {
                envelopeDb = levelDb;
                floorDb = levelDb;
                primed = true;
            } else {
                double tau = levelDb > envelopeDb ? ATTACK_SECONDS : RELEASE_SECONDS;
                double keep = Math.exp(-blockSeconds / tau);
                envelopeDb = levelDb + keep * (envelopeDb - levelDb);
            }
            if (envelopeDb < floorDb) {
                floorDb = envelopeDb;
            } else {
                floorDb += FLOOR_RISE_DB_PER_SECOND * blockSeconds;
            }
        }

        boolean speech = informative && primed
                && envelopeDb > floorDb + SPEECH_ABOVE_FLOOR_DB && envelopeDb > SPEECH_MIN_DB;
        if (speech) {
            if (!speechSeen) {
                speechPower = blockPower;
                speechSeen = true;
            } else {
                double keep = Math.exp(-blockSeconds / levelSeconds);
                speechPower = keep * speechPower + (1 - keep) * blockPower;
            }
            double speechDb = 10 * Math.log10(Math.max(speechPower, 1e-12));
            double wanted = Math.max(-maxCutDb, Math.min(maxBoostDb, targetDb - speechDb));
            double maxStep = slewDbPerSecond * blockSeconds;
            agcDb += Math.max(-maxStep, Math.min(maxStep, wanted - agcDb));
        }

        double compressorInDb = (informative ? envelopeDb : levelDb) + agcDb;
        double reductionDb = 0;
        if (compressorInDb > thresholdDb) {
            reductionDb = (compressorInDb - thresholdDb) * (1 - 1 / ratio);
        }
        return (float) Math.pow(10, (agcDb - reductionDb) / 20);
    }

    private void limitFrame(float[] buffer, int index, float g) {
        float peak = 0f;
        for (int ch = 0; ch < channels; ch++) {
            float v = buffer[index + ch] * g;
            buffer[index + ch] = v;
            float a = Math.abs(v);
            if (a > peak) {
                peak = a;
            }
        }
        float required = peak > CEILING ? CEILING / peak : 1f;

        // Sliding minimum of "required" over the current frame and the look-ahead before it.
        int capacity = dequeValue.length;
        while (dequeSize > 0) {
            int tail = (dequeHead + dequeSize - 1) % capacity;
            if (dequeValue[tail] >= required) {
                dequeSize--;
            } else {
                break;
            }
        }
        int slot = (dequeHead + dequeSize) % capacity;
        dequeValue[slot] = required;
        dequeIndex[slot] = sampleIndex;
        dequeSize++;
        while (dequeIndex[dequeHead] <= sampleIndex - lookahead - 1) {
            dequeHead = (dequeHead + 1) % capacity;
            dequeSize--;
        }
        sampleIndex++;
        float limit = dequeValue[dequeHead];
        if (limit < limiterGain) {
            limiterGain += (limit - limiterGain) * limiterAttack;
        } else {
            limiterGain += (limit - limiterGain) * limiterRelease;
        }

        int d = delayPos * channels;
        for (int ch = 0; ch < channels; ch++) {
            float out = delay[d + ch] * limiterGain;
            if (out > CEILING) {
                out = CEILING;
            } else if (out < -CEILING) {
                out = -CEILING;
            }
            delay[d + ch] = buffer[index + ch];
            buffer[index + ch] = out;
        }
        delayPos = delayPos + 1 == lookahead ? 0 : delayPos + 1;
    }

    /** Drops the audio in flight but keeps the learned levels (a seek stays in one recording). */
    void flushSignal() {
        Arrays.fill(delay, 0f);
        delayPos = 0;
        dequeHead = 0;
        dequeSize = 0;
        sampleIndex = 0;
        limiterGain = 1f;
    }

    /** Forgets everything. */
    void reset() {
        flushSignal();
        primed = false;
        envelopeDb = SILENCE_DB;
        floorDb = SILENCE_DB;
        speechPower = 0;
        speechSeen = false;
        agcDb = 0;
        blockGain = 1f;
    }
}
