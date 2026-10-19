package dev.russell.focuspodcast.core.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.receiver.MediaButtonReceiver;
import dev.russell.focuspodcast.core.util.Converter;
import dev.russell.focuspodcast.core.util.TimeSpeedConverter;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetBig;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetCircle;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetClassic;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetMD;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetMD3;
import dev.russell.focuspodcast.core.widget.appwidgets.AppWidgetText;
import dev.russell.focuspodcast.core.widget.base.BaseAppWidget;
import dev.russell.focuspodcast.model.playback.Playable;
import dev.russell.focuspodcast.playback.base.PlayerStatus;

/**
 * Updates the state of the player widget.
 */
public abstract class WidgetUpdater {
    private static final String TAG = "WidgetUpdater";

    public static class WidgetState {
        public final Playable media;
        public final PlayerStatus status;
        public final int position;
        public final int duration;
        public final float playbackSpeed;

        public WidgetState(Playable media, PlayerStatus status, int position, int duration, float playbackSpeed) {
            this.media = media;
            this.status = status;
            this.position = position;
            this.duration = duration;
            this.playbackSpeed = playbackSpeed;
        }

        public WidgetState(PlayerStatus status) {
            this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f);
        }
    }

    /**
     * Update the widgets with the given parameters. Must be called in a background thread.
     */
    public static void updateWidget(Context context, WidgetState widgetState) {
        if (widgetState == null) {
            Log.e(TAG,"updateWidget widgetState is null");
            return;
        }

        BaseAppWidget appWidgetClassic = AppWidgetClassic.getInstance();
        BaseAppWidget appWidgetMd = AppWidgetMD.getInstance();
        BaseAppWidget appWidgetMd3 = AppWidgetMD3.getInstance();
        BaseAppWidget appWidgetBig = AppWidgetBig.getInstance();
        BaseAppWidget appWidgetCircle = AppWidgetCircle.getInstance();
        BaseAppWidget appWidgetText = AppWidgetText.getInstance();


        appWidgetClassic.start(context, widgetState);
        appWidgetMd.start(context, widgetState);
        appWidgetMd3.start(context, widgetState);
        appWidgetBig.start(context, widgetState);
        appWidgetCircle.start(context, widgetState);
        appWidgetText.start(context, widgetState);

    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    public static int getCellsForSize(int size) {
        int n = 2;
        while (70 * n - 30 < size) {
            ++n;
        }
        return n - 1;
    }

    /**
     * Creates an intent which fakes a mediabutton press.
     */
    public static PendingIntent createMediaButtonIntent(Context context, int eventCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, eventCode);
        Intent startingIntent = new Intent(context, MediaButtonReceiver.class);
        startingIntent.setAction(MediaButtonReceiver.NOTIFY_BUTTON_RECEIVER);
        startingIntent.putExtra(Intent.EXTRA_KEY_EVENT, event);

        return PendingIntent.getBroadcast(context, eventCode, startingIntent,
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    public static String getProgressString(int position, int duration, float speed) {
        if (position < 0 || duration <= 0) {
            return null;
        }
        TimeSpeedConverter converter = new TimeSpeedConverter(speed);
        if (Prefs.shouldShowRemainingTime()) {
            return Converter.getDurationStringLong(converter.convert(position)) + " / -"
                    + Converter.getDurationStringLong(converter.convert(Math.max(0, duration - position)));
        } else {
            return Converter.getDurationStringLong(converter.convert(position)) + " / "
                    + Converter.getDurationStringLong(converter.convert(duration));
        }
    }
}
