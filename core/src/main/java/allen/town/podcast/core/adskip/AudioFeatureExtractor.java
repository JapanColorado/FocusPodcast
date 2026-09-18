package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns a stream of mono 16 kHz float PCM into the episode's {@link FeatureFrame} profile.
 *
 * <p>This is the only place in the ad-skip package that knows how audio becomes numbers. It is pure
 * Java with no Android imports so that it can be unit tested on the JVM, and it is a streaming
 * consumer: {@link #accept} may be called with arbitrarily sized chunks as the decoder produces
 * them, and nothing larger than one analysis window (one second, 64 KB, plus its thirty sub-window
 * spectra) is ever retained. A three hour episode as raw 16 kHz float PCM would be about 700 MB,
 * which is why the decoder pushes into this class instead of handing it an array.
 *
 * <p>Geometry: a 1.0 s analysis window advancing in 0.5 s hops. Inside each window the signal is
 * cut into 1024-point Hann sub-windows with 50% overlap. Their power spectra are averaged, which
 * both smooths the periodogram (so spectral flatness is meaningful) and gives the window a single
 * representative timbre; and their per-bin 10th percentile is kept as the "floor", the spectrum
 * of whatever is still playing when the voice pauses ({@link FeatureFrame#floorDb}).
 */
public final class AudioFeatureExtractor {

    /** The rate every caller must resample to before feeding this class. */
    public static final int SAMPLE_RATE = 16000;

    /** Analysis window length in milliseconds. */
    public static final int WINDOW_MS = 1000;

    /** Distance between consecutive analysis windows in milliseconds. */
    public static final int HOP_MS = 500;

    static final int WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_MS / 1000;
    static final int HOP_SAMPLES = SAMPLE_RATE * HOP_MS / 1000;
    static final int FFT_SIZE = 1024;
    static final int FFT_HOP = FFT_SIZE / 2;

    /** Upper edge of the "low band" whose share of the power we track, in Hz. */
    private static final float LOW_BAND_HZ = 250f;

    /** Lower and upper edges of the eight log-spaced timbre bands, in Hz. */
    private static final float BAND_LOW_HZ = 50f;
    private static final float BAND_HIGH_HZ = 8000f;

    private static final float ROLLOFF_FRACTION = 0.85f;

    /**
     * The spectral floor is this percentile of each bin's power across the window's sub-windows.
     * Speech pauses several times a second, so the bottom tenth of a one-second window is what
     * plays underneath the voice: room tone, or a music bed.
     */
    private static final double FLOOR_PERCENTILE = 0.10;

    /** Frequency range over which the floor's flatness is measured, in Hz. */
    private static final float FLOOR_LOW_HZ = 50f;
    private static final float FLOOR_HIGH_HZ = 3000f;

    /** Guards log(0) and 0/0. Relative epsilons are used wherever the quantity has a scale. */
    private static final double EPS = 1e-12;

    private final int bins = FFT_SIZE / 2 + 1;
    private final float binWidthHz = (float) SAMPLE_RATE / FFT_SIZE;
    private final Fft fft = new Fft(FFT_SIZE);
    private final float[] hann = Fft.hann(FFT_SIZE);
    private final float[] windowed = new float[FFT_SIZE];
    private final float[] avgSpectrum = new float[bins];
    private final float[] floorSpectrum = new float[bins];
    private final float[] magnitude = new float[bins];
    private final float[] previousMagnitude = new float[bins];
    private final int[] bandOfBin = new int[bins];
    private final int lowBandBinLimit;
    private final int floorFirstBin;
    private final int floorLastBin;

    /** One power spectrum per sub-window of the current window; the floor is read column-wise. */
    private final float[][] subSpectra;
    private final float[] column;

    private final float[] buffer = new float[WINDOW_SAMPLES];
    private final List<FeatureFrame> frames = new ArrayList<>();
    private int filled;
    private long frameIndex;
    private boolean hasPrevious;

    public AudioFeatureExtractor() {
        // Precompute which log-spaced band each FFT bin belongs to; bin 0 (DC) is excluded from
        // every spectral statistic because it carries only the decoder's DC offset.
        double ratio = Math.log(BAND_HIGH_HZ / BAND_LOW_HZ);
        for (int k = 0; k < bins; k++) {
            float hz = k * binWidthHz;
            if (hz < BAND_LOW_HZ) {
                bandOfBin[k] = 0;
            } else if (hz >= BAND_HIGH_HZ) {
                bandOfBin[k] = FeatureFrame.BAND_COUNT - 1;
            } else {
                int band = (int) (FeatureFrame.BAND_COUNT * Math.log(hz / BAND_LOW_HZ) / ratio);
                bandOfBin[k] = Math.min(FeatureFrame.BAND_COUNT - 1, Math.max(0, band));
            }
        }
        lowBandBinLimit = Math.min(bins, (int) Math.ceil(LOW_BAND_HZ / binWidthHz));
        floorFirstBin = Math.max(1, (int) Math.ceil(FLOOR_LOW_HZ / binWidthHz));
        floorLastBin = Math.min(bins - 1, (int) Math.floor(FLOOR_HIGH_HZ / binWidthHz));
        int subWindowCount = (WINDOW_SAMPLES - FFT_SIZE) / FFT_HOP + 1;
        subSpectra = new float[subWindowCount][bins];
        column = new float[subWindowCount];
    }

    /** Convenience overload for callers holding a whole buffer. */
    public void accept(@NonNull float[] samples, int length) {
        accept(samples, 0, length);
    }

    /**
     * Feeds mono 16 kHz samples. Emits zero or more frames as windows complete. Safe to call with
     * any chunk size, including sizes larger than a window.
     */
    public void accept(@NonNull float[] samples, int offset, int length) {
        int position = offset;
        int end = offset + length;
        while (position < end) {
            int take = Math.min(end - position, WINDOW_SAMPLES - filled);
            System.arraycopy(samples, position, buffer, filled, take);
            filled += take;
            position += take;
            if (filled == WINDOW_SAMPLES) {
                emit();
                // Slide by one hop; the overlap stays so consecutive windows share half their audio.
                System.arraycopy(buffer, HOP_SAMPLES, buffer, 0, WINDOW_SAMPLES - HOP_SAMPLES);
                filled = WINDOW_SAMPLES - HOP_SAMPLES;
            }
        }
    }

    /**
     * Ends the stream and returns the profile. The trailing partial window is discarded rather than
     * zero-padded: a half-empty window would look like a sudden drop in level and invent a change
     * point at the end of every episode.
     */
    @NonNull
    public List<FeatureFrame> finish() {
        return Collections.unmodifiableList(frames);
    }

    /** Frames emitted so far; useful for progress reporting. */
    public int frameCount() {
        return frames.size();
    }

    private void emit() {
        long startMs = frameIndex * HOP_MS;
        frameIndex++;

        // --- time domain -------------------------------------------------------------------
        double sumSquares = 0;
        float peak = 0;
        int crossings = 0;
        float previousSample = buffer[0];
        for (int i = 0; i < WINDOW_SAMPLES; i++) {
            float s = buffer[i];
            sumSquares += (double) s * s;
            float magnitudeOfSample = Math.abs(s);
            if (magnitudeOfSample > peak) {
                peak = magnitudeOfSample;
            }
            if (i > 0 && ((s >= 0) != (previousSample >= 0))) {
                crossings++;
            }
            previousSample = s;
        }
        float rms = (float) Math.sqrt(sumSquares / WINDOW_SAMPLES);
        float rmsDb = (float) (20.0 * Math.log10(Math.max(rms, 1e-5)));
        float crest = rms > 1e-7f ? peak / rms : 1f;
        float zcr = (float) crossings / WINDOW_SAMPLES;

        // --- averaged power spectrum -------------------------------------------------------
        java.util.Arrays.fill(avgSpectrum, 0f);
        int subWindows = 0;
        for (int start = 0; start + FFT_SIZE <= WINDOW_SAMPLES; start += FFT_HOP) {
            for (int i = 0; i < FFT_SIZE; i++) {
                windowed[i] = buffer[start + i] * hann[i];
            }
            float[] spectrum = subSpectra[subWindows];
            fft.powerSpectrum(windowed, 0, spectrum);
            for (int k = 0; k < bins; k++) {
                avgSpectrum[k] += spectrum[k];
            }
            subWindows++;
        }
        if (subWindows > 0) {
            float inverse = 1f / subWindows;
            for (int k = 0; k < bins; k++) {
                avgSpectrum[k] *= inverse;
            }
        }

        // --- spectral floor: what plays underneath the voice ---------------------------------
        for (int k = 0; k < bins; k++) {
            for (int s = 0; s < subWindows; s++) {
                column[s] = subSpectra[s][k];
            }
            floorSpectrum[k] = percentile(column, subWindows, FLOOR_PERCENTILE);
        }
        double floorTotal = 0;
        double meanTotal = 0;
        for (int k = 1; k < bins; k++) {
            floorTotal += floorSpectrum[k];
            meanTotal += avgSpectrum[k];
        }
        float floorDb = meanTotal > EPS
                ? (float) Math.min(0.0, 10.0 * Math.log10((floorTotal + EPS) / (meanTotal + EPS)))
                : 0f;
        int floorBins = floorLastBin - floorFirstBin + 1;
        double floorMean = 0;
        for (int k = floorFirstBin; k <= floorLastBin; k++) {
            floorMean += floorSpectrum[k];
        }
        floorMean /= floorBins;
        double floorGuard = floorMean * 1e-12 + 1e-20;
        double floorLogSum = 0;
        for (int k = floorFirstBin; k <= floorLastBin; k++) {
            floorLogSum += Math.log(floorSpectrum[k] + floorGuard);
        }
        float floorFlatness = floorMean > 0
                ? (float) Math.min(1.0, Math.exp(floorLogSum / floorBins) / (floorMean + floorGuard))
                : 1f;

        // --- spectral descriptors ----------------------------------------------------------
        // Bin 0 is skipped everywhere: it is DC, not sound.
        double total = 0;
        double weighted = 0;
        double logSum = 0;
        int counted = bins - 1;
        for (int k = 1; k < bins; k++) {
            double p = avgSpectrum[k];
            total += p;
            weighted += p * (k * binWidthHz);
        }
        double mean = total / counted;
        double floor = mean * 1e-10 + EPS;
        for (int k = 1; k < bins; k++) {
            logSum += Math.log(avgSpectrum[k] + floor);
        }
        float centroidHz = total > EPS ? (float) (weighted / total) : 0f;
        float flatness = total > EPS
                ? (float) Math.min(1.0, Math.exp(logSum / counted) / (mean + EPS))
                : 1f;

        double rolloffTarget = total * ROLLOFF_FRACTION;
        double cumulative = 0;
        float rolloffHz = SAMPLE_RATE / 2f;
        for (int k = 1; k < bins; k++) {
            cumulative += avgSpectrum[k];
            if (cumulative >= rolloffTarget) {
                rolloffHz = k * binWidthHz;
                break;
            }
        }

        double lowEnergy = 0;
        for (int k = 1; k < lowBandBinLimit; k++) {
            lowEnergy += avgSpectrum[k];
        }
        float lowBandRatio = total > EPS ? (float) (lowEnergy / total) : 0f;

        float[] bands = new float[FeatureFrame.BAND_COUNT];
        for (int k = 1; k < bins; k++) {
            bands[bandOfBin[k]] += avgSpectrum[k];
        }
        float bandTotal = 0;
        for (float b : bands) {
            bandTotal += b;
        }
        if (bandTotal > EPS) {
            for (int b = 0; b < bands.length; b++) {
                bands[b] /= bandTotal;
            }
        } else {
            java.util.Arrays.fill(bands, 1f / FeatureFrame.BAND_COUNT);
        }

        // --- spectral flux -----------------------------------------------------------------
        // L1-normalised magnitudes, so flux measures a change of spectral *shape* only.
        double magnitudeSum = 0;
        for (int k = 1; k < bins; k++) {
            magnitude[k] = (float) Math.sqrt(avgSpectrum[k]);
            magnitudeSum += magnitude[k];
        }
        if (magnitudeSum > EPS) {
            for (int k = 1; k < bins; k++) {
                magnitude[k] /= (float) magnitudeSum;
            }
        }
        float flux = 0f;
        if (hasPrevious) {
            double diff = 0;
            for (int k = 1; k < bins; k++) {
                diff += Math.abs(magnitude[k] - previousMagnitude[k]);
            }
            flux = (float) Math.min(1.0, diff * 0.5);
        }
        System.arraycopy(magnitude, 0, previousMagnitude, 0, bins);
        hasPrevious = true;

        frames.add(new FeatureFrame(startMs, startMs + WINDOW_MS / 2, rmsDb, crest, centroidHz,
                flatness, rolloffHz, zcr, flux, lowBandRatio, floorDb, floorFlatness, bands));
    }

    /**
     * Linearly interpolated percentile of the first {@code count} values. Sorts the array in
     * place; it is a scratch column. The window has only about thirty sub-windows, so a plain sort
     * per bin costs nothing worth optimising.
     */
    static float percentile(float[] values, int count, double fraction) {
        if (count <= 0) {
            return 0f;
        }
        java.util.Arrays.sort(values, 0, count);
        double position = fraction * (count - 1);
        int below = (int) Math.floor(position);
        int above = Math.min(count - 1, below + 1);
        float weight = (float) (position - below);
        return values[below] + weight * (values[above] - values[below]);
    }
}
