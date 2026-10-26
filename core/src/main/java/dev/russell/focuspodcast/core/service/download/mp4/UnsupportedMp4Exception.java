package dev.russell.focuspodcast.core.service.download.mp4;

/**
 * The file is not an MP4/QuickTime file whose audio track can be cut out of it on its own:
 * a different container, a fragmented MP4, no audio track, or a structure this parser rejects.
 */
public class UnsupportedMp4Exception extends Exception {
    public UnsupportedMp4Exception(String message) {
        super(message);
    }
}
