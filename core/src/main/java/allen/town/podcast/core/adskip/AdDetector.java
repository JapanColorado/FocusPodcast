package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import allen.town.podcast.model.feed.AdSegment;

/**
 * Finds advertisement regions in an episode's {@link FeatureFrame} profile.
 *
 * <p>Pure Java, deterministic and side-effect free: the same profile always yields the same
 * segments. It owns the whole decision, from normalisation through change-point detection and
 * scoring to merging and capping; nothing upstream or downstream needs to know the thresholds.
 *
 * <h2>Why this works at all</h2>
 * An episode is mostly one thing: the host talking, recorded once, through one chain. Ads are
 * recorded elsewhere, mixed with a music bed, and mastered louder and more compressed. So instead
 * of asking "does this sound like an ad", which would need a model we are not allowed to ship, the
 * detector asks "is this region unlike the rest of this episode, in the particular ways ads are
 * unlike hosts". Everything is normalised against the episode's own median, which makes the
 * thresholds independent of the show, the microphone and the mastering level.
 *
 * <h2>The pipeline</h2>
 * <ol>
 *   <li><b>Robust normalisation.</b> Every feature is turned into a z-score against the episode
 *       median and MAD. Median and MAD are used rather than mean and standard deviation precisely
 *       because the ads are the outliers we are hunting: they must not be allowed to move the
 *       reference. Each feature also has an absolute scale floor, so an episode with almost no
 *       variation does not get its noise amplified into apparent outliers.</li>
 *   <li><b>Change points.</b> At every hop, the mean normalised profile of the preceding context
 *       window is compared with that of the following one; the distance peaks where the material
 *       actually changes. Peaks above a threshold, greedily picked with a minimum separation,
 *       become region boundaries.</li>
 *   <li><b>Scoring.</b> Each region between boundaries gets a 0..1 confidence (see
 *       {@link #scoreRegion}).</li>
 *   <li><b>Merging and capping.</b> Flagged regions closer than three seconds are merged; the total
 *       flagged time is capped at a fraction of the episode, keeping the most confident.</li>
 * </ol>
 *
 * <p>The detector returns everything at or above {@link Config#minConfidence} (0.3 by default) with
 * its confidence attached. It is the caller's job to apply the user's sensitivity setting on top;
 * a sensible "normal" setting is 0.5.
 */
public final class AdDetector {

    // --- feature vector layout ---------------------------------------------------------------
    private static final int IDX_RMS = 0;
    private static final int IDX_CREST = 1;
    private static final int IDX_CENTROID = 2;
    private static final int IDX_FLATNESS = 3;
    private static final int IDX_ROLLOFF = 4;
    private static final int IDX_ZCR = 5;
    private static final int IDX_FLUX = 6;
    private static final int IDX_LOW = 7;
    private static final int IDX_BAND0 = 8;
    private static final int DIM = IDX_BAND0 + FeatureFrame.BAND_COUNT;

    /**
     * Smallest deviation of each feature that counts as real, in the feature's own units. The
     * robust scale never goes below this, which stops a very uniform episode from turning
     * microscopic wobble into large z-scores.
     */
    private static final double[] SCALE_FLOOR = {
            1.5,    // rmsDb, dB
            0.50,   // crest, ratio
            100.0,  // centroid, Hz
            0.02,   // flatness, 0..1
            200.0,  // rolloff, Hz
            0.01,   // zcr, fraction
            0.02,   // flux, 0..1
            0.02,   // low band ratio, 0..1
            0.015, 0.015, 0.015, 0.015, 0.015, 0.015, 0.015, 0.015 // band shares, 0..1 each
    };

    /** Dimensions the change-point statistic looks at: timbre, tonality, level and brightness. */
    private static final int[] CHANGE_DIMS = {
            IDX_BAND0, IDX_BAND0 + 1, IDX_BAND0 + 2, IDX_BAND0 + 3,
            IDX_BAND0 + 4, IDX_BAND0 + 5, IDX_BAND0 + 6, IDX_BAND0 + 7,
            IDX_FLATNESS, IDX_RMS, IDX_CENTROID
    };

    /**
     * Tuning knobs. Public final fields with a {@link Builder}; defaults are what the synthetic and
     * hand-checked episodes were tuned on.
     */
    public static final class Config {

        /** Segments below this confidence are not returned at all. */
        public final float minConfidence;

