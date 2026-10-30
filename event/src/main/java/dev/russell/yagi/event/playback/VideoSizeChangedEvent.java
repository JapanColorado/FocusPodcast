package dev.russell.yagi.event.playback;

/**
 * The playing video's display size changed (first frame of an episode, or a stream switching
 * resolution). The size is in display pixels: non-square pixels are already applied, so the
 * aspect ratio is {@code width / height}.
 */
public class VideoSizeChangedEvent {
    private final int width;
    private final int height;

    public VideoSizeChangedEvent(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
