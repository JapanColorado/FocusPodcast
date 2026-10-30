package dev.russell.yagi.core.dsp;

import dev.russell.yagi.model.playback.ClearVoiceLevel;
import dev.russell.yagi.model.playback.VolumeLeveling;

/**
 * The player's Voice modes and Volume leveling on interleaved 16-bit PCM, independent of any player
 * API. Every Voice mode starts with a high-pass at 80 Hz (rumble, handling noise). Light and
 * Strong (Clear voice) then run a light {@link DeEsser}, {@link SpectralDenoiser} and
 * {@link Leveler}; Warm runs {@link VoiceEnhancer} instead. Any channel count and sample rate.
 * Clear voice deliberately has no tonal EQ: the earlier cut around 300 Hz and lift around 3 kHz
 * helped muddy home recordings but made well-produced voices sound thin, which is most of what
 * people listen to.
 *
 * <p>Volume leveling ({@link VolumeLeveling}) is the same {@link Leveler} on its own: with the
 * Original voice it is the only stage (no high-pass, so the tone is untouched), with Warm it runs
 * after the enhancer, and Clear voice already has it, so there it only widens the leveler's range
 * and makes it act faster: each leveler parameter takes the stronger of the Voice mode's and the
 * leveling mode's value, so turning leveling on never levels less than Clear voice alone. All
 * modes aim at the same speech level, so switching between them keeps the loudness.
 *
 * <p>Off (Original voice, no leveling) is a plain copy, so it is bit exact. When on, the stages
 * delay the signal by {@link #latencyFrames()}; the chain hides that by discarding the first
 * {@code latency} output frames after a (re)start and by {@link #drain draining} the tail at the
 * end of the stream or when switched off, so the output has exactly the input's frames, in the
 * same positions, with nothing lost or repeated at a switch. A change that keeps the same stages
 * (Light and Strong, or the leveling strength) just changes the parameters in place; a change of
 * stages (Clear voice to Warm, leveling on or off with Original or Warm) drains one set and starts
 * the other, like switching off and on again (the learned noise floor and levels are kept).
 *
 * <p>Threading: {@link #setLevel} and {@link #setVolumeLeveling} may be called from any thread;
 * they only store a volatile that the audio thread picks up at the start of its next
 * {@link #process} call. Everything else must be called from the one audio thread.
 */
public final class ClearVoiceChain {
    private static final int CHUNK_FRAMES = 1024;
    private static final double HIGH_PASS_HZ = 80;
    /** De-esser: cut sibilants that rise above the speech body, by at most 8 dB. */
    private static final double DE_ESS_THRESHOLD_DB = 0;
    private static final double DE_ESS_MAX_CUT_DB = 8;
    private static final double TARGET_DB = -20;

    // Which stages run. Changing between these drains one set and starts the other.
    private static final int STAGES_NONE = 0;
    /** High-pass, de-esser, denoiser, leveler. */
    private static final int STAGES_CLEAR = 1;
    /** High-pass, enhancer. */
    private static final int STAGES_WARM = 2;
    /** High-pass, enhancer, leveler. */
    private static final int STAGES_WARM_LEVELED = 3;
    /** The leveler alone. */
    private static final int STAGES_LEVEL = 4;

    // Leveler parameters per mode: max boost dB, max cut dB, level averaging s, slew dB/s,
    // compressor threshold above the target dB, compressor ratio. Combined by strongerOf.
    private static final double[] LEVELER_NONE = {0, 0, Double.MAX_VALUE, 0, Double.MAX_VALUE, 1};
    private static final double[] LEVELER_CLEAR = {6, 6, 1.0, 3, 6, 2};
    private static final double[] LEVELER_CLEAR_PLUS = {12, 9, 0.5, 6, 3, 3};
    /**
     * Follows the program, not the sentence: the speech level is averaged over 4 s and the gain
     * moves at most 1.5 dB/s, so a podcast is brought to the target within the first seconds (see
     * {@link Leveler}'s settling) and then barely moves, keeping its own dynamics. The range covers
     * the spread between quiet and loud podcasts; the compressor only touches shouts.
     */
    private static final double[] LEVELER_GENTLE = {15, 12, 4.0, 1.5, 10, 1.5};
    /** Also evens out speakers and phrases, and squeezes loud moments: for noisy places. */
    private static final double[] LEVELER_STRONG = {15, 12, 0.75, 5, 4, 2.5};

