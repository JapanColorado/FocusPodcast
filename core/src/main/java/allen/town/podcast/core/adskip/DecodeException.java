package allen.town.podcast.core.adskip;

/**
 * Thrown by {@link PcmDecoder} when a file cannot be turned into PCM: missing or unreadable file,
 * no audio track, a codec the device does not provide, a corrupt stream, or a decoder that stops
 * making progress.
 *
 * <p>Checked on purpose. Ad analysis is best-effort background work and every caller has to decide
 * what to do when a particular episode cannot be analysed; a checked exception makes that decision
 * visible rather than letting a worker crash.
 */
public class DecodeException extends Exception {

    private static final long serialVersionUID = 1L;

    public DecodeException(String message) {
        super(message);
    }

    public DecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
