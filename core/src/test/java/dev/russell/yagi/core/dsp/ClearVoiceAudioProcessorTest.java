package dev.russell.yagi.core.dsp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import dev.russell.yagi.model.playback.ClearVoiceLevel;
import dev.russell.yagi.model.playback.VolumeLeveling;

/**
 * The ExoPlayer adapter, driven the way {@code DefaultAudioSink} drives it: configure, flush,
 * queue buffers and read each output before the next input, then end of stream.
 */
public class ClearVoiceAudioProcessorTest {
    private static final int RATE = 48000;
    private static final int CHANNELS = 2;

    @Test
    public void activeForSixteenBitPcmOnly() throws Exception {
        ClearVoiceAudioProcessor processor = new ClearVoiceAudioProcessor();
        processor.configure(new AudioProcessor.AudioFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT));
        assertTrue(processor.isActive());
        processor.configure(new AudioProcessor.AudioFormat(RATE, CHANNELS, C.ENCODING_PCM_FLOAT));
        assertFalse(processor.isActive());
    }

    @Test
    public void offPassesEveryByteThrough() throws Exception {
        ClearVoiceAudioProcessor processor = configured(ClearVoiceLevel.OFF);
        byte[] input = randomPcm(RATE / 2);
        assertArrayEquals(input, runThrough(processor, input));
    }

    @Test
    public void onKeepsTheStreamLengthAndCanBeSwitchedLive() throws Exception {
        ClearVoiceAudioProcessor processor = configured(ClearVoiceLevel.LIGHT);
        byte[] input = randomPcm(RATE * 2);
        byte[] output = runThrough(processor, input, ClearVoiceLevel.STRONG, ClearVoiceLevel.OFF);
        assertEquals(input.length, output.length);
        assertTrue(processor.isEnded());
    }

    @Test
    public void flushStartsCleanAndKeepsTheLevel() throws Exception {
        ClearVoiceAudioProcessor processor = configured(ClearVoiceLevel.STRONG);
        runThrough(processor, randomPcm(RATE));
        processor.flush();
        byte[] silence = new byte[RATE * CHANNELS * 2];
        assertArrayEquals(silence, runThrough(processor, silence));
        assertEquals(ClearVoiceLevel.STRONG, processor.getLevel());
    }

    @Test
    public void levelingAloneIsAppliedAndKeepsTheStreamLength() throws Exception {
        ClearVoiceAudioProcessor processor = configured(ClearVoiceLevel.OFF);
        processor.setVolumeLeveling(VolumeLeveling.STRONG);
        // quiet phrases (about -40 dBFS) with faint noise (-70 dBFS) in the pauses between them,
        // which the leveler brings up
        ByteBuffer quiet = ByteBuffer.wrap(randomPcm(RATE * 4)).order(ByteOrder.nativeOrder());
        for (int i = 0; i < RATE * 4 * CHANNELS; i++) {
            boolean phrase = (i / CHANNELS) % (RATE * 8 / 10) < RATE * 4 / 10;
            quiet.putShort(i * 2, (short) (quiet.getShort(i * 2) / (phrase ? 10 : 300)));
        }
        byte[] input = quiet.array();
        byte[] output = runThrough(processor, input);
        assertEquals(input.length, output.length);
        assertTrue(rms(output, RATE * 2) > 3 * rms(input, RATE * 2));
        assertEquals(VolumeLeveling.STRONG, processor.getVolumeLeveling());
    }

    private static ClearVoiceAudioProcessor configured(int level) throws Exception {
        ClearVoiceAudioProcessor processor = new ClearVoiceAudioProcessor();
        processor.setLevel(level);
        processor.configure(new AudioProcessor.AudioFormat(RATE, CHANNELS, C.ENCODING_PCM_16BIT));
        processor.flush();
        return processor;
    }

    /**
     * Queues {@code input} in 20 ms buffers, switching to the next of {@code switches} at each
     * quarter, then ends the stream and returns all output.
     */
    private static byte[] runThrough(ClearVoiceAudioProcessor processor, byte[] input, int... switches) {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        int buffer = RATE / 50 * CHANNELS * 2;
        int quarter = input.length / 4;
        int nextSwitch = 0;
        for (int pos = 0; pos < input.length; pos += buffer) {
            if (nextSwitch < switches.length && pos >= quarter * (nextSwitch + 1)) {
                processor.setLevel(switches[nextSwitch++]);
            }
            int length = Math.min(buffer, input.length - pos);
            ByteBuffer in = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
            in.put(input, pos, length).flip();
            processor.queueInput(in);
            assertFalse(in.hasRemaining());
            drainOutput(processor, collected);
        }
        processor.queueEndOfStream();
        for (int i = 0; i < 4 && !processor.isEnded(); i++) {
            drainOutput(processor, collected);
        }
        return collected.toByteArray();
    }

    private static void drainOutput(ClearVoiceAudioProcessor processor, ByteArrayOutputStream collected) {
        ByteBuffer out = processor.getOutput();
        byte[] bytes = new byte[out.remaining()];
        out.get(bytes);
        collected.write(bytes, 0, bytes.length);
    }

    /** RMS of the 16-bit samples from frame {@code fromFrame} on. */
    private static double rms(byte[] pcm, int fromFrame) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.nativeOrder());
        double sum = 0;
        int count = 0;
        for (int i = fromFrame * CHANNELS; i < pcm.length / 2; i++, count++) {
            double v = buffer.getShort(i * 2);
            sum += v * v;
        }
        return Math.sqrt(sum / Math.max(1, count));
    }

    private static byte[] randomPcm(int frames) {
        Random random = new Random(frames);
        ByteBuffer buffer = ByteBuffer.allocate(frames * CHANNELS * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames * CHANNELS; i++) {
            buffer.putShort((short) (random.nextGaussian() * 3000));
        }
        return buffer.array();
    }
}
