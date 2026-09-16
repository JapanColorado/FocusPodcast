package allen.town.podcast.activity

import allen.town.focus_common.util.Timber
import allen.town.focus_common.util.TopSnackbarUtil.showSnack
import allen.town.podcast.R
import allen.town.podcast.activity.MainActivity
import allen.town.podcast.storage.db.Db
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Shows the logo while waiting for the main activity to start.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)
        Completable.create { subscriber: CompletableEmitter ->
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
                    Handler().postDelayed({
                        try {
                            finish()
                        } catch (e: Exception) {
                            Timber.w("splash error $e")
                        }
                    }, 200)
                }) { error: Throwable ->
                Timber.e(error, "init")
                showSnack(this, error.localizedMessage, Toast.LENGTH_LONG)
                finish()
            }
    }

    /**
     * The splash screen must block the back button, otherwise the user can quit the app before the ad is shown and billed.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            true
        } else super.onKeyDown(keyCode, event)
    }
}