        /** Change-point statistic (normalised distance) a peak must exceed to become a boundary. */
        public final float changePointThreshold;

        /** Half-width of the before/after context compared at each hop, in milliseconds. */
        public final int contextWindowMs;

        /** Two boundaries may not be closer than this, in milliseconds. */
        public final int minBoundarySeparationMs;

        /** Flagged regions separated by less than this are merged, in milliseconds. */
        public final int mergeGapMs;

        /** Upper bound on the share of the episode that may be flagged, in 0..1. */
        public final float maxFlaggedFraction;

        /** Nothing shorter than this can be an ad, in milliseconds. */
        public final int minAdMs;

        /** Start of the "typical ad length" plateau, in milliseconds. */
        public final int idealMinAdMs;

        /** End of the "typical ad length" plateau, in milliseconds. */
        public final int idealMaxAdMs;

        /** Nothing longer than this can be an ad, in milliseconds. */
        public final int maxAdMs;

        /** How far a z-score has to travel before a term saturates at 1. */
        public final float saturation;

        /** Evidence weights; they sum to 1. */
        public final float profileWeight;
        public final float musicWeight;
        public final float loudnessWeight;

        public Config() {
            this(new Builder());
        }

        private Config(Builder b) {
            this.minConfidence = b.minConfidence;
            this.changePointThreshold = b.changePointThreshold;
            this.contextWindowMs = b.contextWindowMs;
            this.minBoundarySeparationMs = b.minBoundarySeparationMs;
            this.mergeGapMs = b.mergeGapMs;
            this.maxFlaggedFraction = b.maxFlaggedFraction;
            this.minAdMs = b.minAdMs;
            this.idealMinAdMs = b.idealMinAdMs;
            this.idealMaxAdMs = b.idealMaxAdMs;
            this.maxAdMs = b.maxAdMs;
            this.saturation = b.saturation;
            this.profileWeight = b.profileWeight;
            this.musicWeight = b.musicWeight;
            this.loudnessWeight = b.loudnessWeight;
        }

        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** Mutable builder for {@link Config}; every setter returns {@code this}. */
        public static final class Builder {
            private float minConfidence = 0.3f;
            private float changePointThreshold = 0.75f;
            private int contextWindowMs = 10000;
            private int minBoundarySeparationMs = 5000;
            private int mergeGapMs = 3000;
            private float maxFlaggedFraction = 0.25f;
            private int minAdMs = 10000;
            private int idealMinAdMs = 15000;
            private int idealMaxAdMs = 120000;
            private int maxAdMs = 240000;
            private float saturation = 1.5f;
            private float profileWeight = 0.40f;
            private float musicWeight = 0.30f;
            private float loudnessWeight = 0.30f;

            public Builder minConfidence(float v) {
                minConfidence = v;
                return this;
            }

            public Builder changePointThreshold(float v) {
                changePointThreshold = v;
                return this;
            }

            public Builder contextWindowMs(int v) {
                contextWindowMs = v;
                return this;
            }

            public Builder minBoundarySeparationMs(int v) {
                minBoundarySeparationMs = v;
                return this;
            }

            public Builder mergeGapMs(int v) {
                mergeGapMs = v;
                return this;
            }

            public Builder maxFlaggedFraction(float v) {
                maxFlaggedFraction = v;
                return this;
            }

            public Builder adLengthMs(int min, int idealMin, int idealMax, int max) {
                minAdMs = min;
                idealMinAdMs = idealMin;
                idealMaxAdMs = idealMax;
                maxAdMs = max;
                return this;
            }

            public Builder saturation(float v) {
                saturation = v;
                return this;
            }

            public Builder weights(float profile, float music, float loudness) {
                profileWeight = profile;
                musicWeight = music;
                loudnessWeight = loudness;
                return this;
            }

            @NonNull
            public Config build() {
                return new Config(this);
            }
        }
    }

    @NonNull
    private final Config config;

    public AdDetector() {
        this(new Config());
    }

    public AdDetector(@NonNull Config config) {
        this.config = config;
    }

    @NonNull
    public Config config() {
        return config;
    }

