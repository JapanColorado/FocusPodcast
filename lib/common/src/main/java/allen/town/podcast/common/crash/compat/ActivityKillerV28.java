package allen.town.podcast.common.crash.compat;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.servertransaction.ClientTransaction;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import allen.town.podcast.common.util.Timber;


public class ActivityKillerV28 implements IActivityKiller {


    /**
     * Three ways of getting at the activity token of a P+ {@code ClientTransaction}, tried in
     * order because which one works depends on the OEM's ActivityThread. Each failure is only a
     * reason to try the next one; if all three fail the activity is left alone, which is the
     * best a crash handler can do without taking the process down with it.
     */
    @Override
    public void finishLaunchActivity(Message message) {

        try {
            tryFinish1(message);
            return;
        } catch (Throwable throwable) {
            // The compile-time ClientTransaction shim did not match this ROM; try reflection.
            Timber.w(throwable, "finishLaunchActivity: direct getActivityToken() failed");
        }

        try {
            tryFinish2(message);
            return;
        } catch (Throwable throwable) {
            // No getActivityToken() method either; try the backing field.
            Timber.w(throwable, "finishLaunchActivity: reflective getActivityToken() failed");
        }

        try {
            tryFinish3(message);
            return;
        } catch (Throwable throwable) {
            // Last resort exhausted: log at error level and leave the activity as it is.
            Timber.e(throwable, "could not finish the activity whose lifecycle threw");
        }

    }

    private void tryFinish1(Message message) throws Throwable {
        ClientTransaction clientTransaction = (ClientTransaction) message.obj;
        IBinder binder = clientTransaction.getActivityToken();
        finish(binder);
    }

    private void tryFinish3(Message message) throws Throwable {
        Object clientTransaction = message.obj;
        Field mActivityTokenField = clientTransaction.getClass().getDeclaredField("mActivityToken");
        IBinder binder = (IBinder) mActivityTokenField.get(clientTransaction);
        finish(binder);
    }

    private void tryFinish2(Message message) throws Throwable {
        Object clientTransaction = message.obj;
        Method getActivityTokenMethod = clientTransaction.getClass().getDeclaredMethod("getActivityToken");
        IBinder binder = (IBinder) getActivityTokenMethod.invoke(clientTransaction);
        finish(binder);
    }


    // From API 28 on every lifecycle callback arrives as a single EXECUTE_TRANSACTION message,
    // which CustomCrashHandler routes to finishLaunchActivity, so the three hooks below are
    // never reached on this platform and intentionally do nothing.

    @Override
    public void finishResumeActivity(Message message) {

    }


    @Override
    public void finishPauseActivity(Message message) {

    }

    @Override
    public void finishStopActivity(Message message) {
    }

    private void finish(IBinder binder) throws Exception {
        Method getServiceMethod = ActivityManager.class.getDeclaredMethod("getService");
        Object activityManager = getServiceMethod.invoke(null);

        Method finishActivityMethod = activityManager.getClass().getDeclaredMethod("finishActivity", IBinder.class, int.class, Intent.class, int.class);
        finishActivityMethod.setAccessible(true);
        int DONT_FINISH_TASK_WITH_ACTIVITY = 0;
        finishActivityMethod.invoke(activityManager, binder, Activity.RESULT_CANCELED, null, DONT_FINISH_TASK_WITH_ACTIVITY);

    }
}
