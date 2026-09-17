package allen.town.podcast.core.service.playback;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.event.playback.PlaybackServiceEvent;
import allen.town.podcast.playback.base.PlayerStatus;
import allen.town.podcast.ui.startintent.LockScreenActivityStarter;

/**
 * Owns the set of {@link BroadcastReceiver}s that {@link PlaybackService} listens with: the
 * Android Auto connection state, headset plug and bluetooth A2dp connection events (including the
 * transient-pause bookkeeping that decides whether playback resumes on reconnect), "audio becoming
 * noisy", the app-internal shutdown / skip / pause-play actions and the screen-off event that
 * opens the full lock screen. The service registers them in {@code onCreate} and unregisters them
 * in {@code onDestroy} through this class, in exactly the order it used to.
 */
class PlaybackServiceReceivers {
    private static final String TAG = "PlaybackService";

    /**
     * Is true if the service was running, but paused due to headphone disconnect
     */
    private static boolean transientPause = false;

    private final PlaybackService service;

    PlaybackServiceReceivers(PlaybackService service) {
        this.service = service;
    }

    // Lint's UnspecifiedRegisterReceiverFlag fires on the pre-Android-13 branch below,
    // where the two-argument registerReceiver is the only overload that exists. The
    // exported flags are passed on Android 13+, which is where they are enforced.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.registerReceiver(autoStateUpdated,
                    new IntentFilter("com.google.android.gms.car.media.STATUS"), Context.RECEIVER_EXPORTED);
            service.registerReceiver(headsetDisconnected,
                    new IntentFilter(Intent.ACTION_HEADSET_PLUG), Context.RECEIVER_EXPORTED);
            // app-internal actions are always sent with setPackage(); do not let other apps drive playback
            service.registerReceiver(shutdownReceiver,
                    new IntentFilter(PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE), Context.RECEIVER_NOT_EXPORTED);
            service.registerReceiver(bluetoothStateUpdated,
                    new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED), Context.RECEIVER_EXPORTED);
            service.registerReceiver(audioBecomingNoisy,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_EXPORTED);
            service.registerReceiver(skipCurrentEpisodeReceiver,
                    new IntentFilter(PlaybackService.ACTION_SKIP_CURRENT_EPISODE), Context.RECEIVER_NOT_EXPORTED);
            service.registerReceiver(pausePlayCurrentEpisodeReceiver,
                    new IntentFilter(PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE), Context.RECEIVER_NOT_EXPORTED);
            service.registerReceiver(lockScreenReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_EXPORTED);
        } else {
            // Pre-Android-13 registerReceiver takes no exported flag, which is what lint's
            // UnspecifiedRegisterReceiverFlag asks for; the flags are passed in the branch above.
            service.registerReceiver(autoStateUpdated,
                    new IntentFilter("com.google.android.gms.car.media.STATUS"));
            service.registerReceiver(headsetDisconnected, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            service.registerReceiver(shutdownReceiver,
                    new IntentFilter(PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE));
            service.registerReceiver(bluetoothStateUpdated,
                    new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED));
            service.registerReceiver(audioBecomingNoisy,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            service.registerReceiver(skipCurrentEpisodeReceiver,
                    new IntentFilter(PlaybackService.ACTION_SKIP_CURRENT_EPISODE));
            service.registerReceiver(pausePlayCurrentEpisodeReceiver,
                    new IntentFilter(PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE));
            service.registerReceiver(lockScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
    }

    void unregister() {
        service.unregisterReceiver(autoStateUpdated);
        service.unregisterReceiver(headsetDisconnected);
        service.unregisterReceiver(shutdownReceiver);
        service.unregisterReceiver(bluetoothStateUpdated);
        service.unregisterReceiver(audioBecomingNoisy);
        service.unregisterReceiver(skipCurrentEpisodeReceiver);
        service.unregisterReceiver(pausePlayCurrentEpisodeReceiver);
        service.unregisterReceiver(lockScreenReceiver);
    }

    private final BroadcastReceiver autoStateUpdated = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("media_connection_status");
            boolean isConnectedToCar = "media_connected".equals(status);
            if (!isConnectedToCar) {
                return;
            } else {
                PlayerStatus playerStatus = service.mediaPlayer.getPlayerStatus();
                if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
                    service.mediaPlayer.resume();
                } else if (playerStatus == PlayerStatus.PREPARING) {
                    service.mediaPlayer.setStartWhenPrepared(!service.mediaPlayer.isStartWhenPrepared());
                } else if (playerStatus == PlayerStatus.INITIALIZED) {
                    service.mediaPlayer.setStartWhenPrepared(true);
                    service.mediaPlayer.prepare();
                }
            }
        }
    };

    /**
     * Pauses playback when the headset is disconnected and the preference is
     * set
     */
    private final BroadcastReceiver headsetDisconnected = new BroadcastReceiver() {
        private static final String TAG = "headsetDisconnected";
        private static final int UNPLUGGED = 0;
        private static final int PLUGGED = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (isInitialStickyBroadcast()) {
                // Don't pause playback after we just started, just because the receiver
                // delivers the current headset state (instead of a change)
                return;
            }

            if (TextUtils.equals(intent.getAction(), Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                Log.d(TAG, "headset plug event " + state);
                if (state != -1) {
                    if (state == UNPLUGGED) {
                        Log.d(TAG, "headset unplugged during playback");
                    } else if (state == PLUGGED) {
                        Log.d(TAG, "headset plugged during playback");
                        unpauseIfPauseOnDisconnect(false);
                    }
                } else {
                    Log.e(TAG, "received invalid ACTION_HEADSET_PLUG intent");
                }
            }
        }
    };

    private final BroadcastReceiver bluetoothStateUpdated = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Log.d(TAG, "received bluetooth connection intent");
                    unpauseIfPauseOnDisconnect(true);
                } else {
                    Log.d(TAG, "received bluetooth connection state " + state);
                }
            }
        }
    };

    private final BroadcastReceiver audioBecomingNoisy = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Sound is about to change, eg. bluetooth -> speaker. Testing showed this broadcast is
            // received twice, so nothing is set to false in this branch.
            Log.d(TAG, "pause playback because bluetooth -> speaker");
            pauseIfPauseOnDisconnect();
        }
    };

    /**
     * Pauses playback if PREF_PAUSE_ON_HEADSET_DISCONNECT was set to true.
     */
    private void pauseIfPauseOnDisconnect() {
        Log.d(TAG, "pauseIfPauseOnDisconnect");
        if (service.mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
            transientPause = true;
        }
        Log.d(TAG, "transientPause playing status " + service.mediaPlayer.getPlayerStatus());
        if (Prefs.isPauseOnHeadsetDisconnect() && !PlaybackService.isCasting()) {
            service.mediaPlayer.pause(!Prefs.isPersistNotify(), false);
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private void unpauseIfPauseOnDisconnect(boolean bluetooth) {
        if (service.mediaPlayer.isAudioChannelInUse()) {
            Log.d(TAG, "do nothing when audio is in use");
            return;
        }
        Log.d(TAG, "bluetooth " + bluetooth + " transientPause " + transientPause);
        if (transientPause) {
            transientPause = false;
            if (!bluetooth && Prefs.isUnpauseOnHeadsetReconnect()) {
                service.mediaPlayer.resume();
            } else if (bluetooth && Prefs.isUnpauseOnBluetoothReconnect()) {
                // let the user know we've started playback again...
                Vibrator v = (Vibrator) service.getApplicationContext()
                        .getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(500);
                }
                service.mediaPlayer.resume();
            }
        }
    }

    private final BroadcastReceiver shutdownReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE)) {
                EventBus.getDefault().post(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN));
                service.stateManager.stopService();
            }
        }

    };

    private final BroadcastReceiver skipCurrentEpisodeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), PlaybackService.ACTION_SKIP_CURRENT_EPISODE)) {
                Log.d(TAG, "SKIP_CURRENT_EPISODE received");
                service.mediaPlayer.skip();
            }
        }
    };

    private final BroadcastReceiver pausePlayCurrentEpisodeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE)) {
                Log.d(TAG, "PAUSE_PLAY_CURRENT_EPISODE received");
                service.mediaPlayer.pause(false, false);
            }
        }
    };

    private final BroadcastReceiver lockScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Prefs.isFullLockScreen() && service.getStatus() == PlayerStatus.PLAYING) {
                new LockScreenActivityStarter(context).start();
            }
        }
    };
}
