package allen.town.podcast.core.adskip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Random;

/**
 * Checks that the descriptors mean what the detector assumes they mean: flatness separates noise
 * from tones, and the level readings are in dB and ordered.
 */
public class AudioFeatureExtractorTest {

    private static final int RATE = AudioFeatureExtractor.SAMPLE_RATE;

    private static List<FeatureFrame> analyse(float[] samples) {
        AudioFeatureExtractor extractor = new AudioFeatureExtractor();
        // Feed in awkward chunk sizes to exercise the window buffering.
        int position = 0;
        while (position < samples.length) {
            int length = Math.min(777, samples.length - position);
            extractor.accept(samples, position, length);
            position += length;
        }
        return extractor.finish();
    }

    private static float[] whiteNoise(int seconds, float amplitude, long seed) {
        Random random = new Random(seed);
        float[] x = new float[RATE * seconds];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (random.nextGaussian() * amplitude);
        }
        return x;
    }

    private static float[] tone(int seconds, float hz, float amplitude) {
        float[] x = new float[RATE * seconds];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * hz * i / RATE));
        }
        return x;
    }

    @Test
    public void windowsAreOneSecondEveryHalfSecond() {
        List<FeatureFrame> frames = analyse(whiteNoise(5, 0.1f, 1));
        // 5 s of audio yields windows starting at 0.0 .. 4.0 s: nine complete one-second windows.
        assertEquals(9, frames.size());
        assertEquals(0, frames.get(0).startMs);
        assertEquals(500, frames.get(1).startMs);
        assertEquals(500, frames.get(0).centerMs);
    }

    @Test
    public void whiteNoiseIsSpectrallyFlat() {
        for (FeatureFrame frame : analyse(whiteNoise(3, 0.1f, 42))) {
            assertTrue("flatness should be near 1 for white noise, was " + frame.flatness,
                    frame.flatness > 0.7f);
        }
    }

    @Test
    public void aToneIsNotSpectrallyFlat() {
        for (FeatureFrame frame : analyse(tone(3, 1000f, 0.5f))) {
            assertTrue("flatness should be near 0 for a pure tone, was " + frame.flatness,
                    frame.flatness < 0.05f);
        }
    }

    @Test
    public void louderAudioReportsHigherRmsDb() {
        float quiet = analyse(tone(2, 440f, 0.05f)).get(0).rmsDb;
        float loud = analyse(tone(2, 440f, 0.5f)).get(0).rmsDb;
        assertTrue("loud (" + loud + ") should exceed quiet (" + quiet + ")", loud > quiet);
        // A factor of ten in amplitude is 20 dB.
        assertEquals(20.0, loud - quiet, 0.5);
        // A 0.5 amplitude sine has an RMS of 0.354, i.e. -9 dBFS.
        assertEquals(-9.0, loud, 0.5);
    }

    @Test
    public void toneCentroidAndRolloffSitAtTheToneFrequency() {
        FeatureFrame frame = analyse(tone(2, 1000f, 0.5f)).get(0);
        assertEquals(1000.0, frame.centroidHz, 40.0);
        assertEquals(1000.0, frame.rolloffHz, 40.0);
    }

    @Test
    public void bandVectorIsNormalisedAndTracksTheTone() {
        FeatureFrame low = analyse(tone(2, 120f, 0.5f)).get(0);
        FeatureFrame high = analyse(tone(2, 5000f, 0.5f)).get(0);
        float sum = 0;
        for (float b : low.bands) {
            sum += b;
        }
        assertEquals(1.0, sum, 1e-3);
        assertTrue("120 Hz belongs in a low band", low.bands[1] > 0.8f);
        assertTrue("5 kHz belongs in the top band",
                high.bands[FeatureFrame.BAND_COUNT - 1] > 0.8f);
        assertTrue("low-band ratio should be high for a 120 Hz tone", low.lowBandRatio > 0.9f);
        assertTrue("low-band ratio should be tiny for a 5 kHz tone", high.lowBandRatio < 0.05f);
    }

    @Test
    public void constantSpectrumGivesZeroFluxAfterTheFirstFrame() {
        List<FeatureFrame> frames = analyse(tone(3, 1000f, 0.4f));
        assertEquals(0f, frames.get(0).flux, 1e-6);
        for (int i = 1; i < frames.size(); i++) {
            assertTrue("steady tone should have near-zero flux, was " + frames.get(i).flux,
                    frames.get(i).flux < 0.02f);
        }
    }

    @Test
    public void crestFactorIsLowerForACompressedSignal() {
        float[] square = new float[RATE * 2];
        for (int i = 0; i < square.length; i++) {
            square[i] = (i / 8) % 2 == 0 ? 0.5f : -0.5f;
        }
        float squareCrest = analyse(square).get(0).crest;
        float noiseCrest = analyse(whiteNoise(2, 0.1f, 7)).get(0).crest;
        assertEquals("a square wave has a crest factor of 1", 1.0, squareCrest, 0.05);
        assertTrue("noise peaks well above its RMS, was " + noiseCrest, noiseCrest > 3f);
    }
}
