package dev.russell.focuspodcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.Log;

import dev.russell.focuspodcast.core.ClientConfig;
import dev.russell.focuspodcast.core.util.NetworkUtils;

public class ConnectivityChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectivityActionRecvr";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (TextUtils.equals(intent.getAction(), ConnectivityManager.CONNECTIVITY_ACTION)) {
            Log.d(TAG, "onReceive");

            ClientConfig.ensureInitialized(context);
            NetworkUtils.networkChangedDetected();
        }
    }
}
