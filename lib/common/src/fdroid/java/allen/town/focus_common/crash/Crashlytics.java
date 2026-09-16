package allen.town.focus_common.crash;

public class Crashlytics {
    private static final Crashlytics sInstance = new Crashlytics();
    public static Crashlytics getInstance() {
        return sInstance;
    }

    public void recordException(Throwable ex) {
    }

    public void setCrashlyticsCollectionEnabled(boolean enabled) {
    }

    public void log(String message) {
    }
}
