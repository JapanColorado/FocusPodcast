package allen.town.focus_common.crash;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
public class Crashlytics {
    private static final Crashlytics sInstance = new Crashlytics();
    public static Crashlytics getInstance() {
        return  sInstance;
    }

    public void recordException(Throwable ex) {
        FirebaseCrashlytics.getInstance().recordException(ex);
    }

    public void setCrashlyticsCollectionEnabled(boolean enabled) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled);
    }

    public void log(String message) {
        FirebaseCrashlytics.getInstance().log(message);
    }
}
