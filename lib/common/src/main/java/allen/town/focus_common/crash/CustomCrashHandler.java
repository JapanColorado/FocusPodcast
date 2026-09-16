package allen.town.focus_common.crash;


import static allen.town.focus_common.crash.Reflection.getFieldValue;
import static allen.town.focus_common.crash.Reflection.getStaticFieldValue;
import static allen.town.focus_common.crash.Reflection.invokeMethod;
import static allen.town.focus_common.crash.Reflection.setFieldValue;

import android.content.res.Resources;
import android.os.Build;
import android.os.DeadSystemException;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import allen.town.focus_common.crash.compat.ActivityKillerV15_V20;
import allen.town.focus_common.crash.compat.ActivityKillerV21_V23;
import allen.town.focus_common.crash.compat.ActivityKillerV24_V25;
import allen.town.focus_common.crash.compat.ActivityKillerV26;
import allen.town.focus_common.crash.compat.ActivityKillerV28;
import allen.town.focus_common.crash.compat.IActivityKiller;
import allen.town.focus_common.util.Timber;


public class CustomCrashHandler implements Thread.UncaughtExceptionHandler {
    private static IActivityKiller sActivityKiller;
    private static final String[] CRASH_PACKAGE_PREFIXES = {
            "android.view.Choreographer",// An exception during view measure/layout/draw kills the Choreographer, so just kill the app
    };

    private static final String LOADED_APK_GET_ASSETS = "android.app.LoadedApk.getAssets";

    private static final String ASSET_MANAGER_GET_RESOURCE_VALUE = "android.content.res.AssetManager.getResourceValue";

    // The system's default UncaughtException handler
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private static CustomCrashHandler mInstance = new CustomCrashHandler();

    private CustomCrashHandler() {
        // Grab the system's default UncaughtException handler
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        initActivityKiller();
        safeMode();
    }

    public static CustomCrashHandler getInstance() {
        return mInstance;
    }

    /**
     * Main thread exceptions never reach this, see {@link #safeMode()}
     *
     * @param thread
     * @param ex
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // Deliberately swallowed: this handler exists to keep the process alive. Logging is the
        // only fallback available here — rethrowing or delegating to mDefaultHandler would kill
        // the app, which is exactly what installing this handler opted out of.
        reportSwallowed(ex, "uncaught exception on " + thread.getName());
    }

    /**
     * The one place a swallowed crash is recorded. There is no crash reporter in this build, so
     * "handling" an exception here means writing it to the log and carrying on.
     */
    private void reportSwallowed(Throwable ex, String what) {
        Timber.e(ex, "swallowed: %s", what);
    }