    /**
     * Runs the whole pipeline.
     *
     * @param frames     the episode profile, in time order, as produced by
     *                   {@link AudioFeatureExtractor}.
     * @param durationMs episode duration; if not positive, the end of the last frame is used.
     * @param feedItemId stamped onto every returned segment.
     * @return detected segments, sorted by start, non-overlapping, each with Source.DETECTED and a
     *         confidence at or above {@link Config#minConfidence}.
     */
    @NonNull
    public List<AdSegment> detect(@NonNull List<FeatureFrame> frames, long durationMs,
                                  long feedItemId) {
        int n = frames.size();
        long episodeMs = durationMs > 0
                ? durationMs
                : (n > 0 ? frames.get(n - 1).startMs + AudioFeatureExtractor.WINDOW_MS : 0);
        if (n < 8 || episodeMs <= 0) {
            return Collections.emptyList();
        }

        double[][] z = normalise(frames);
        int hopMs = AudioFeatureExtractor.HOP_MS;
        int context = Math.max(2, config.contextWindowMs / hopMs);
        int minSeparation = Math.max(1, config.minBoundarySeparationMs / hopMs);

        int[] boundaries = findBoundaries(z, n, context, minSeparation);
        List<Candidate> candidates = scoreRegions(frames, z, boundaries, episodeMs);
        List<Candidate> merged = mergeAndCap(candidates, episodeMs);

        List<AdSegment> out = new ArrayList<>(merged.size());
        for (Candidate c : merged) {
            long start = Math.max(0, c.startMs);
            long end = Math.min(episodeMs, c.endMs);
            if (end - start >= config.minAdMs) {
                out.add(new AdSegment(feedItemId, start, end, AdSegment.Source.DETECTED,
                        (float) c.confidence));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // 1. robust normalisation
    // ---------------------------------------------------------------------------------------

    private static double[][] normalise(List<FeatureFrame> frames) {
        int n = frames.size();
        double[][] raw = new double[DIM][n];
        for (int i = 0; i < n; i++) {
            FeatureFrame f = frames.get(i);
            raw[IDX_RMS][i] = f.rmsDb;
            raw[IDX_CREST][i] = f.crest;
            raw[IDX_CENTROID][i] = f.centroidHz;
            raw[IDX_FLATNESS][i] = f.flatness;
            raw[IDX_ROLLOFF][i] = f.rolloffHz;
            raw[IDX_ZCR][i] = f.zcr;
            raw[IDX_FLUX][i] = f.flux;
            raw[IDX_LOW][i] = f.lowBandRatio;
            for (int b = 0; b < FeatureFrame.BAND_COUNT; b++) {
                raw[IDX_BAND0 + b][i] = f.bands[b];
            }
        }
        double[][] z = new double[DIM][n];
        double[] scratch = new double[n];
        for (int d = 0; d < DIM; d++) {
            System.arraycopy(raw[d], 0, scratch, 0, n);
            double median = median(scratch);
            for (int i = 0; i < n; i++) {
                scratch[i] = Math.abs(raw[d][i] - median);
            }
            // 1.4826 * MAD estimates the standard deviation of a normal distribution.
            double scale = Math.max(1.4826 * median(scratch), SCALE_FLOOR[d]);
            for (int i = 0; i < n; i++) {
                z[d][i] = (raw[d][i] - median) / scale;
            }
        }
        return z;
    }

    /** Sorts {@code values} in place and returns its median. */
    private static double median(double[] values) {
        Arrays.sort(values);
        int n = values.length;
        return (n & 1) == 1 ? values[n / 2] : 0.5 * (values[n / 2 - 1] + values[n / 2]);
    }

    // ---------------------------------------------------------------------------------------
    // 2. change-point detection
    // ---------------------------------------------------------------------------------------

    private int[] findBoundaries(double[][] z, int n, int context, int minSeparation) {
        double[] statistic = new double[n];
        double inverseDim = 1.0 / Math.sqrt(CHANGE_DIMS.length);
        for (int i = context; i <= n - context; i++) {
            double sum = 0;
            for (int d : CHANGE_DIMS) {
                double before = 0;
                double after = 0;
                for (int k = 1; k <= context; k++) {
                    before += z[d][i - k];
                    after += z[d][i + k - 1];
                }
                double delta = (after - before) / context;
                sum += delta * delta;
            }
            statistic[i] = Math.sqrt(sum) * inverseDim;
        }

        // Greedy non-maximum suppression: strongest peaks first, each blocking its neighbourhood.
        List<Integer> order = new ArrayList<>();
        for (int i = context; i <= n - context; i++) {
            if (statistic[i] >= config.changePointThreshold) {
                order.add(i);
            }
        }
        final double[] stat = statistic;
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(stat[b], stat[a]);
            }
        });
        List<Integer> accepted = new ArrayList<>();
        for (int candidate : order) {
            boolean clear = true;
            for (int taken : accepted) {
                if (Math.abs(taken - candidate) < minSeparation) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                accepted.add(candidate);
            }
        }
        Collections.sort(accepted);

        int[] out = new int[accepted.size() + 2];
        out[0] = 0;
        for (int i = 0; i < accepted.size(); i++) {
            out[i + 1] = accepted.get(i);
        }
        out[out.length - 1] = n;
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // 3. region scoring
    // ---------------------------------------------------------------------------------------

    /** A scored region, before merging. */
    private static final class Candidate {
        long startMs;
        long endMs;
        double confidence;

        Candidate(long startMs, long endMs, double confidence) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
        }
    }

