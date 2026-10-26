package dev.russell.focuspodcast.core.service.download.mp4;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One box (QuickTime "atom") of an MP4 file held in memory: where it starts in its byte array,
 * how long its header is and where it ends. The parsing is strict: a size that runs past its
 * parent or below its own header is an {@link UnsupportedMp4Exception}, never a guess.
 */
final class Mp4Box {
    final String type;
    /** Offset of the box's first header byte in the array it was read from. */
    final int start;
    final int headerLength;
    /** Offset one past the box's last byte. */
    final int end;

    private Mp4Box(String type, int start, int headerLength, int end) {
        this.type = type;
        this.start = start;
        this.headerLength = headerLength;
        this.end = end;
    }

    int contentStart() {
        return start + headerLength;
    }

    int size() {
        return end - start;
    }

    /** Reads the box that starts at {@code offset}; it must end at or before {@code limit}. */
    @NonNull
    static Mp4Box read(@NonNull byte[] data, int offset, int limit) throws UnsupportedMp4Exception {
        if (limit - offset < 8) {
            throw new UnsupportedMp4Exception("Truncated box header at " + offset);
        }
        long size = readUInt32(data, offset);
        String type = readType(data, offset + 4);
        int headerLength = 8;
        if (size == 1) {
            if (limit - offset < 16) {
                throw new UnsupportedMp4Exception("Truncated large box header at " + offset);
            }
            size = readUInt64(data, offset + 8);
            headerLength = 16;
        } else if (size == 0) {
            size = limit - offset;
        }
        if (size < headerLength || size > limit - offset) {
            throw new UnsupportedMp4Exception("Box '" + type + "' at " + offset + " has bad size " + size);
        }
        return new Mp4Box(type, offset, headerLength, offset + (int) size);
    }

    /** The boxes directly inside this one, starting {@code skip} bytes into its content. */
    @NonNull
    List<Mp4Box> children(@NonNull byte[] data, int skip) throws UnsupportedMp4Exception {
        List<Mp4Box> children = new ArrayList<>();
        int offset = contentStart() + skip;
        while (offset < end) {
            Mp4Box child = read(data, offset, end);
            children.add(child);
            offset = child.end;
        }
        return children;
    }

    @NonNull
    List<Mp4Box> children(@NonNull byte[] data) throws UnsupportedMp4Exception {
        return children(data, 0);
    }

    @Nullable
    Mp4Box child(@NonNull byte[] data, @NonNull String childType) throws UnsupportedMp4Exception {
        for (Mp4Box child : children(data)) {
            if (child.type.equals(childType)) {
                return child;
            }
        }
        return null;
    }

    @NonNull
    Mp4Box requireChild(@NonNull byte[] data, @NonNull String childType) throws UnsupportedMp4Exception {
        Mp4Box child = child(data, childType);
        if (child == null) {
            throw new UnsupportedMp4Exception("'" + type + "' has no '" + childType + "'");
        }
        return child;
    }

    static long readUInt32(@NonNull byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8) | (data[offset + 3] & 0xFFL);
    }

    static long readUInt64(@NonNull byte[] data, int offset) {
        return (readUInt32(data, offset) << 32) | readUInt32(data, offset + 4);
    }

    static void writeUInt32(@NonNull byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    static void writeUInt64(@NonNull byte[] data, int offset, long value) {
        writeUInt32(data, offset, value >>> 32);
        writeUInt32(data, offset + 4, value);
    }

    @NonNull
    static String readType(@NonNull byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.ISO_8859_1);
    }
}
