package dev.russell.yagi.core.dsp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.audio.AudioProcessingPipeline;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dev.russell.yagi.core.util.playback.DownMixAudioProcessor;
import dev.russell.yagi.model.playback.ClearVoiceLevel;
import dev.russell.yagi.model.playback.VolumeLeveling;

/**
 * The whole chain the player runs, driven by ExoPlayer's real pipeline the way the sink drives it:
 * a drain has to finish and let every frame out exactly once, in every combination of effects.
 */
public class PlayerAudioProcessorChainTest {
    private static final int RATE = 44100;
    private static final int CHANNELS = 2;
    private static final int FRAME_BYTES = CHANNELS * 2;
    private static final int[][] EFFECTS = {
            {ClearVoiceLevel.OFF, VolumeLeveling.OFF},
            {ClearVoiceLevel.OFF, VolumeLeveling.GENTLE},
            {ClearVoiceLevel.LIGHT, VolumeLeveling.OFF},
            {ClearVoiceLevel.STRONG, VolumeLeveling.STRONG},
            {ClearVoiceLevel.WARM, VolumeLeveling.GENTLE},
    };

    /**
     * A wavering voice that stops 100 ms before the end, so skip silence is unsure about the rest.
     * Not a steady tone: Clear voice would rightly treat that as noise and turn it down until skip
     * silence takes it for silence.
     */
    private static byte[] speechThenShortPause() {
        int frames = RATE * 2;
        ByteBuffer pcm = ByteBuffer.allocate(frames * FRAME_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames; i++) {
            double t = (double) i / RATE;
            double voice = (0.55 + 0.45 * Math.sin(2 * Math.PI * 3 * t))
                    * Math.sin(2 * Math.PI * 220 * t + 3 * Math.sin(2 * Math.PI * 5 * t));
            short v = i < frames - RATE / 10 ? (short) (12000 * voice) : 0;
            for (int ch = 0; ch < CHANNELS; ch++) {
                pcm.putShort(v);
            }
        }
        return pcm.array();
    }

    @Test
    public void drainFinishesAndRepeatsNothingWithEveryCombinationOfEffects() throws Exception {
        byte[] input = speechThenShortPause();
        for (int[] effect : EFFECTS) {
            for (boolean skipSilence : new boolean[]{false, true}) {
                for (float speed : new float[]{1f, 1.5f, 2f}) {
                    String mode = "voice " + effect[0] + ", leveling " + effect[1]
                            + ", skip silence " + skipSilence + ", speed " + speed;
                    PlayerAudioProcessorChain chain = chain(effect);
                    chain.applySkipSilenceEnabled(skipSilence);
                    chain.applyPlaybackParameters(new PlaybackParameters(speed));
                    AudioProcessingPipeline pipeline = pipeline(chain);

                    ByteArrayOutputStream collected = new ByteArrayOutputStream();
                    queue(pipeline, input, collected);
                    assertTrue("never ended: " + mode, drain(pipeline, collected, input.length));

                    double expectedFrames = input.length / (double) FRAME_BYTES / speed;
                    double frames = collected.size() / (double) FRAME_BYTES;
                    if (skipSilence) {
                        // Under 150 ms of silence is not skipped, so nothing may be lost either.
                        assertTrue(mode + ": " + frames, frames < expectedFrames + RATE / 50.0);
                    } else {
                        assertEquals(mode, expectedFrames, frames, RATE / 50.0);
                    }
                }
            }
        }
    }

    /** What the sink does at a speed change: drain, apply the new speed, flush, carry on. */
    @Test
    public void playbackContinuesAfterASpeedChange() throws Exception {
        byte[] input = speechThenShortPause();
        for (int[] effect : EFFECTS) {
            PlayerAudioProcessorChain chain = chain(effect);
            chain.applySkipSilenceEnabled(true);
            chain.applyPlaybackParameters(new PlaybackParameters(1.5f));
            AudioProcessingPipeline pipeline = pipeline(chain);
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            queue(pipeline, input, first);
            assertTrue(drain(pipeline, first, input.length));

            chain.applyPlaybackParameters(new PlaybackParameters(2f));
            pipeline.flush();
            ByteArrayOutputStream second = new ByteArrayOutputStream();
            queue(pipeline, input, second);
            assertTrue(drain(pipeline, second, input.length));
            // Clear voice can turn the voice's dips down far enough for skip silence to take a few out.
            double expectedFrames = input.length / 2.0 / FRAME_BYTES;
            double frames = second.size() / (double) FRAME_BYTES;
            assertTrue("" + frames, frames > 0.8 * expectedFrames && frames < expectedFrames + RATE / 50.0);
        }
    }

    private static PlayerAudioProcessorChain chain(int[] effect) {
        ClearVoiceAudioProcessor clearVoice = new ClearVoiceAudioProcessor();
        clearVoice.setLevel(effect[0]);
        clearVoice.setVolumeLeveling(effect[1]);
        return new PlayerAudioProcessorChain(new DownMixAudioProcessor(), clearVoice);
    }

    private static AudioProcessingPipeline pipeline(PlayerAudioProcessorChain chain) throws Exception {
        AudioProcessingPipeline pipeline = new AudioProcessingPipeline(ImmutableList.copyOf(chain.getAudioProcessors()));
        pipeline.configure(new AudioProcessor.AudioFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT));
        pipeline.flush();
        return pipeline;
    }

    private static void queue(AudioProcessingPipeline pipeline, byte[] input, ByteArrayOutputStream collected) {
        int buffer = 1152 * FRAME_BYTES;
        for (int pos = 0; pos < input.length; pos += buffer) {
            int length = Math.min(buffer, input.length - pos);
            ByteBuffer in = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
            in.put(input, pos, length).flip();
            while (in.hasRemaining()) {
                pipeline.queueInput(in);
                collect(pipeline, collected, Integer.MAX_VALUE);
            }
        }
    }

    /** @return false if it does not end, or if it keeps producing far more than went in */
    private static boolean drain(AudioProcessingPipeline pipeline, ByteArrayOutputStream collected, int inputBytes) {
        int limit = collected.size() + inputBytes;
        for (int i = 0; i < 100 && !pipeline.isEnded(); i++) {
            pipeline.queueEndOfStream();
            if (!collect(pipeline, collected, limit)) {
                return false;
            }
        }
        return pipeline.isEnded();
    }

    private static boolean collect(AudioProcessingPipeline pipeline, ByteArrayOutputStream collected, int limit) {
        ByteBuffer out;
        while ((out = pipeline.getOutput()).hasRemaining()) {
            byte[] bytes = new byte[out.remaining()];
            out.get(bytes);
            collected.write(bytes, 0, bytes.length);
            if (collected.size() > limit) {
                return false;
            }
        }
        return true;
    }
}
