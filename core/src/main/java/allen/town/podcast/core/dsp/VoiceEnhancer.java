package allen.town.podcast.core.dsp;

/**
 * The "Warm" voice mode on interleaved float samples: a warmth EQ for thin, harsh microphones
 * (a low shelf of +4 dB below about 180 Hz and a 2 dB dip around 3.5 kHz), a light
 * {@link DeEsser}, and a -1 dBFS peak limiter (a {@link Leveler} with its AGC and compressor
 * switched off) to catch the peaks the shelf adds. Unlike Clear voice it has no noise reduction
 * and does not change the level of the speech, so a well-recorded episode only gets a little
 * fuller and softer on the "s" sounds. Any channel count and sample rate; delays the signal by
 * {@link #latencyFrames()}. Not thread safe.
 */
public final class VoiceEnhancer {
    private static final double SHELF_HZ = 180;
    private static final double SHELF_Q = 0.7;
    private static final double SHELF_DB = 4;
    private static final double HARSH_HZ = 3500;
    private static final double HARSH_Q = 1.0;
    private static final double HARSH_DB = -2;
    private static final double DE_ESS_THRESHOLD_DB = 0;
    private static final double DE_ESS_MAX_CUT_DB = 8;

    private final int channels;
    private final Biquad shelf;
    private final Biquad harsh;
    private final DeEsser deEsser;
    private final Leveler limiter;

    public VoiceEnhancer(int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("bad format " + sampleRate + " Hz x " + channels);
        }
        this.channels = channels;
        shelf = new Biquad(channels);
        shelf.setLowShelf(sampleRate, SHELF_HZ, SHELF_Q, SHELF_DB);
        harsh = new Biquad(channels);
        harsh.setPeaking(sampleRate, HARSH_HZ, HARSH_Q, HARSH_DB);
        deEsser = new DeEsser(sampleRate, channels);
        deEsser.setParameters(DE_ESS_THRESHOLD_DB, DE_ESS_MAX_CUT_DB);
        limiter = new Leveler(sampleRate, channels);
        // no boost, no cut, compressor threshold far above anything real: only the limiter acts
        limiter.setParameters(-20, 0, 0, 1.0, 3, 60, 1);
    }

    public int getChannels() {
        return channels;
    }

    public int latencyFrames() {
        return limiter.latencyFrames();
    }

    /** Processes {@code frames} interleaved frames of {@code buffer} in place. */
    public void process(float[] buffer, int frames) {
        shelf.process(buffer, frames);
        harsh.process(buffer, frames);
        deEsser.process(buffer, frames);
        limiter.process(buffer, frames);
    }

    /** Forgets everything, as if newly created. */
    public void reset() {
        shelf.clear();
        harsh.clear();
        deEsser.reset();
        limiter.reset();
    }
}
