package dev.russell.focuspodcast.event.settings;

/**
 * Posted when the audio effects (skip silence, stereo to mono, Clear voice, volume leveling) change, either
 * the global defaults from Settings or one podcast's own values. The playback service re-applies
 * the effects of what is playing and, for a podcast event, copies the values into the in-memory
 * preferences of the playing episode's feed so a later re-prepare does not use stale ones.
 */
public class AudioEffectsChangedEvent {
    /** 0 when the global defaults changed rather than one podcast's settings. */
    private final long feedId;
    private final boolean useFeedEffect;
    private final boolean skipSilence;
    private final boolean mono;
    private final int clearVoiceLevel;
    private final int volumeLeveling;

    private AudioEffectsChangedEvent(long feedId, boolean useFeedEffect, boolean skipSilence,
                                     boolean mono, int clearVoiceLevel, int volumeLeveling) {
        this.feedId = feedId;
        this.useFeedEffect = useFeedEffect;
        this.skipSilence = skipSilence;
        this.mono = mono;
        this.clearVoiceLevel = clearVoiceLevel;
        this.volumeLeveling = volumeLeveling;
    }

    /** The global defaults in Settings changed; podcasts with their own effects are unaffected. */
    public static AudioEffectsChangedEvent global() {
        return new AudioEffectsChangedEvent(0, false, false, false, 0, 0);
    }

    /**
     * One podcast's effects changed. {@code useFeedEffect} false means the podcast follows the
     * global defaults again, and the other values are then ignored.
     */
    public static AudioEffectsChangedEvent forFeed(long feedId, boolean useFeedEffect,
                                                   boolean skipSilence, boolean mono, int clearVoiceLevel,
                                                   int volumeLeveling) {
        return new AudioEffectsChangedEvent(feedId, useFeedEffect, skipSilence, mono, clearVoiceLevel,
                volumeLeveling);
    }

    public boolean isGlobal() {
        return feedId == 0;
    }

    public long getFeedId() {
        return feedId;
    }

    public boolean isUseFeedEffect() {
        return useFeedEffect;
    }

    public boolean isSkipSilence() {
        return skipSilence;
    }

    public boolean isMono() {
        return mono;
    }

    public int getClearVoiceLevel() {
        return clearVoiceLevel;
    }

    public int getVolumeLeveling() {
        return volumeLeveling;
    }
}
