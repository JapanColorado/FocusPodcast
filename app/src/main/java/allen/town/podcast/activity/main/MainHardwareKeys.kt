package allen.town.podcast.activity.main

import allen.town.podcast.core.receiver.MediaButtonReceiver
import allen.town.podcast.core.service.playback.PlaybackService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.widget.EditText
import androidx.core.content.ContextCompat
import org.greenrobot.eventbus.EventBus

/**
 * Hardware keyboard support for MainActivity: it broadcasts every key-up on the EventBus (so
 * fragments can react to their own shortcuts), adjusts or mutes the music stream for the
 * volume-ish keys, and forwards the transport keys to [PlaybackService] as media button key codes.
 * Keys typed into an [EditText] are left alone. [onKeyUp] answers null when the event was not
 * consumed, which is the activity's signal to fall through to `super.onKeyUp`.
 */
internal class MainHardwareKeys(private val activity: Activity) {

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean? {
        val currentFocus = activity.currentFocus
        if (currentFocus is EditText) {
            return null
        }
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventBus.getDefault().post(event)
        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
            KeyEvent.KEYCODE_M -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI
                )
                return true
            }
        }
        if (customKeyCode != null) {
            val intent = Intent(activity, PlaybackService::class.java)
            intent.putExtra(MediaButtonReceiver.EXTRA_KEYCODE, customKeyCode)
            ContextCompat.startForegroundService(activity, intent)
            return true
        }
        return null
    }
}
