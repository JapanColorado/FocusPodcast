package dev.russell.yagi.parser.media.mp4;

/** Thrown when an MP4 file's box structure is corrupt. */
public class Mp4ReaderException extends Exception {
    private static final long serialVersionUID = 1L;

    public Mp4ReaderException(String message) {
        super(message);
    }
}
