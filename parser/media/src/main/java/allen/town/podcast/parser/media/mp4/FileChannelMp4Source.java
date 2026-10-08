package allen.town.podcast.parser.media.mp4;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Random-access {@link Mp4Source} over a {@link FileChannel}, e.g. from a
 * {@code RandomAccessFile} or the {@code FileInputStream} of a {@code ParcelFileDescriptor}.
 * Uses positional reads, so the channel's own position is left untouched. The caller owns
 * (and closes) the channel.
 */
public final class FileChannelMp4Source implements Mp4Source {
    private final FileChannel channel;
    private final long length;
    private long position;

    public FileChannelMp4Source(FileChannel channel) throws IOException {
        this.channel = channel;
        this.length = channel.size();
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
        if (newPosition < 0) {
            throw new IOException("negative seek position " + newPosition);
        }
        position = newPosition;
    }

    @Override
    public void readFully(byte[] buffer, int offset, int len) throws IOException {
        ByteBuffer target = ByteBuffer.wrap(buffer, offset, len);
        while (target.hasRemaining()) {
            int read = channel.read(target, position);
            if (read < 0) {
                throw new EOFException("end of file at " + position);
            }
            position += read;
        }
    }
}
