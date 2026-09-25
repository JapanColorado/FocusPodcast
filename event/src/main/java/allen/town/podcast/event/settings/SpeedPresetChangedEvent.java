package allen.town.podcast.event.settings;

/**
 * Posted when a stored playback speed changes outside the player: one podcast's own speed (from
 * its Feed Settings or a bulk edit), or the global default in Settings. The playback service
 * copies a podcast's value into the in-memory preferences of the playing episode's feed and
 * re-applies the effective speed.
 */
public class SpeedPresetChangedEvent {
    private final float speed;
    private final long feedId;

    /**
     * @param speed  the podcast's new speed, or {@code FeedPreferences.SPEED_USE_GLOBAL} when it
     *               follows the default again; ignored when {@code feedId} is 0
     * @param feedId the podcast whose speed changed, or 0 when the global default changed
     */
    public SpeedPresetChangedEvent(float speed, long feedId) {
        this.speed = speed;
        this.feedId = feedId;
    }

    public float getSpeed() {
        return speed;
    }

    public long getFeedId() {
        return feedId;
    }

    /** True when the global default speed changed rather than one podcast's. */
    public boolean isGlobal() {
        return feedId == 0;
    }
}