    /**
     * Some main thread exceptions leave the UI or core features unusable even when swallowed, so those still have to crash.
     *
     * @param t
     * @return
     */
    private boolean needCrash(final Throwable t) {
        if (null == t) {
            return false;
        }
        for (final StackTraceElement element : t.getStackTrace()) {
            for (final String prefix : CRASH_PACKAGE_PREFIXES) {
                if (element.getClassName().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * https://github.com/android-notes/Cockroach/blob/master/%E5%8E%9F%E7%90%86%E5%88%86%E6%9E%90.md
     */
    private void safeMode() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // Intercept main thread exceptions
                while (true) {
                    try {
                        Looper.loop();// main thread exceptions are thrown from here
                    } catch (final Error ex) {
                        // Errors (OOM, StackOverflow, ...) leave the VM in no state to continue,
                        // so these are handed to the platform handler and do crash the app.
                        mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                    } catch (Throwable ex) {
                        if (needCrash(ex)) {
                            mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                        } else {
                            // The code below comes from https://github.com/didi/booster/blob/master/booster-android-instrument-activity-thread/src/main/java/com/didiglobal/booster/instrument/ActivityThreadCallback.java
                            // Purpose unknown
                            if (ex instanceof NullPointerException) {
                                if (hasStackTraceElement(ex, ASSET_MANAGER_GET_RESOURCE_VALUE, LOADED_APK_GET_ASSETS)) {
                                    mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                                } else {
                                    reportSwallowed(ex, "main thread NPE");
                                }
                            } else if (ex instanceof Resources.NotFoundException) {
                                mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                            } else {
                                final Throwable cause = ex.getCause();
                                if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && isCausedBy(cause, DeadSystemException.class))
                                        || (isCausedBy(cause, NullPointerException.class) && hasStackTraceElement(ex, LOADED_APK_GET_ASSETS))) {
                                    mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                                } else {
                                    reportSwallowed(ex, "main thread exception");
                                }
                            }


                        }
                    }
                }
            }
        });
    }

    private static boolean hasStackTraceElement(final Throwable t, final String... traces) {
        return hasStackTraceElement(t, new HashSet<>(Arrays.asList(traces)));
    }

    private static boolean hasStackTraceElement(final Throwable t, final Set<String> traces) {
        if (null == t || null == traces || traces.isEmpty()) {
            return false;
        }

        for (final StackTraceElement element : t.getStackTrace()) {
            if (traces.contains(element.getClassName() + "." + element.getMethodName())) {
                return true;
            }
        }

        return hasStackTraceElement(t.getCause(), traces);
    }

    @SafeVarargs
    private static boolean isCausedBy(final Throwable t, final Class<? extends Throwable>... causes) {
        return isCausedBy(t, new HashSet<>(Arrays.asList(causes)));
    }

    private static boolean isCausedBy(final Throwable t, final Set<Class<? extends Throwable>> causes) {
        if (null == t) {
            return false;
        }

        if (causes.contains(t.getClass())) {
            return true;
        }

        return isCausedBy(t.getCause(), causes);
    }

    /**
     * Simply ignoring a lifecycle exception leaves a black screen, so instead call
     * ActivityManager.finishActivity to finish the Activity whose lifecycle threw.
     */
    private void initActivityKiller() {
        // How to obtain the ActivityManager, the finishActivity arguments and the token (a binder) all differ per Android version
        if (Build.VERSION.SDK_INT >= 28) {
            sActivityKiller = new ActivityKillerV28();
        } else if (Build.VERSION.SDK_INT >= 26) {
            sActivityKiller = new ActivityKillerV26();
        } else if (Build.VERSION.SDK_INT == 25 || Build.VERSION.SDK_INT == 24) {
            sActivityKiller = new ActivityKillerV24_V25();
        } else if (Build.VERSION.SDK_INT >= 21) {
            sActivityKiller = new ActivityKillerV21_V23();
        } else if (Build.VERSION.SDK_INT >= 17) {
            sActivityKiller = new ActivityKillerV15_V20();
        }
        hook();
    }

    private volatile boolean hooked;

    public void hook() {
        if (hooked) {
            return;
        }

        Object thread = null;
        Class activityThreadClass = null;
        try {
            activityThreadClass = Class.forName("android.app.ActivityThread");
            thread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null);
        } catch (final Throwable t2) {
            // currentActivityThread() is hidden API; fall back to reading the static field it
            // returns. If that fails too, `thread` stays null and the hook is skipped below.
            Timber.w(t2, "ActivityThread.currentActivityThread() is inaccessible");
            try {
                thread = getStaticFieldValue(activityThreadClass, "sCurrentActivityThread");
            } catch (final Throwable t1) {
                // Both routes are gone on this ROM: `thread` stays null and the null check
                // below skips the hook entirely, which is handled and logged there.
                Timber.e(t1, "ActivityThread.sCurrentActivityThread is inaccessible");
            }
        }

        if (null == thread) {
            // Nothing to hook: lifecycle exceptions will crash the app the normal way.
            Timber.w("ActivityThread instance is inaccessible");
            return;
        }

        try {
            final Handler handler = getHandler(thread);
            if (null == handler || !(hooked = setFieldValue(handler, "mCallback", new ActivityThreadCallback(handler)))) {
                Timber.i("Hook ActivityThread.mH.mCallback failed");
            }
        } catch (final Throwable t) {
            // `hooked` stays false, so lifecycle exceptions are not intercepted; the app keeps
            // working, it just crashes the normal way instead of recovering.
            Timber.e(t, "Hook ActivityThread.mH.mCallback failed");
        }
        if (hooked) {
            Timber.i("Hook ActivityThread.mH.mCallback success!");
        }
    }

    private static Handler getHandler(final Object thread) {
        Handler handler;

        if (null != (handler = getFieldValue(thread, "mH"))) {
            return handler;
        }

        if (null != (handler = invokeMethod(thread, "getHandler"))) {
            return handler;
        }

        try {
            if (null != (handler = getFieldValue(thread, Class.forName("android.app.ActivityThread$H")))) {
                return handler;
            }
        } catch (final ClassNotFoundException e) {
            // Returning null is the documented "no handler found" answer; hook() logs and skips.
            Timber.e(e, "Main thread handler is inaccessible");
        }

        return null;
    }


    class ActivityThreadCallback implements Handler.Callback {
        final int LAUNCH_ACTIVITY = 100;
        final int PAUSE_ACTIVITY = 101;
        final int PAUSE_ACTIVITY_FINISHING = 102;
        final int STOP_ACTIVITY_HIDE = 104;
        final int RESUME_ACTIVITY = 107;
        final int DESTROY_ACTIVITY = 109;
        final int NEW_INTENT = 112;
        final int RELAUNCH_ACTIVITY = 126;
        private Handler mhHandler;

        ActivityThreadCallback(Handler handler) {
            mhHandler = handler;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (Build.VERSION.SDK_INT >= 28) {// on Android P every lifecycle callback goes through here
                final int EXECUTE_TRANSACTION = 159;
                if (msg.what == EXECUTE_TRANSACTION) {
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // Swallowed on purpose: finish the activity that threw instead of
                        // crashing, otherwise the user is left staring at a black screen.
                        Timber.e(throwable, "lifecycle transaction threw");
                        sActivityKiller.finishLaunchActivity(msg);
                    }
                    return true;
                }
                return false;
            }
            switch (msg.what) {
                case LAUNCH_ACTIVITY:// startActivity--> activity.attach  activity.onCreate  r.activity!=null  activity.onStart  activity.onResume
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // Swallowed on purpose, see above.
                        Timber.e(throwable, "Activity.onCreate/onStart/onResume threw");
                        sActivityKiller.finishLaunchActivity(msg);
                    }
                    return true;
                case RESUME_ACTIVITY:// returning to an activity: onRestart onStart onResume
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // Swallowed on purpose, see above.
                        Timber.e(throwable, "Activity.onResume threw");
                        sActivityKiller.finishResumeActivity(msg);
                    }
                    return true;
                case PAUSE_ACTIVITY_FINISHING:// back pressed: onPause
                case PAUSE_ACTIVITY:// opening a new screen: the old one runs activity.onPause
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // Swallowed on purpose, see above.
                        Timber.e(throwable, "Activity.onPause threw");
                        sActivityKiller.finishPauseActivity(msg);
                    }
                    return true;
                case STOP_ACTIVITY_HIDE:// opening a new screen: the old one runs activity.onStop
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // Swallowed on purpose, see above.
                        Timber.e(throwable, "Activity.onStop threw");
                        sActivityKiller.finishStopActivity(msg);
                    }
                    return true;
                case DESTROY_ACTIVITY:// closing an activity: onStop onDestroy
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        // The activity is being destroyed anyway, so there is nothing left to
                        // finish; log and let the destroy complete.
                        Timber.e(throwable, "Activity.onStop/onDestroy threw");
                    }
                    return true;
            }
            return false;
        }
    }

    public void setCustomCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }


}
