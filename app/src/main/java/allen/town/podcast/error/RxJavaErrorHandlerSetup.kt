package allen.town.podcast.error

import android.util.Log
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins

object RxJavaErrorHandlerSetup {
    private const val TAG = "RxJavaErrorHandler"
    fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { exception: Throwable? ->
            if (exception is UndeliverableException) {
                // Probably just disposed because the fragment was left
                Log.d(TAG, "ignored exception: " + Log.getStackTraceString(exception))
                return@setErrorHandler
            }
            if (exception is OnErrorNotImplementedException) {
                // both variants of this exception only need to be logged
                Log.w(TAG, "ignored exception: OnErrorNotImplementedException")
                return@setErrorHandler
            }

            // Usually, undeliverable exceptions are wrapped in an UndeliverableException.
            // If an undeliverable exception is a NPE (or some others), wrapping does not happen.
            // threads might throw NPEs after disposing because we set controllers to null.
            // Just swallow all exceptions here.

            //hand off to the global exception handler
//            if (BuildConfig.DEBUG) {
            Thread.currentThread().uncaughtExceptionHandler
                .uncaughtException(Thread.currentThread(), exception)
        }
    }
}