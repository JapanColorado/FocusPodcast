package allen.town.podcast.core.dsp;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import allen.town.podcast.model.playback.ClearVoiceLevel;

/**
 * Runs {@link ClearVoiceChain} inside ExoPlayer's audio sink.
 *
 * <p>The sink only asks a processor whether it is active when the format is configured, so this
 * one is active for every 16-bit PCM stream and passes audio through untouched (a buffer copy)
 * while the level is Off; switching it on or off then takes effect on the next buffer, without
 * restarting playback. Other encodings never reach custom processors in practice (the sink
 * converts high-resolution PCM to 16 bit first); they are declined, which leaves the processor
 * out of the chain.
 *
 * <p>Custom processors run before the sink's speed changer, so at 2x speed this sees twice as
 * many samples per second of wall-clock time; {@link ClearVoiceChain} is cheap enough for that.
 */
public final class ClearVoiceAudioProcessor extends BaseAudioProcessor {
    private volatile int level = ClearVoiceLevel.OFF;
    @Nullable
    private ClearVoiceChain chain;
    private short[] inSamples = new short[0];
    private short[] outSamples = new short[0];
    private boolean drainPending;

    /**
     * Sets the {@link ClearVoiceLevel}. Safe to call from any thread: it only stores a volatile,
     * which the audio thread hands to the chain at the start of each buffer.
     */
    public void setLevel(int level) {
        this.level = ClearVoiceLevel.sanitize(level);
    }

    public int getLevel() {
        return level;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                || inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            return AudioFormat.NOT_SET;
        }
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) {
            return;
        }
        ClearVoiceChain current = chain;
        if (current != null) {
            current.setLevel(level);
        }
        if (current == null || current.isBypassed()) {
            replaceOutputBuffer(remaining).put(inputBuffer).flip();
            return;
        }
        int count = remaining / 2;
        if (inSamples.length < count) {
            inSamples = new short[count];
        }
        ShortBuffer shorts = inputBuffer.asShortBuffer();
        shorts.get(inSamples, 0, count);
        inputBuffer.position(inputBuffer.limit());
        int needed = current.maxOutputSamples(count);
        if (outSamples.length < needed) {
            outSamples = new short[needed];
        }
        emit(current.process(inSamples, count, outSamples));
    }

    @Override
    protected void onQueueEndOfStream() {
        // The tail still inside the stages is emitted from getOutput() once the last regular
        // output has been consumed, since the output buffer may not be replaced before that.
        ClearVoiceChain current = chain;
        drainPending = current != null && !current.isBypassed();
    }

    @Override
    public ByteBuffer getOutput() {
        ClearVoiceChain current = chain;
        if (drainPending && super.isEnded() && current != null) {
            drainPending = false;
            int needed = current.maxOutputSamples(0);
            if (outSamples.length < needed) {
                outSamples = new short[needed];
            }
            emit(current.drain(outSamples));
        }
        return super.getOutput();
    }

    @Override
    public boolean isEnded() {
        return super.isEnded() && !drainPending;
    }

    private void emit(int count) {
        if (count <= 0) {
            return;
        }
        ByteBuffer out = replaceOutputBuffer(count * 2);
        out.asShortBuffer().put(outSamples, 0, count);
        out.position(0);
        out.limit(count * 2);
    }

    @Override
    protected void onFlush() {
        drainPending = false;
        if (outputAudioFormat == AudioFormat.NOT_SET) {
            chain = null;
            return;
        }
        ClearVoiceChain current = chain;
        if (current != null && current.getSampleRate() == inputAudioFormat.sampleRate
                && current.getChannels() == inputAudioFormat.channelCount) {
            current.flush();
        } else {
            chain = new ClearVoiceChain(inputAudioFormat.sampleRate, inputAudioFormat.channelCount);
        }
    }

    @Override
    protected void onReset() {
        chain = null;
        drainPending = false;
    }
}
