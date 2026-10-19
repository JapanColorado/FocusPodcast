package dev.russell.focuspodcast.activity

import dev.russell.focuspodcast.common.util.Timber
import dev.russell.focuspodcast.common.util.TopSnackbarUtil.showSnack
import dev.russell.focuspodcast.R
import dev.russell.focuspodcast.storage.db.Db
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Shows the logo while waiting for the main activity to start.
 */
class SplashActivity : AppCompatActivity() {
    private val uiHandler = Handler(Looper.getMainLooper())
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)
        disposable = Completable.create { subscriber: CompletableEmitter ->
            // Trigger schema updates
            Db.getInstance()
            subscriber.onComplete()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    // detekt: finish() may race with the activity already being gone; the
                    // splash screen must never crash the app on its way out.
                    @Suppress("TooGenericExceptionCaught")
                    uiHandler.postDelayed({
                        try {
                            finish()
                        } catch (e: Exception) {
                            // safe to continue: the activity is already gone, which is the
                            // outcome this finish() was after
                            Timber.w(e, "finishing the splash screen failed")
                        }
                    }, 200)
                }) { error: Throwable ->
                Timber.e(error, "init")
                showSnack(this, error.localizedMessage, Toast.LENGTH_LONG)
                finish()
            }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        disposable?.dispose()
        super.onDestroy()
    }
}