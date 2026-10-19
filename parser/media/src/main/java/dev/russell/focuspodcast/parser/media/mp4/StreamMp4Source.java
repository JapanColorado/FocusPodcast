package dev.russell.focuspodcast.parser.media.mp4;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Forward-only {@link Mp4Source} over an {@link InputStream}. Seeking forward skips bytes,
 * up to a total skip budget (so a network stream is not downloaded in full just to reach a
 * moov box at the end); seeking backwards fails with an {@link IOException}. The caller owns
 * (and closes) the stream.
 */
public final class StreamMp4Source implements Mp4Source {
    private final InputStream in;
    private final long length;
    private final long maxSkip;
    private long position;
    private long skipped;

    /** Unknown length, unlimited skipping. */
    public StreamMp4Source(InputStream in) {
        this(in, -1, Long.MAX_VALUE);
    }

    /**
     * @param length  total length if known, else -1
     * @param maxSkip the most bytes {@link #seek} may skip over in total
     */
    public StreamMp4Source(InputStream in, long length, long maxSkip) {
        this.in = in;
        this.length = length;
        this.maxSkip = maxSkip;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public void seek(long newPosition) throws IOException {
        if (newPosition < position) {
            throw new IOException("cannot seek backwards from " + position + " to " + newPosition);
        }
        long remaining = newPosition - position;
        if (remaining > maxSkip - skipped) {
            throw new IOException("skip budget of " + maxSkip + " bytes exceeded");
        }
        while (remaining > 0) {
            long n = in.skip(remaining);
            if (n <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("end of stream at " + position);
                }
                n = 1;
            }
            remaining -= n;
            position += n;
            skipped += n;
        }
    }

    @Override
    public void readFully(byte[] buffer, int offset, int len) throws IOException {
        int done = 0;
        while (done < len) {
            int n = in.read(buffer, offset + done, len - done);
            if (n < 0) {
                throw new EOFException("end of stream at " + position);
            }
            done += n;
            position += n;
        }
    }
}
