package allen.town.focus_common.crash.compat;

import android.app.Activity;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import allen.town.focus_common.util.Timber;

/**
 * Created by wanjian on 2018/5/24.
 * <p>
 */

public class ActivityKillerV15_V20 implements IActivityKiller {


    @Override
    public void finishLaunchActivity(Message message) {
        try {
            Object activityClientRecord = message.obj;

            Field tokenField = activityClientRecord.getClass().getDeclaredField("token");

            tokenField.setAccessible(true);
            IBinder binder = (IBinder) tokenField.get(activityClientRecord);
            finish(binder);
        } catch (Exception e) {
            // Best effort: we are already unwinding a lifecycle crash, so if the private
            // ActivityManager internals are out of reach there is nothing left to try;
            // rethrowing would kill the process this handler exists to keep alive.
            Timber.e(e, "could not finish the activity whose lifecycle threw");
        }
    }


    @Override
    public void finishResumeActivity(Message message) {

        try {
            finish((IBinder) message.obj);
        } catch (Exception e) {
            // Best effort: we are already unwinding a lifecycle crash, so if the private
            // ActivityManager internals are out of reach there is nothing left to try;
            // rethrowing would kill the process this handler exists to keep alive.
            Timber.e(e, "could not finish the activity whose lifecycle threw");
        }
    }


    @Override
    public void finishPauseActivity(Message message) {

        try {
            finish((IBinder) message.obj);
        } catch (Exception e) {
            // Best effort: we are already unwinding a lifecycle crash, so if the private
            // ActivityManager internals are out of reach there is nothing left to try;
            // rethrowing would kill the process this handler exists to keep alive.
            Timber.e(e, "could not finish the activity whose lifecycle threw");
        }
    }

    @Override
    public void finishStopActivity(Message message) {
        try {
            finish((IBinder) message.obj);
        } catch (Exception e) {
            // Best effort: we are already unwinding a lifecycle crash, so if the private
            // ActivityManager internals are out of reach there is nothing left to try;
            // rethrowing would kill the process this handler exists to keep alive.
            Timber.e(e, "could not finish the activity whose lifecycle threw");
        }
    }


    private void finish(IBinder binder) throws Exception {

        Class activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");

        Method getDefaultMethod = activityManagerNativeClass.getDeclaredMethod("getDefault");

        Object activityManager = getDefaultMethod.invoke(null);


        Method finishActivityMethod = activityManager.getClass().getDeclaredMethod("finishActivity", IBinder.class, int.class, Intent.class);
        finishActivityMethod.invoke(activityManager, binder, Activity.RESULT_CANCELED, null);

    }

}
