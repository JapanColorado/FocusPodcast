package dev.russell.yagi.core.dsp;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.SilenceSkippingAudioProcessor;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;

import java.nio.ByteBuffer;

/**
 * ExoPlayer's default processors (skip silence, then speed) behind the given custom ones, with one
 * repair. {@code AudioProcessingPipeline} calls {@code queueEndOfStream()} on a processor again on
 * every pass once the processor before it has ended, and {@link SilenceSkippingAudioProcessor}
 * answers each call by outputting its buffer of possible silence, without emptying it. So if the
 * sink drains the pipeline (at the end of an episode, at a speed change) during a pause in the
 * speech, that chunk of audio loops for as long as playback runs and the drain never finishes.
 * Here it only ever gets the first call.
 */
public final class PlayerAudioProcessorChain implements DefaultAudioSink.AudioProcessorChain {
    private final DefaultAudioSink.DefaultAudioProcessorChain delegate;
    private final AudioProcessor[] processors;

    public PlayerAudioProcessorChain(AudioProcessor... customProcessors) {
        SilenceSkippingAudioProcessor silenceSkipping = new SilenceSkippingAudioProcessor();
        delegate = new DefaultAudioSink.DefaultAudioProcessorChain(
                customProcessors, silenceSkipping, new SonicAudioProcessor());
        processors = delegate.getAudioProcessors().clone();
        for (int i = 0; i < processors.length; i++) {
            if (processors[i] == silenceSkipping) {
                processors[i] = new SingleEndOfStream(silenceSkipping);
            }
        }
    }

    @Override
    public AudioProcessor[] getAudioProcessors() {
        return processors;
    }

    @Override
    public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
        return delegate.applyPlaybackParameters(playbackParameters);
    }

    @Override
    public boolean applySkipSilenceEnabled(boolean skipSilenceEnabled) {
        return delegate.applySkipSilenceEnabled(skipSilenceEnabled);
    }

    @Override
    public long getMediaDuration(long playoutDuration) {
        return delegate.getMediaDuration(playoutDuration);
    }

    @Override
    public long getSkippedOutputFrameCount() {
        return delegate.getSkippedOutputFrameCount();
    }

    /** Passes on the first {@link #queueEndOfStream()} after a flush and swallows the repeats. */
    private static final class SingleEndOfStream implements AudioProcessor {
        private final AudioProcessor processor;
        private boolean endOfStreamQueued;

        SingleEndOfStream(AudioProcessor processor) {
            this.processor = processor;
        }

        @Override
        public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
            return processor.configure(inputAudioFormat);
        }

        @Override
        public boolean isActive() {
            return processor.isActive();
        }

        @Override
        public void queueInput(ByteBuffer inputBuffer) {
            processor.queueInput(inputBuffer);
        }

        @Override
        public void queueEndOfStream() {
            if (!endOfStreamQueued) {
                endOfStreamQueued = true;
                processor.queueEndOfStream();
            }
        }

        @Override
        public ByteBuffer getOutput() {
            return processor.getOutput();
        }

        @Override
        public boolean isEnded() {
            return processor.isEnded();
        }

        @Override
        public void flush() {
            endOfStreamQueued = false;
            processor.flush();
        }

        @Override
        public void reset() {
            endOfStreamQueued = false;
            processor.reset();
        }
    }
}
