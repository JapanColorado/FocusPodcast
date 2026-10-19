package dev.russell.focuspodcast.core.sync;

import android.content.Context;
import android.content.SharedPreferences;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.sync.queue.SynchronizationQueueSink;

/**
 * Manages preferences for accessing gpodder.net service and other sync providers.
 *
 * <p>Holds the {@link SharedPreferences} handed to it by
 * {@link dev.russell.focuspodcast.core.ClientConfig#initialize} rather than a {@code Context}, so that
 * the (many) call sites can stay parameterless without core keeping a static Context around.</p>
 */
public class SynchronizationCredentials {

    private SynchronizationCredentials() {
    }

    private static final String PREF_NAME = "gpodder.net";
    private static final String PREF_USERNAME = "dev.russell.focuspodcast.preferences.gpoddernet.username";
    private static final String PREF_PASSWORD = "dev.russell.focuspodcast.preferences.gpoddernet.password";
    private static final String PREF_DEVICEID = "dev.russell.focuspodcast.preferences.gpoddernet.deviceID";
    private static final String PREF_HOSTNAME = "prefGpodnetHostname";

    private static SharedPreferences preferences;

    /** Called once from {@link dev.russell.focuspodcast.core.ClientConfig#initialize}. */
    public static void init(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPreferences() {
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            throw new IllegalStateException("SynchronizationCredentials.init(context) must be called first");
        }
        return prefs;
    }

    public static String getUsername() {
        return getPreferences().getString(PREF_USERNAME, null);
    }

    public static void setUsername(String username) {
        getPreferences().edit().putString(PREF_USERNAME, username).apply();
    }

    public static String getPassword() {
        return getPreferences().getString(PREF_PASSWORD, null);
    }

    public static void setPassword(String password) {
        getPreferences().edit().putString(PREF_PASSWORD, password).apply();
    }

    public static String getDeviceID() {
        return getPreferences().getString(PREF_DEVICEID, null);
    }

    public static void setDeviceID(String deviceID) {
        getPreferences().edit().putString(PREF_DEVICEID, deviceID).apply();
    }

    public static String getHosturl() {
        return getPreferences().getString(PREF_HOSTNAME, null);
    }

    public static void setHosturl(String value) {
        getPreferences().edit().putString(PREF_HOSTNAME, value).apply();
    }

    public static synchronized void clear(Context context) {
        setUsername(null);
        setPassword(null);
        setDeviceID(null);
        SynchronizationQueueSink.clearQueue(context);
        Prefs.setGpodnetNotificationsEnabled();
    }
}
