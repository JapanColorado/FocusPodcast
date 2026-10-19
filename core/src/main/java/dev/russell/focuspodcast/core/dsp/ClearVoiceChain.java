package dev.russell.focuspodcast.core.dsp;

import dev.russell.focuspodcast.model.playback.ClearVoiceLevel;

/**
 * The player's Voice modes on interleaved 16-bit PCM, independent of any player API. Every mode
 * starts with a high-pass at 80 Hz (rumble, handling noise). Light and Strong (Clear voice) then
 * run a light {@link DeEsser}, {@link SpectralDenoiser} and {@link Leveler}; Warm runs
 * {@link VoiceEnhancer} instead. Any channel count and sample rate. Clear voice deliberately has
 * no tonal EQ: the earlier cut around 300 Hz and lift around 3 kHz helped muddy home recordings
 * but made well-produced voices sound thin, which is most of what people listen to.
 *
 * <p>Off is a plain copy, so it is bit exact. When on, the stages delay the signal by
 * {@link #latencyFrames()}; the chain hides that by discarding the first {@code latency} output
 * frames after a (re)start and by {@link #drain draining} the tail at the end of the stream or
 * when switched off, so the output has exactly the input's frames, in the same positions, with
 * nothing lost or repeated at a switch. Switching between Light and Strong just changes the
 * parameters in place; switching between Clear voice and Warm drains one and starts the other,
 * like switching off and on again (the learned noise floor and levels are kept).
 *
 * <p>Threading: {@link #setLevel} may be called from any thread; it only stores a volatile that
 * the audio thread picks up at the start of its next {@link #process} call. Everything else must
 * be called from the one audio thread.
 */
public final class ClearVoiceChain {
    private static final int CHUNK_FRAMES = 1024;
    private static final double HIGH_PASS_HZ = 80;
    /** De-esser: cut sibilants that rise above the speech body, by at most 8 dB. */
    private static final double DE_ESS_THRESHOLD_DB = 0;
    private static final double DE_ESS_MAX_CUT_DB = 8;
    private static final double TARGET_DB = -20;

    private final int sampleRate;
    private final int channels;
    private final Biquad highPass;
    private final DeEsser deEsser;
    private final SpectralDenoiser denoiser;
    private final Leveler leveler;
    private final VoiceEnhancer enhancer;
    private final int clearLatency;
    private final int warmLatency;
    private final float[] work;

    private volatile int requestedLevel;
    private int appliedLevel = ClearVoiceLevel.OFF;
    /** True while the stages hold audio that has not been emitted yet. */
    private boolean running;
    /** Which stages are running: Warm's or Clear voice's. Only meaningful while running. */
    private boolean runningWarm;
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
        clearLatency = denoiser.latencyFrames() + leveler.latencyFrames();
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

    /** The delay of the mode now requested (0 when Off). */
    public int latencyFrames() {
        int level = requestedLevel;
        if (level == ClearVoiceLevel.OFF) {
            return 0;
        }
        return level == ClearVoiceLevel.WARM ? warmLatency : clearLatency;
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    /** True when the next {@link #process} call would be a plain copy. */
    public boolean isBypassed() {
        return requestedLevel == ClearVoiceLevel.OFF && !running;
    }

    /** Upper bound of the shorts {@link #process} or {@link #drain} may write for {@code inSamples}. */
    public int maxOutputSamples(int inSamples) {
        return inSamples + Math.max(clearLatency, warmLatency) * channels;
    }

    /**
     * Processes {@code inSamples} interleaved samples (whole frames) of {@code in}.
     *
     * @param out receives the output; must hold {@link #maxOutputSamples(int)}
     * @return the number of samples written to {@code out}
     */
    public int process(short[] in, int inSamples, short[] out) {
        int level = requestedLevel;
        int written = 0;
        if (level == ClearVoiceLevel.OFF) {
            if (running) {
                written = drain(out);
            }
            System.arraycopy(in, 0, out, written, inSamples);
            return written + inSamples;
        }
        boolean warm = level == ClearVoiceLevel.WARM;
        if (running && runningWarm != warm) {
            written = drain(out);
        }
        if (!warm && level != appliedLevel) {
            applyParameters(level);
        }
        if (!running) {
            highPass.clear();
            if (warm) {
                enhancer.reset();
                skipFrames = warmLatency;
            } else {
                deEsser.reset();
                denoiser.flushSignal();
                leveler.flushSignal();
                skipFrames = clearLatency;
            }
            runningWarm = warm;
            running = true;
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
        for (int remaining = runningWarm ? warmLatency : clearLatency; remaining > 0; ) {
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
        appliedLevel = ClearVoiceLevel.OFF;
    }

    private int runStages(int frames, short[] out, int written) {
        highPass.process(work, frames);
        if (runningWarm) {
            enhancer.process(work, frames);
        } else {
            deEsser.process(work, frames);
            denoiser.process(work, frames);
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

    private void applyParameters(int level) {
        boolean strong = level == ClearVoiceLevel.STRONG;
        if (strong) {
            denoiser.setStrength(18f, 1.5f);
            leveler.setParameters(TARGET_DB, 12, 9, 0.5, 6, 3, 3);
        } else {
            denoiser.setStrength(9f, 1.0f);
            leveler.setParameters(TARGET_DB, 6, 6, 1.0, 3, 6, 2);
        }
        appliedLevel = level;
    }
}
