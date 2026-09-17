package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * One analysis window's worth of audio descriptors: a single row of the per-episode signal profile
 * that {@link AdDetector} reasons over.
 *
 * <p>Frames are immutable value objects produced by {@link AudioFeatureExtractor}, one per hop
 * (0.5 s) over a one-second window. A three hour episode therefore yields roughly 21 600 of them,
 * about 1 MB in total, which is why the detector can keep the whole profile in memory while the PCM
 * itself is streamed and discarded.
 *
 * <p>All spectral descriptors are computed from a power spectrum averaged over the window's
 * 1024-point Hann sub-windows, so they describe the window's average timbre rather than any single
 * instant.
 */
public final class FeatureFrame {

    /** Number of log-spaced bands in {@link #bands}. */
    public static final int BAND_COUNT = 8;

    /** Start of the analysis window, in milliseconds from the beginning of the episode. */
    public final long startMs;

    /** Centre of the analysis window; the timestamp the detector treats this frame as describing. */
    public final long centerMs;

    /** Full-window RMS level in dBFS (negative; -100 for digital silence). */
    public final float rmsDb;

    /** Peak divided by RMS. High for speech with pauses, low for compressed/limited audio. */
    public final float crest;

    /** Power-weighted mean frequency of the averaged spectrum, in Hz. */
    public final float centroidHz;

    /**
     * Geometric mean divided by arithmetic mean of the averaged power spectrum, in 0..1.
     * Near 1 for noise-like content, near 0 for tonal content such as a music bed.
     */
    public final float flatness;

    /** Frequency below which 85% of the spectral power lies, in Hz. */
    public final float rolloffHz;

    /** Zero-crossing rate over the window, as a fraction of the sample count. */
    public final float zcr;

    /**
     * Half the L1 distance between this window's L1-normalised magnitude spectrum and the previous
     * window's, in 0..1. Scale invariant on purpose: loudness change is already in {@link #rmsDb}.
     * Zero for the first frame.
     */
    public final float flux;

    /** Share of the spectral power below 250 Hz, in 0..1. */
    public final float lowBandRatio;

    /**
     * Energy in eight log-spaced bands between 50 Hz and 8 kHz, normalised to sum to 1. This is the
     * "timbre signature": it ignores level and describes only the shape of the spectrum, which is
     * what changes when the voice, the microphone chain or the music bed changes.
     */
    @NonNull
    public final float[] bands;

    FeatureFrame(long startMs, long centerMs, float rmsDb, float crest, float centroidHz,
                 float flatness, float rolloffHz, float zcr, float flux, float lowBandRatio,
                 @NonNull float[] bands) {
        if (bands.length != BAND_COUNT) {
            throw new IllegalArgumentException("expected " + BAND_COUNT + " bands");
        }
        this.startMs = startMs;
        this.centerMs = centerMs;
        this.rmsDb = rmsDb;
        this.crest = crest;
        this.centroidHz = centroidHz;
        this.flatness = flatness;
        this.rolloffHz = rolloffHz;
        this.zcr = zcr;
        this.flux = flux;
        this.lowBandRatio = lowBandRatio;
        this.bands = bands;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "FeatureFrame{t=%.1fs rms=%.1fdB crest=%.2f centroid=%.0fHz flat=%.3f "
                        + "rolloff=%.0fHz zcr=%.3f flux=%.3f low=%.3f}",
                centerMs / 1000.0, rmsDb, crest, centroidHz, flatness, rolloffHz, zcr, flux,
                lowBandRatio);
    }
}
