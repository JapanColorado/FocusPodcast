# Ad auto-skip: how the detector was tuned

This records the measurement that produced the `AdDetector.Config` defaults, so that the next
change to the detector is measured the same way instead of tuned by ear. Everything below ran on
the JVM against real episodes; nothing needed a device.

## Ground truth

Sponsor chapters. Several networks publish chapter lists in which every ad break is a chapter
titled `Sponsor: <name>` (Podcasting 2.0 `<podcast:chapters>` JSON, or ID3 chapters in the MP3).
Those timestamps are the labels. Episodes used, all fetched from the public feeds on 2026-09-18:

| show | episode | labelled breaks | ad style |
|---|---|---|---|
| The Changelog | 684, 685 | 3 + 3 | host-read over a music bed |
| Changelog & Friends | 130 | 3 | host-read over a music bed |
| Go Time | 339 | 3 | host-read over a music bed |
| JS Party | 349 | 3 | host-read over a music bed, one very quiet bed |
| Ship It | 134 | 3 | host-read over a music bed |
| Practical AI | (Transistor id 66185604) | 2 | host-read over a music bed, no bass |
| Accidental Tech Podcast | 709 | 3 | host-read, **no bed at all** |

23 labelled breaks, 3257 s, over 12.6 hours of audio. Six further episodes without sponsor
chapters (Upgrade 637, Linux Unplugged 683/684, Syntax 1039, Mac Power Users 866, Talk Python 563)
were used only to eyeball false positives. Show intros under two minutes and chapters titled
outro/ending/closing/wrapping up are scored as *neutral*: theme music is indistinguishable from a
produced spot, and skipping it is not a mistake most listeners would mind.

## Harness

1. `ffmpeg -i episode.mp3 -ac 1 -ar 16000 -f f32le -` piped into a tiny `main` that feeds the
   real `AudioFeatureExtractor` and writes one `FeatureFrame` per line (a *frames* cache).
2. A bench `main` loads the frames, runs the real `AdDetector` with `Config` knobs taken from
   `-D` system properties, and reports, at the three sensitivity thresholds the app uses
   (0.45 high, 0.60 medium, 0.75 low): breaks hit, share of ad time covered, false segments,
   non-ad minutes that would be skipped, and *overrun*, the seconds a detected segment extends
   past its labelled break into content.
3. Compile with plain `javac` against the module sources plus the androidx annotation jar; no
   Gradle, no Android. A full pass over 14 episodes takes about two seconds once frames are cached.

## What the measurements showed

**The original scoring did nothing.** It combined a timbre change against the episode median
with three signed cues: ads are louder, more compressed (lower crest factor) and more tonal
(lower spectral flatness). On the labelled data those three cues flip sign from show to show:
Changelog ads are 0.6 to 1.0 dB-z *quieter* than the show, have a *higher* crest factor and
*higher* flatness (the bed fills in the spectrum); Friends 130 is the other way round. Only the
timbre term ever fired, capping confidence near 0.45, below the medium threshold. Result at
medium sensitivity: 3 of 23 breaks, 8% of ad time.

**"Different from the episode median" is the wrong question.** On interview shows the guest's
voice sits as far from the median as any ad, and one Changelog guest's boomy microphone gave
non-ad minutes more sustained bass than the ads had.

**The music bed is what is consistent.** Speech pauses several times a second. Taking, for each
FFT bin, the 10th percentile of power across the thirty sub-windows of a one-second window gives
the *spectral floor*: what is still playing when the voice stops. Two absolute readings of it
separate ads regardless of the voices, the microphones or the mastering:

- `floorDb`, the floor relative to the window mean. Plain speech in a quiet room: -35 dB and
  below. Speech over a bed: -30 to -20 dB. Wall-to-wall music: near 0 dB.
- `floorFlatness`, the spectral flatness of the floor between 50 Hz and 3 kHz. Room noise: 0.15
  and up. A bed: well under 0.1 (sustained harmonics).

Either alone fails somewhere (Ship It's room has a quiet tonal hum; JS Party's hosts have a loud
noise-like floor). Their product, each through a saturating ramp, gave a region-level AUC of 0.91
against 60-second non-ad windows; the false alarms left were outro themes.

**Duration is the best second cue.** With per-frame scores smoothed over 10 s and cut with
hysteresis, true breaks produced runs of 65 to 370 s while stings, transitions, laughs and hummy
rooms produced runs of 13 to 68 s (AUC 0.97). The confidence prior therefore weights duration at
0.30 with the plateau starting at 60 s.

**The median beats the mean.** Runs where the bed score flickers on and off (mean 0.4 to 0.48)
were the bulk of the false positives at medium sensitivity; real beds hold a median of 0.5 to 1.0.
Evidence is the run's median bed score.

**Edges must be pulled inward.** A 10 s moving average crosses its thresholds while most of the
window is still outside the bed, so hysteresis edges sit up to 5 s too far out; walking inward
from each edge on a 1.5 s average fixed a 766 s total overrun.

## Result with the shipped defaults

| sensitivity | threshold | breaks hit | ad time covered | false segments | non-ad time skipped |
|---|---|---|---|---|---|
| high | 0.45 | 19 / 23 | 71% | 20 | 16.8 min / 12.6 h |
| medium (default) | 0.60 | 19 / 23 | 68% | 5 | 8.9 min / 12.6 h |
| low | 0.75 | 17 / 23 | 63% | 2 | 6.7 min / 12.6 h |

Overrun into content around the detected breaks: 175 s in total, about 8 s per break. The four
misses are the three ATP breaks (no bed; the detector cannot see them, and it flags nothing else
in that episode either at medium) and the JS Party WorkOS spot, whose bed is quieter than the
level ramp admits. Most of the remaining false segments at medium are outro music that the
chapter files did not label. Partial coverage on some breaks (Changelog's Buildkite spots, for
example) is a bed that drops out mid-ad; the skipper then jumps the first part and the rest plays.

## Re-running it

The harness lives outside the repository (it hard-codes local paths and needs ffmpeg and the
downloaded episodes), but it is small: `Extract` (stdin PCM to frames), `Frames` (loader),
`Bench` (scoring) and `Series` (print bed score over a time range). Recreate it from this
description, keep the sponsor-chapter episodes as ground truth, and treat the table above as the
baseline any change has to beat on both the hit count at 0.60 and the non-ad minutes.
