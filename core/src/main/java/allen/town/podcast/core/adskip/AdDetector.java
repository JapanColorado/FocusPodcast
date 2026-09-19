package allen.town.podcast.core.adskip;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import allen.town.podcast.model.feed.AdSegment;

/**
 * Finds advertisement regions in an episode's {@link FeatureFrame} profile.
 *
 * <p>Pure Java, deterministic and side-effect free: the same profile always yields the same
 * segments. It owns the whole decision, from the per-frame score through segmentation to
 * confidence and capping; nothing upstream or downstream needs to know the thresholds.
 *
 * <h2>What it listens for</h2>
 * A music bed. Almost every produced ad, host-read or not, runs music underneath the voice, and
 * the show around it almost never does. The bed shows up in the spectral floor of each one-second
 * window ({@link FeatureFrame#floorDb}, {@link FeatureFrame#floorFlatness}): speech pauses several
 * times a second, and in those pauses plain speech drops to quiet, noise-like room tone while a
 * bed stays up at a steady level with a tonal spectrum. Both readings are absolute, so the detector
 * needs no per-episode normalisation and is indifferent to how many voices the show has, how they
 * are miked, or how the episode was mastered. An earlier design scored "how unlike the rest of the
 * episode is this region"; measured against labelled episodes it turned out that ads are quieter
 * than the show on one podcast and louder on the next, flatter on one and peakier on another, and
 * on interview shows the guest's voice is at least as unlike the median as any ad. Only the bed
 * held up across shows.
 *
 * <h2>The pipeline</h2>
 * <ol>
 *   <li><b>Bed score.</b> Each frame gets a 0..1 score, the product of two saturating ramps: how
 *       far the floor level is above {@link Config#floorDbMin} and how far its flatness is below
 *       {@link Config#floorFlatnessMax}. Noisy-room speech fails the flatness ramp, a quiet hum
 *       fails the level ramp, and a bed passes both.</li>
 *   <li><b>Segmentation.</b> The score is averaged over a {@link Config#smoothingMs} window, since
 *       a bed dips under loud syllables and between sentences, and runs are cut with hysteresis
 *       ({@link Config#enterThreshold} to open, {@link Config#stayThreshold} to keep going). The
 *       long average blurs the edges, so each edge is then re-placed on a short (1.5 s) average.
 *       Runs closer than {@link Config#mergeGapMs} are joined, because one ad break is often two
 *       spots with a spoken hand-over between them.</li>
 *   <li><b>Confidence.</b> The mean bed score over the run, scaled so that
 *       {@link Config#evidenceFullAt} reads as certain, then multiplied by a mild prior that
 *       prefers typical ad lengths and the usual pre-roll and mid-roll positions. The prior can
 *       scale evidence down by at most 30%; it never creates any.</li>
 *   <li><b>Capping.</b> The total flagged time is limited to {@link Config#maxFlaggedFraction} of
 *       the episode, keeping the most confident runs.</li>
 * </ol>
 *
 * <p>The detector returns everything at or above {@link Config#minConfidence} (0.3 by default) with
 * its confidence attached. It is the caller's job to apply the user's sensitivity setting on top.
 *
 * <p>Honest limits: an ad read by the host with no bed and no level change is invisible to this
 * detector, and a theme tune (intro or outro) looks exactly like a produced spot and will be
 * flagged when it is long enough. Both are why segments are shown on the seek bar and can be
 * undone, disabled or deleted per episode.
 */
public final class AdDetector {

    /**
     * Tuning knobs. Public final fields with a {@link Builder}; the defaults were fitted on
     * twenty-three labelled sponsor breaks across six shows and checked on a synthetic episode.
     */
    public static final class Config {

        /** Segments below this confidence are not returned at all. */
        public final float minConfidence;

        /** Floor level at or below which a frame has no bed, in dB relative to the window mean. */
        public final float floorDbMin;

        /** The level ramp saturates this many dB above {@link #floorDbMin}. */
        public final float floorDbRange;

        /** Floor flatness at or above which a frame has no bed. */
        public final float floorFlatnessMax;

        /** The flatness ramp saturates this far below {@link #floorFlatnessMax}. */
        public final float floorFlatnessRange;