    private List<Candidate> scoreRegions(List<FeatureFrame> frames, double[][] z, int[] boundaries,
                                         long episodeMs) {
        int n = frames.size();
        double episodeFlatnessSd = standardDeviation(frames, 0, n);
        List<Candidate> out = new ArrayList<>();
        for (int r = 0; r + 1 < boundaries.length; r++) {
            int from = boundaries[r];
            int to = boundaries[r + 1];
            if (to - from < 2) {
                continue;
            }
            long startMs = from == 0 ? 0L : frames.get(from).centerMs;
            long endMs = to >= n ? episodeMs : frames.get(to).centerMs;
            if (endMs <= startMs) {
                continue;
            }
            double confidence = scoreRegion(frames, z, from, to, startMs, endMs, episodeMs,
                    episodeFlatnessSd);
            if (confidence >= config.minConfidence) {
                out.add(new Candidate(startMs, endMs, confidence));
            }
        }
        return out;
    }

    /**
     * Confidence for one region, in 0..1.
     *
     * <p>Three pieces of evidence, each a saturating function of how far the region's mean
     * normalised profile sits from the episode median:
     * <pre>
     *   profile  = |mean z of the 8 band shares| / sqrt(8)        the voice/mix changed
     *   music    = 0.30 * (flatness below median)                 tonal, i.e. a music bed
     *            + 0.25 * (low-band share above median)           bass the host chain does not have
     *            + 0.25 * (crest below median)                    compressed, no pauses
     *            + 0.20 * (flatness steadier than the episode)    the bed is sustained, not speech
     *   loudness = (RMS above median)                             mastered hotter
     *
     *   evidence = 0.40 * profile + 0.30 * music + 0.30 * loudness
     * </pre>
     * Two priors then modulate it. They can only scale the evidence between one half and one, never
     * create it, so a region that sounds exactly like the host can never be flagged because of
     * where it sits or how long it is:
     * <pre>
     *   prior      = 0.5 + 0.25 * durationPrior + 0.25 * positionPrior
     *   confidence = evidence * prior
     * </pre>
     */
    private double scoreRegion(List<FeatureFrame> frames, double[][] z, int from, int to,
                               long startMs, long endMs, long episodeMs, double episodeFlatnessSd) {
        int count = to - from;
        double[] meanZ = new double[DIM];
        for (int d = 0; d < DIM; d++) {
            double sum = 0;
            for (int i = from; i < to; i++) {
                sum += z[d][i];
            }
            meanZ[d] = sum / count;
        }

        double bandDistance = 0;
        for (int b = 0; b < FeatureFrame.BAND_COUNT; b++) {
            double v = meanZ[IDX_BAND0 + b];
            bandDistance += v * v;
        }
        double profile = saturate(Math.sqrt(bandDistance) / Math.sqrt(FeatureFrame.BAND_COUNT));

        double regionFlatnessSd = standardDeviation(frames, from, to);
        double stability = episodeFlatnessSd > 1e-9
                ? clamp(1.0 - regionFlatnessSd / episodeFlatnessSd, 0, 1)
                : 0;
        double music = 0.30 * saturate(-meanZ[IDX_FLATNESS])
                + 0.25 * saturate(meanZ[IDX_LOW])
                + 0.25 * saturate(-meanZ[IDX_CREST])
                + 0.20 * stability;

        double loudness = saturate(meanZ[IDX_RMS]);

        double evidence = config.profileWeight * profile
                + config.musicWeight * music
                + config.loudnessWeight * loudness;

        double prior = 0.5
                + 0.25 * durationPrior(endMs - startMs)
                + 0.25 * positionPrior(startMs, endMs, episodeMs);

        return clamp(evidence * prior, 0, 1);
    }

