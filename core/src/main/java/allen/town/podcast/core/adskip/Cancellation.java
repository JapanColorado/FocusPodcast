package allen.town.podcast.core.adskip;

/**
 * Cooperative cancellation hook for the ad analysis.
 *
 * <p>Analysing a three hour episode takes minutes of CPU, so every long loop in this package polls
 * this between chunks and returns early when it reports true. A WorkManager worker passes
 * {@code () -> isStopped()}.
 *
 * <p>This is deliberately not {@code java.util.function.BooleanSupplier}: that type only exists from
 * API 24 and the app's minSdk is 21 with no core library desugaring configured. It is a functional
 * interface, so callers still write it as a lambda.
 */
public interface Cancellation {

    /** A hook that never cancels. */
    Cancellation NEVER = new Cancellation() {
        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    boolean isCancelled();
}
