package allen.town.podcast.core.service.playback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import allen.town.podcast.core.R;
import allen.town.podcast.core.util.ui.NotificationUtils;
import allen.town.podcast.model.playback.Playable;
import allen.town.podcast.playback.base.PlayerStatus;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Owns the notifications of {@link PlaybackService}: the single
 * {@link PlaybackServiceNotificationBuilder} the service posts its playback notification with, the
 * background load of that notification's icon (disposed as soon as a newer load supersedes it or
 * the service dies, so that it can never re-post on a dead service), and the separate "streaming
 * over mobile data is not allowed" confirmation notification. The service keeps the synchronized
 * {@code setupNotification} entry point and the foreground-service calls, and asks this class for
 * the notification to show.
 */
class PlaybackServiceNotificationUpdater {
    private static final String TAG = "PlaybackService";

    private final PlaybackService service;
    private final PlaybackServiceNotificationBuilder notificationBuilder;

    /**
     * Used by {@link #setupNotification(Playable)} to load the notification icon off the main
     * thread. Disposed when a newer icon load supersedes it and in {@link #dispose()}, so that a
     * load still in flight cannot re-post the notification on a dead service.
     */
    private Disposable playableIconLoader;

    PlaybackServiceNotificationUpdater(PlaybackService service) {
        this.service = service;
        this.notificationBuilder = new PlaybackServiceNotificationBuilder(service);
    }

    Notification build() {
        return notificationBuilder.build();
    }

    boolean isIconCached() {
        return notificationBuilder.isIconCached();
    }

    Bitmap getCachedIcon() {
        return notificationBuilder.getCachedIcon();
    }

    /**
     * Replaces a still visible "playing" notification with a stopped one, so that the notification
     * does not keep claiming that playback is running after the service is gone.
     */
    void notifyStoppedIfPlaying() {
        if (notificationBuilder.getPlayerStatus() == PlayerStatus.PLAYING) {
            notificationBuilder.setPlayerStatus(PlayerStatus.STOPPED);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(service);
            notificationManager.notify(R.id.notification_playing, notificationBuilder.build());
        }
    }

    void dispose() {
        if (playableIconLoader != null) {
            // otherwise a Glide load still in flight re-posts the notification on a dead service
            playableIconLoader.dispose();
            playableIconLoader = null;
        }
    }

    /**
     * Refreshes the position shown on the already visible notification, used by the once-a-second
     * position observer on the Android versions that do not animate it themselves.
     */
    void updatePositionAndNotify(int position, float speed) {
        notificationBuilder.updatePosition(position, speed);
        NotificationManager notificationManager = (NotificationManager)
                service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        notificationManager.notify(R.id.notification_playing, notificationBuilder.build());
    }

    void cancelStreamingConfirmation() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(service);
        notificationManager.cancel(R.id.notification_streaming_confirmation);
    }

    /**
     * Prepares notification and starts the service in the foreground.
     */
    void setupNotification(final Playable playable) {
        Log.d(TAG, "setupNotification");
        if (playableIconLoader != null) {
            playableIconLoader.dispose();
        }
        if (playable == null || service.mediaPlayer == null) {
            if (!service.stateManager.hasReceivedValidStartCommand()) {
                service.stateManager.stopService();
            }
            return;
        }

        PlayerStatus playerStatus = service.mediaPlayer.getPlayerStatus();
        notificationBuilder.setPlayable(playable);
        notificationBuilder.setMediaSessionToken(service.mediaSessionHolder.getSessionToken());
        notificationBuilder.setPlayerStatus(playerStatus);
        notificationBuilder.updatePosition(service.getCurrentPosition(), service.getCurrentPlaybackSpeed());

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(service);
        notificationManager.notify(R.id.notification_playing, notificationBuilder.build());

        if (!notificationBuilder.isIconCached()) {
            playableIconLoader = Single.fromCallable(() -> {
                        Log.d(TAG, "Loading notification icon");
                        notificationBuilder.loadIcon();
                        return notificationBuilder.build();
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(notification -> {
                        notificationManager.notify(R.id.notification_playing, notification);
                        service.mediaSessionHolder.updateMetadata(playable);
                    }, error -> Log.e(TAG, "Failed to load notification icon", error));
        }
    }

    void displayStreamingNotAllowedNotification(Intent originalIntent) {
        Intent intentAllowThisTime = new Intent(originalIntent);
        intentAllowThisTime.setAction(PlaybackService.EXTRA_ALLOW_STREAM_THIS_TIME);
        intentAllowThisTime.putExtra(PlaybackService.EXTRA_ALLOW_STREAM_THIS_TIME, true);
        PendingIntent pendingIntentAllowThisTime;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntentAllowThisTime = PendingIntent.getForegroundService(service,
                    R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntentAllowThisTime = PendingIntent.getService(service,
                    R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }

        Intent intentAlwaysAllow = new Intent(intentAllowThisTime);
        intentAlwaysAllow.setAction(PlaybackService.EXTRA_ALLOW_STREAM_ALWAYS);
        intentAlwaysAllow.putExtra(PlaybackService.EXTRA_ALLOW_STREAM_ALWAYS, true);
        PendingIntent pendingIntentAlwaysAllow;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntentAlwaysAllow = PendingIntent.getForegroundService(service,
                    R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntentAlwaysAllow = PendingIntent.getService(service,
                    R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service,
                NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setSmallIcon(R.drawable.ic_notification_stream)
                .setContentTitle(service.getString(R.string.confirm_mobile_streaming_notification_title))
                .setContentText(service.getString(R.string.confirm_mobile_streaming_notification_message))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(service.getString(R.string.confirm_mobile_streaming_notification_message)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntentAllowThisTime)
                .addAction(R.drawable.ic_notification_stream,
                        service.getString(R.string.confirm_mobile_streaming_button_once),
                        pendingIntentAllowThisTime)
                .addAction(R.drawable.ic_notification_stream,
                        service.getString(R.string.confirm_mobile_streaming_button_always),
                        pendingIntentAlwaysAllow)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(service);
        notificationManager.notify(R.id.notification_streaming_confirmation, builder.build());
    }
}