    /** Standard deviation of the flatness feature over the half-open frame range. */
    private static double standardDeviation(List<FeatureFrame> frames, int from, int to) {
        int count = to - from;
        if (count < 2) {
            return 0;
        }
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += frames.get(i).flatness;
        }
        double mean = sum / count;
        double variance = 0;
        for (int i = from; i < to; i++) {
            double d = frames.get(i).flatness - mean;
            variance += d * d;
        }
        return Math.sqrt(variance / (count - 1));
    }

    /**
     * 1 across the typical ad length, tapering to 0 outside it. Real ad breaks run 15 s to 2 min;
     * anything under 10 s is a jingle or a bad boundary and anything over 4 min is a segment of the
     * show that happens to sound different.
     */
    private double durationPrior(long durationMs) {
        if (durationMs < config.minAdMs || durationMs > config.maxAdMs) {
            return 0;
        }
        if (durationMs < config.idealMinAdMs) {
            return (double) (durationMs - config.minAdMs)
                    / Math.max(1, config.idealMinAdMs - config.minAdMs);
        }
        if (durationMs <= config.idealMaxAdMs) {
            return 1;
        }
        return (double) (config.maxAdMs - durationMs)
                / Math.max(1, config.maxAdMs - config.idealMaxAdMs);
    }

    /**
     * Weak bump for pre-roll (first two minutes) and for the usual mid-roll slots at a third and
     * two thirds of the episode. Never drops below 0.35, because plenty of shows do not place their
     * breaks anywhere near those points.
     */
    private static double positionPrior(long startMs, long endMs, long episodeMs) {
        double centre = 0.5 * (startMs + endMs);
        double best = 0.35;
        if (centre <= 120000) {
            best = 1.0;
        }
        double sigma = Math.max(45000.0, 0.08 * episodeMs);
        best = Math.max(best, gaussian(centre, episodeMs / 3.0, sigma));
        best = Math.max(best, gaussian(centre, 2.0 * episodeMs / 3.0, sigma));
        return clamp(best, 0, 1);
    }

    private static double gaussian(double x, double mu, double sigma) {
        double t = (x - mu) / sigma;
        return Math.exp(-0.5 * t * t);
    }

    private double saturate(double z) {
        return clamp(z / config.saturation, 0, 1);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------------------------------
    // 4. merging and capping
    // ---------------------------------------------------------------------------------------

    private List<Candidate> mergeAndCap(List<Candidate> candidates, long episodeMs) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Candidate> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Long.compare(a.startMs, b.startMs);
            }
        });

        List<Candidate> merged = new ArrayList<>();
        Candidate current = null;
        for (Candidate c : sorted) {
            if (current != null && c.startMs - current.endMs < config.mergeGapMs) {
                current.endMs = Math.max(current.endMs, c.endMs);
                // The merged region is at least as ad-like as its strongest part, so keep the
                // maximum rather than diluting a confident hit with a marginal neighbour.
                current.confidence = Math.max(current.confidence, c.confidence);
            } else {
                current = new Candidate(c.startMs, c.endMs, c.confidence);
                merged.add(current);
            }
        }

        long budget = (long) (config.maxFlaggedFraction * episodeMs);
        long total = 0;
        for (Candidate c : merged) {
            total += c.endMs - c.startMs;
        }
        if (total <= budget) {
            return merged;
        }
        // Over budget: keep the most confident regions that fit, then restore time order.
        List<Candidate> byConfidence = new ArrayList<>(merged);
        Collections.sort(byConfidence, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(b.confidence, a.confidence);
            }
        });
        List<Candidate> kept = new ArrayList<>();
        long used = 0;
        for (Candidate c : byConfidence) {
            long length = c.endMs - c.startMs;
            if (used + length <= budget) {
                kept.add(c);
                used += length;
            }
        }
        Collections.sort(kept, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Long.compare(a.startMs, b.startMs);
            }
        });
        return kept;
    }
}
