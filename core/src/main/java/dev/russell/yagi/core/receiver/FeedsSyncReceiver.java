package dev.russell.yagi.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dev.russell.yagi.core.ClientConfig;
import dev.russell.yagi.core.util.download.AutoUpdateManager;

/**
 * Refreshes all feeds when it receives an intent
 */
public class FeedsSyncReceiver extends BroadcastReceiver {

    private static final String TAG = "FeedUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        ClientConfig.ensureInitialized(context);

        AutoUpdateManager.runOnce(context);
    }

}