        /**
         * Floor level, in dB relative to the window mean, from which a frame counts as "filled"
         * regardless of flatness: a produced spot whose music leaves no gaps at all.
         */
        public final float filledFloorDbMin;

        /** The filled-floor ramp saturates this many dB above {@link #filledFloorDbMin}. */
        public final float filledFloorDbRange;

        /** Length of the moving average the segmentation runs on, in milliseconds. */
        public final int smoothingMs;

        /** Smoothed score needed to open a run. */
        public final float enterThreshold;

        /** Smoothed score below which an open run closes. */
        public final float stayThreshold;

        /** Short-average score an edge is moved out to, when re-placing the blurred edges. */
        public final float edgeThreshold;

        /** Runs separated by less than this are merged, in milliseconds. */
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

        /**
         * Which quantile of the per-frame bed score over a run is taken as its evidence. The
         * median (0.5) by default: a real bed is present in most frames of the run, while the
         * false runs that a mean would admit are ones where the score flickers on and off.
         */
        public final float evidenceQuantile;

        /** Evidence-quantile bed score that counts as full evidence. */
        public final float evidenceFullAt;

        /** Prior with both duration and position at their worst; the three weights sum to 1. */
        public final float priorBase;

        /** Weight of the duration prior. */
        public final float durationWeight;

        /** Weight of the position prior. */
        public final float positionWeight;

        public Config() {
            this(new Builder());
        }

        private Config(Builder b) {
            this.minConfidence = b.minConfidence;
            this.floorDbMin = b.floorDbMin;
            this.floorDbRange = b.floorDbRange;
            this.floorFlatnessMax = b.floorFlatnessMax;
            this.floorFlatnessRange = b.floorFlatnessRange;
            this.filledFloorDbMin = b.filledFloorDbMin;
            this.filledFloorDbRange = b.filledFloorDbRange;
            this.smoothingMs = b.smoothingMs;
            this.enterThreshold = b.enterThreshold;
            this.stayThreshold = b.stayThreshold;
            this.edgeThreshold = b.edgeThreshold;
            this.mergeGapMs = b.mergeGapMs;
            this.maxFlaggedFraction = b.maxFlaggedFraction;
            this.minAdMs = b.minAdMs;
            this.idealMinAdMs = b.idealMinAdMs;
            this.idealMaxAdMs = b.idealMaxAdMs;
            this.maxAdMs = b.maxAdMs;
            this.evidenceQuantile = b.evidenceQuantile;
            this.evidenceFullAt = b.evidenceFullAt;
            this.priorBase = b.priorBase;
            this.durationWeight = b.durationWeight;
            this.positionWeight = b.positionWeight;
        }

        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** Mutable builder for {@link Config}; every setter returns {@code this}. */
        public static final class Builder {
            private float minConfidence = 0.3f;
            private float floorDbMin = -31f;
            private float floorDbRange = 8f;
            private float floorFlatnessMax = 0.16f;
            private float floorFlatnessRange = 0.08f;
            private float filledFloorDbMin = -21f;
            private float filledFloorDbRange = 6f;
            private int smoothingMs = 10000;
            private float enterThreshold = 0.35f;
            private float stayThreshold = 0.20f;
            private float edgeThreshold = 0.25f;
            private int mergeGapMs = 10000;
            private float maxFlaggedFraction = 0.25f;
            private int minAdMs = 20000;
            private int idealMinAdMs = 60000;
            private int idealMaxAdMs = 240000;
            private int maxAdMs = 420000;
            private float evidenceQuantile = 0.5f;
            private float evidenceFullAt = 0.5f;
            private float priorBase = 0.55f;
            private float durationWeight = 0.30f;
            private float positionWeight = 0.15f;

            public Builder minConfidence(float v) {
                minConfidence = v;
                return this;
            }

            public Builder floorLevel(float minDb, float rangeDb) {
                floorDbMin = minDb;
                floorDbRange = rangeDb;
                return this;
            }

            public Builder floorFlatness(float max, float range) {
                floorFlatnessMax = max;
                floorFlatnessRange = range;
                return this;
            }

            public Builder filledFloor(float minDb, float rangeDb) {
                filledFloorDbMin = minDb;
                filledFloorDbRange = rangeDb;
                return this;
            }