    private final int sampleRate;
    private final int channels;
    private final Biquad highPass;
    private final DeEsser deEsser;
    private final SpectralDenoiser denoiser;
    private final Leveler leveler;
    private final VoiceEnhancer enhancer;
    private final int clearLatency;
    private final int warmLatency;
    private final int levelLatency;
    private final float[] work;

    private volatile int requestedLevel;
    private volatile int requestedLeveling;
    /** Voice level and leveling mode the parameters were last set for, or -1 for none yet. */
    private int appliedLevel = -1;
    private int appliedLeveling = -1;
    /** True while the stages hold audio that has not been emitted yet. */
    private boolean running;
    /** Which stages are running. Only meaningful while running. */
    private int runningStages = STAGES_NONE;
    private int skipFrames;

    public ClearVoiceChain(int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("bad format " + sampleRate + " Hz x " + channels);
        }
        this.sampleRate = sampleRate;
        this.channels = channels;
        highPass = new Biquad(channels);
        highPass.setHighPass(sampleRate, HIGH_PASS_HZ, Math.sqrt(0.5));
        deEsser = new DeEsser(sampleRate, channels);
        deEsser.setParameters(DE_ESS_THRESHOLD_DB, DE_ESS_MAX_CUT_DB);
        denoiser = new SpectralDenoiser(sampleRate, channels);
        leveler = new Leveler(sampleRate, channels);
        levelLatency = leveler.latencyFrames();
        clearLatency = denoiser.latencyFrames() + levelLatency;
        enhancer = new VoiceEnhancer(sampleRate, channels);
        warmLatency = enhancer.latencyFrames();
        work = new float[CHUNK_FRAMES * channels];
    }

    /** Requests a {@link ClearVoiceLevel}; safe from any thread, applied on the next call. */
    public void setLevel(int level) {
        requestedLevel = ClearVoiceLevel.sanitize(level);
    }

    public int getLevel() {
        return requestedLevel;
    }

    /** Requests a {@link VolumeLeveling} mode; safe from any thread, applied on the next call. */
    public void setVolumeLeveling(int mode) {
        requestedLeveling = VolumeLeveling.sanitize(mode);
    }

    public int getVolumeLeveling() {
        return requestedLeveling;
    }

    /** The delay of the modes now requested (0 when Off). */
    public int latencyFrames() {
        return latencyOf(stagesFor(requestedLevel, requestedLeveling));
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    /** True when the next {@link #process} call would be a plain copy. */
    public boolean isBypassed() {
        return stagesFor(requestedLevel, requestedLeveling) == STAGES_NONE && !running;
    }

    /** True while the stages hold audio that {@link #drain} would still push out. */
    public boolean hasAudioInFlight() {
        return running;
    }

    /** Upper bound of the shorts {@link #process} or {@link #drain} may write for {@code inSamples}. */
    public int maxOutputSamples(int inSamples) {
        return inSamples + Math.max(clearLatency, warmLatency + levelLatency) * channels;
    }

    /**
     * Processes {@code inSamples} interleaved samples (whole frames) of {@code in}.
     *
     * @param out receives the output; must hold {@link #maxOutputSamples(int)}
     * @return the number of samples written to {@code out}
     */
    public int process(short[] in, int inSamples, short[] out) {
        int level = requestedLevel;
        int leveling = requestedLeveling;
        int stages = stagesFor(level, leveling);
        int written = 0;
        if (running && runningStages != stages) {
            written = drain(out);
        }
        if (stages == STAGES_NONE) {
            System.arraycopy(in, 0, out, written, inSamples);
            return written + inSamples;
        }
        if (level != appliedLevel || leveling != appliedLeveling) {
            applyParameters(level, leveling);
        }
        if (!running) {
            start(stages);
        }
        int frames = inSamples / channels;
        for (int start = 0; start < frames; start += CHUNK_FRAMES) {
            int length = Math.min(CHUNK_FRAMES, frames - start);
            int base = start * channels;
            for (int i = 0, count = length * channels; i < count; i++) {
                work[i] = in[base + i] * (1f / 32768f);
            }
            written = runStages(length, out, written);
        }
        return written;
    }

    /**
     * Pushes the audio still inside the stages out (end of stream, or switched off). Afterwards
     * the chain restarts cleanly on the next {@link #process} call.
     *
     * @return the number of samples written to {@code out}
     */
    public int drain(short[] out) {
        if (!running) {
            return 0;
        }
        int written = 0;
        for (int remaining = latencyOf(runningStages); remaining > 0; ) {
            int length = Math.min(CHUNK_FRAMES, remaining);
            java.util.Arrays.fill(work, 0, length * channels, 0f);
            written = runStages(length, out, written);
            remaining -= length;
        }
        running = false;
        return written;
    }

    /** Drops the audio in flight (a seek); learned noise floor and levels are kept. */
    public void flush() {
        running = false;
    }

    /** Forgets everything, as if newly created. */
    public void reset() {
        running = false;
        denoiser.reset();
        leveler.reset();
        enhancer.reset();
        appliedLevel = -1;
        appliedLeveling = -1;
    }

    private static int stagesFor(int level, int leveling) {
        boolean leveled = leveling != VolumeLeveling.OFF;
        switch (level) {
            case ClearVoiceLevel.LIGHT:
            case ClearVoiceLevel.STRONG:
                return STAGES_CLEAR;
            case ClearVoiceLevel.WARM:
                return leveled ? STAGES_WARM_LEVELED : STAGES_WARM;
            default:
                return leveled ? STAGES_LEVEL : STAGES_NONE;
        }
    }

    private int latencyOf(int stages) {
        switch (stages) {
            case STAGES_CLEAR:
                return clearLatency;
            case STAGES_WARM:
                return warmLatency;
            case STAGES_WARM_LEVELED:
                return warmLatency + levelLatency;
            case STAGES_LEVEL:
                return levelLatency;
            default:
                return 0;
        }
    }

    private void start(int stages) {
        highPass.clear();
        if (stages == STAGES_CLEAR) {
            deEsser.reset();
            denoiser.flushSignal();
        } else if (stages == STAGES_WARM || stages == STAGES_WARM_LEVELED) {
            enhancer.reset();
        }
        if (stages != STAGES_WARM) {
            leveler.flushSignal();
        }
        skipFrames = latencyOf(stages);
        runningStages = stages;
        running = true;
    }

    private int runStages(int frames, short[] out, int written) {
        int stages = runningStages;
        if (stages != STAGES_LEVEL) {
            highPass.process(work, frames);
        }
        if (stages == STAGES_CLEAR) {
            deEsser.process(work, frames);
            denoiser.process(work, frames);
        } else if (stages == STAGES_WARM || stages == STAGES_WARM_LEVELED) {
            enhancer.process(work, frames);
        }
        if (stages != STAGES_WARM) {
            leveler.process(work, frames);
        }
        int first = 0;
        if (skipFrames > 0) {
            first = Math.min(skipFrames, frames);
            skipFrames -= first;
        }
        for (int i = first * channels, end = frames * channels; i < end; i++) {
            float y = work[i] * 32768f;
            int v = (int) (y >= 0 ? y + 0.5f : y - 0.5f);
            if (v > Short.MAX_VALUE) {
                v = Short.MAX_VALUE;
            } else if (v < Short.MIN_VALUE) {
                v = Short.MIN_VALUE;
            }
            out[written++] = (short) v;
        }
        return written;
    }

    private void applyParameters(int level, int leveling) {
        double[] voice = LEVELER_NONE;
        if (level == ClearVoiceLevel.STRONG) {
            denoiser.setStrength(18f, 1.5f);
            voice = LEVELER_CLEAR_PLUS;
        } else if (level == ClearVoiceLevel.LIGHT) {
            denoiser.setStrength(9f, 1.0f);
            voice = LEVELER_CLEAR;
        }
        double[] volume = LEVELER_NONE;
        if (leveling == VolumeLeveling.STRONG) {
            volume = LEVELER_STRONG;
        } else if (leveling == VolumeLeveling.GENTLE) {
            volume = LEVELER_GENTLE;
        }
        double[] p = strongerOf(voice, volume);
        leveler.setParameters(TARGET_DB, p[0], p[1], p[2], p[3], p[4], p[5]);
        appliedLevel = level;
        appliedLeveling = leveling;
    }

    /** Per parameter, whichever of the two levels more: wider range, faster, lower, steeper. */
    private static double[] strongerOf(double[] a, double[] b) {
        return new double[]{
                Math.max(a[0], b[0]),
                Math.max(a[1], b[1]),
                Math.min(a[2], b[2]),
                Math.max(a[3], b[3]),
                Math.min(a[4], b[4]),
                Math.max(a[5], b[5]),
        };
    }
}
