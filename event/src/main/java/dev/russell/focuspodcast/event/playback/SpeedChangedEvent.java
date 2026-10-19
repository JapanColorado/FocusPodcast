package dev.russell.focuspodcast.event.playback;

public class SpeedChangedEvent {
    private final float newSpeed;

    public SpeedChangedEvent(float newSpeed) {
        this.newSpeed = newSpeed;
    }

    public float getNewSpeed() {
        return newSpeed;
    }
}