            public Builder smoothingMs(int v) {
                smoothingMs = v;
                return this;
            }

            public Builder thresholds(float enter, float stay, float edge) {
                enterThreshold = enter;
                stayThreshold = stay;
                edgeThreshold = edge;
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

            public Builder evidence(float quantile, float fullAt) {
                evidenceQuantile = quantile;
                evidenceFullAt = fullAt;
                return this;
            }

            public Builder priors(float base, float duration, float position) {
                priorBase = base;
                durationWeight = duration;
                positionWeight = position;
                return this;
            }

            @NonNull
            public Config build() {
                return new Config(this);
            }
        }
    }

    /** Frames the short edge-placement average spans (1.5 s). */
    private static final int EDGE_SMOOTHING_FRAMES = 3;

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

        float[] bed = new float[n];
        for (int i = 0; i < n; i++) {
            bed[i] = bedScore(frames.get(i));
        }
        int hopMs = AudioFeatureExtractor.HOP_MS;
        int smoothingFrames = Math.max(1, config.smoothingMs / hopMs);
        float[] smoothed = movingAverage(bed, smoothingFrames);
        float[] edges = movingAverage(bed, EDGE_SMOOTHING_FRAMES);

        List<int[]> runs = hysteresis(smoothed);
        refineEdges(runs, edges, smoothingFrames / 2, n);
        runs = mergeRuns(runs, Math.max(1, config.mergeGapMs / hopMs));

        List<Candidate> candidates = new ArrayList<>();
        for (int[] run : runs) {
            long startMs = run[0] == 0 ? 0L : frames.get(run[0]).startMs;
            long endMs = Math.min(episodeMs,
                    frames.get(run[1] - 1).startMs + AudioFeatureExtractor.WINDOW_MS);
            if (endMs - startMs < config.minAdMs) {
                continue;
            }
            double confidence = confidence(bed, run[0], run[1], startMs, endMs, episodeMs);
            if (confidence >= config.minConfidence) {
                candidates.add(new Candidate(startMs, endMs, confidence));
            }
        }

        List<AdSegment> out = new ArrayList<>();
        for (Candidate c : cap(candidates, episodeMs)) {
            out.add(new AdSegment(feedItemId, c.startMs, c.endMs, AdSegment.Source.DETECTED,
                    (float) c.confidence));
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // 1. per-frame bed score
    // ---------------------------------------------------------------------------------------

    /**
     * How much this frame sounds like speech over a music bed, in 0..1. Exposed for tests and for
     * the diagnostics that tune the thresholds.
     */
    float bedScore(@NonNull FeatureFrame frame) {
        float level = clamp((frame.floorDb - config.floorDbMin) / config.floorDbRange, 0f, 1f);
        float tonal = clamp((config.floorFlatnessMax - frame.floorFlatness)
                / config.floorFlatnessRange, 0f, 1f);
        // A produced spot with drums or broadband music under the voice has a floor that is not
        // tonal but is so high that speech gaps have all but vanished; nothing a host records in
        // a room gets there, except heavily processed shows, which the duration prior handles.
        float filled = clamp((frame.floorDb - config.filledFloorDbMin) / config.filledFloorDbRange,
                0f, 1f);
        return Math.max(level * tonal, filled);
    }

    // ---------------------------------------------------------------------------------------
    // 2. segmentation
    // ---------------------------------------------------------------------------------------

    /** Centred moving average over {@code width} frames, shrinking the window at both ends. */
    private static float[] movingAverage(float[] values, int width) {
        int n = values.length;
        float[] out = new float[n];
        int half = width / 2;
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }
        for (int i = 0; i < n; i++) {
            int from = Math.max(0, i - half);
            int to = Math.min(n, i + half + 1);
            out[i] = (float) ((prefix[to] - prefix[from]) / (to - from));
        }
        return out;
    }

    /** Half-open {from, to} frame ranges where the smoothed score stays up. */
    private List<int[]> hysteresis(float[] smoothed) {
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < smoothed.length; i++) {
            if (start < 0) {
                if (smoothed[i] >= config.enterThreshold) {
                    start = i;
                }
            } else if (smoothed[i] < config.stayThreshold) {
                runs.add(new int[]{start, i});
                start = -1;
            }
        }
        if (start >= 0) {
            runs.add(new int[]{start, smoothed.length});
        }
        return runs;
    }

