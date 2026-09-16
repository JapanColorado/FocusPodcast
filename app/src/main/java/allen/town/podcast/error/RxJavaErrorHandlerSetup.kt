package allen.town.podcast.error

import allen.town.podcast.BuildConfig
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException

/**
 * Global handler for RxJava errors that could not be delivered to a subscriber, either because the
 * chain was disposed first or because the subscriber has no `onError` at all.
 *
 * Exceptions that merely describe a runtime condition of a chain that nobody is listening to any
 * more (I/O, interrupts) are swallowed. Everything else is a programming error and is surfaced:
 * loudly in debug builds, as an error log in release builds.
 */
object RxJavaErrorHandlerSetup {
    private const val TAG = "RxJavaErrorHandler"

    @JvmStatic
    fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { exception: Throwable ->
            when (val cause = unwrap(exception)) {
                is IOException, is SocketException ->
                    // A network/disk chain was disposed (fragment left, service stopped) while a
                    // request was still in flight. There is no subscriber left that could react to
                    // it and the underlying resource is already being torn down, so swallowing it
                    // is the documented RxJava2 behaviour for this case.
                    Log.d(TAG, "Undeliverable I/O error after dispose: ${cause.javaClass.simpleName}", cause)

                is InterruptedException ->
                    // A blocking source was interrupted because its executor was shut down.
                    // Expected during teardown; nothing to report.
                    Log.d(TAG, "Undeliverable interruption after dispose", cause)

                else -> reportProgrammingError(exception, cause)
            }
        }
    }

    /**
     * [UndeliverableException] and [OnErrorNotImplementedException] are RxJava wrappers; the
     * interesting exception is the one they carry.
     */
    private fun unwrap(exception: Throwable): Throwable {
        if (exception is UndeliverableException || exception is OnErrorNotImplementedException) {
            return exception.cause ?: exception
        }
        return exception
    }

    /**
     * A missing `onError` handler, or a bug inside an operator, is a defect rather than a runtime
     * condition. Crash on it in debug builds so it is found during development, and only log it in
     * release builds so that users are not shown a crash for something nobody was listening to.
     */
    private fun reportProgrammingError(original: Throwable, cause: Throwable) {
        if (BuildConfig.DEBUG) {
            // The error may reach us on any scheduler thread; rethrowing there would be caught by
            // that pool's uncaught handler and may be silently dropped. Post to the main thread so
            // the app's normal crash reporting sees it.
            Handler(Looper.getMainLooper()).post { throw asRuntime(original) }
        } else {
            Log.e(TAG, "Undeliverable RxJava error", cause)
        }
    }

    private fun asRuntime(throwable: Throwable): RuntimeException =
        throwable as? RuntimeException ?: RuntimeException(throwable)
}
