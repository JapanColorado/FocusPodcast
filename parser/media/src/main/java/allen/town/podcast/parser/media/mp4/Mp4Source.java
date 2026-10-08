package allen.town.podcast.parser.media.mp4;

import java.io.IOException;

/**
 * Byte source for {@link Mp4ChapterReader}. Implementations may be fully seekable
 * ({@link FileChannelMp4Source}) or forward-only ({@link StreamMp4Source}); a forward-only
 * source throws an {@link IOException} from {@link #seek} when asked to go backwards.
 */
public interface Mp4Source {
    /** Total length in bytes, or -1 if unknown. */
    long length();

    /** Current read position. */
    long position();

    /** Moves the read position to {@code position}. */
    void seek(long position) throws IOException;

    /** Reads exactly {@code len} bytes, throwing {@link java.io.EOFException} at end of data. */
    void readFully(byte[] buffer, int offset, int len) throws IOException;
}