    /**
     * Pulls each run's edges inward to where the short average first reaches
     * {@link Config#edgeThreshold}. The long average that cut the run crosses its thresholds while
     * most of its window is still outside the bed, so a run's edges sit up to half a window too
     * far out; walking inward from each edge on the short average finds where the bed really
     * starts and stops. Edges only ever move inward here, never past each other.
     */
    private void refineEdges(List<int[]> runs, float[] edges, int reach, int n) {
        for (int[] run : runs) {
            int start = run[0];
            int limit = Math.min(run[1] - 1, run[0] + reach);
            for (int i = run[0]; i <= limit; i++) {
                if (edges[i] >= config.edgeThreshold) {
                    start = i;
                    break;
                }
            }
            int end = run[1];
            int floor = Math.max(start + 1, run[1] - reach);
            for (int i = run[1] - 1; i >= floor; i--) {
                if (edges[i] >= config.edgeThreshold) {
                    end = i + 1;
                    break;
                }
            }
            run[0] = start;
            run[1] = Math.max(end, start + 1);
        }
    }

    private static List<int[]> mergeRuns(List<int[]> runs, int gapFrames) {
        List<int[]> merged = new ArrayList<>();
        int[] current = null;
        for (int[] run : runs) {
            if (current != null && run[0] - current[1] < gapFrames) {
                current[1] = Math.max(current[1], run[1]);
            } else {
                current = new int[]{run[0], run[1]};
                merged.add(current);
            }
        }
        return merged;
    }

    // ---------------------------------------------------------------------------------------
    // 3. confidence
    // ---------------------------------------------------------------------------------------

    /** A scored run, before capping. */
    private static final class Candidate {
        final long startMs;
        final long endMs;
        final double confidence;

        Candidate(long startMs, long endMs, double confidence) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
        }
    }

    /**
     * Confidence for one run, in 0..1:
     * <pre>
     *   evidence   = evidenceQuantile of the bed score over the run / evidenceFullAt, capped at 1
     *   prior      = priorBase + durationWeight * durationPrior + positionWeight * positionPrior
     *   confidence = evidence * prior
     * </pre>
     * The duration prior carries real weight because it is the best single separator between ad
     * breaks and everything else that has music under it: measured on labelled episodes, ad
     * breaks ran from about a minute to six minutes, while stings, transitions, laughs and the
     * odd hummy room all produced runs under a minute.
     */
    private double confidence(float[] bed, int from, int to, long startMs, long endMs,
                              long episodeMs) {
        int count = Math.max(1, to - from);
        float[] sorted = new float[count];
        System.arraycopy(bed, from, sorted, 0, Math.min(count, bed.length - from));
        float typical = AudioFeatureExtractor.percentile(sorted, count, config.evidenceQuantile);
        double evidence = clamp(typical / config.evidenceFullAt, 0, 1);
        double prior = config.priorBase
                + config.durationWeight * durationPrior(endMs - startMs)
                + config.positionWeight * positionPrior(startMs, endMs, episodeMs);
        return clamp(evidence * prior, 0, 1);
    }

    /**
     * 1 across the typical ad length, tapering to 0 outside it. Real ad breaks run 15 s to 2 min;
     * anything under 10 s is a jingle and anything over 4 min is a segment of the show that
     * happens to have music under it.
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

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------------------------------
    // 4. capping
    // ---------------------------------------------------------------------------------------

    private List<Candidate> cap(List<Candidate> candidates, long episodeMs) {
        // A short episode still gets room for one full-length break; the fraction is there to
        // stop a long episode with music under everything from being flagged wholesale.
        long budget = Math.max((long) (config.maxFlaggedFraction * episodeMs),
                config.idealMaxAdMs);
        long total = 0;
        for (Candidate c : candidates) {
            total += c.endMs - c.startMs;
        }
        if (total <= budget) {
            return candidates;
        }
        // Over budget: keep the most confident runs that fit, then restore time order.
        List<Candidate> byConfidence = new ArrayList<>(candidates);
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
