package dev.russell.yagi.core.service.download.mp4;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The header of a box at the top level of a remote MP4 file, read from its first bytes. Only the
 * header is needed to find the next box, so the downloader can walk past a multi-gigabyte
 * {@code mdat} with one small range request per box.
 */
public final class Mp4TopLevelBox {
    /** Enough bytes for any box header, including the 64-bit size form. */
    public static final int MAX_HEADER_LENGTH = 16;

    /**
     * Box types that may appear at the top level of an MP4 or QuickTime file. Anything else at
     * offset 0 means the file is not MP4 at all (WebM, MPEG-TS, an HTML error page, ...).
     */
    private static final Set<String> TOP_LEVEL_TYPES = new HashSet<>(Arrays.asList(
            "ftyp", "moov", "mdat", "free", "skip", "wide", "uuid", "pdin", "meta",
            "moof", "mfra", "styp", "sidx", "ssix", "prft", "emsg", "pnot", "junk"));

    public final String type;
    public final long offset;
    public final long size;
    public final int headerLength;

    private Mp4TopLevelBox(String type, long offset, long size, int headerLength) {
        this.type = type;
        this.offset = offset;
        this.size = size;
        this.headerLength = headerLength;
    }

    public long end() {
        return offset + size;
    }

    /**
     * Parses the header in {@code header} (at least 8 bytes, 16 if the box uses the 64-bit size
     * form) of the box that starts at {@code offset} in a file of {@code fileSize} bytes.
     */
    @NonNull
    public static Mp4TopLevelBox parse(@NonNull byte[] header, int length, long offset, long fileSize)
            throws UnsupportedMp4Exception {
        if (length < 8) {
            throw new UnsupportedMp4Exception("Truncated box header at " + offset);
        }
        String type = Mp4Box.readType(header, 4);
        if (!TOP_LEVEL_TYPES.contains(type)) {
            throw new UnsupportedMp4Exception("Not an MP4 box at " + offset + ": '" + type + "'");
        }
        long size = Mp4Box.readUInt32(header, 0);
        int headerLength = 8;
        if (size == 1) {
            if (length < 16) {
                throw new UnsupportedMp4Exception("Truncated large box header at " + offset);
            }
            size = Mp4Box.readUInt64(header, 8);
            headerLength = 16;
        } else if (size == 0) {
            size = fileSize - offset;
        }
        if (size < headerLength || size > fileSize - offset) {
            throw new UnsupportedMp4Exception("Box '" + type + "' at " + offset + " has bad size " + size);
        }
        return new Mp4TopLevelBox(type, offset, size, headerLength);
    }
}
