package allen.town.focus_common.crash;


import static allen.town.focus_common.crash.Reflection.getFieldValue;
import static allen.town.focus_common.crash.Reflection.getStaticFieldValue;
import static allen.town.focus_common.crash.Reflection.invokeMethod;
import static allen.town.focus_common.crash.Reflection.setFieldValue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.DeadSystemException;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import allen.town.focus_common.crash.compat.ActivityKillerV15_V20;
import allen.town.focus_common.crash.compat.ActivityKillerV21_V23;
import allen.town.focus_common.crash.compat.ActivityKillerV24_V25;
import allen.town.focus_common.crash.compat.ActivityKillerV26;
import allen.town.focus_common.crash.compat.ActivityKillerV28;
import allen.town.focus_common.crash.compat.IActivityKiller;
import allen.town.focus_common.util.Timber;


public class CustomCrashHandler implements Thread.UncaughtExceptionHandler {
    public static final String BUGLY_TAG = "BuglyCrashHandler";
    private static IActivityKiller sActivityKiller;
    private static final String[] CRASH_PACKAGE_PREFIXES = {
            "android.view.Choreographer",//view measure layout draw时抛出异常会导致Choreographer挂掉建议直接杀死app
    };

    private static final String LOADED_APK_GET_ASSETS = "android.app.LoadedApk.getAssets";

    private static final String ASSET_MANAGER_GET_RESOURCE_VALUE = "android.content.res.AssetManager.getResourceValue";

    // 系统默认的UncaughtException处理类
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private static CustomCrashHandler mInstance = new CustomCrashHandler();

    private CustomCrashHandler() {
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        initActivityKiller();
        safeMode();
    }

    public static CustomCrashHandler getInstance() {
        return mInstance;
    }

    /**
     * 主线程的异常不会过来,see {@link #safeMode()}
     *
     * @param thread
     * @param ex
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
//        saveInfoToSD(mContext, ex);
        //这里应该是空，但是不知道去掉是否会影响使用，暂时判空
        if (mDefaultHandler != null) {
//            if (thread.getId() == mContext.getMainLooper().getThread().getId()) {
////                //主进程且主线程的异常进行捕获，传给系统处理,实际就是闪退了
//                mDefaultHandler.uncaughtException(thread, ex);
//            } else {
            //其他情况捕获异常不会闪退
            reportToBugly(ex);
//            }
        }
    }

    private void saveInfoToSD(Context context, Throwable ex) {
        StringBuffer sb = obtainPhoneInfo(context);

        sb.append(obtainExceptionInfo(ex));

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            FileOutputStream fos = null;
            try {
                File outFile = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS + "/log.txt");
                fos = new FileOutputStream(outFile, true);
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Exception e) {
                Timber.e(e, "");
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e1) {
                        Timber.e(e1, "");
                    }
                }
            }

        }

    }

    public static StringBuffer obtainPhoneInfo(Context context) {

        HashMap<String, String> map = new HashMap<String, String>();
        PackageManager mPackageManager = context.getPackageManager();
        try {
            PackageInfo mPackageInfo = mPackageManager.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_ACTIVITIES);
            map.put("versionName", mPackageInfo.versionName);
            map.put("versionCode", "" + mPackageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "");
        }

        map.put("time", new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(System
                .currentTimeMillis())));
        map.put("MODEL", "" + Build.MODEL);
        map.put("SDK_INT", "" + Build.VERSION.SDK_INT);
        map.put("PRODUCT", "" + Build.PRODUCT);
        map.put("cpu_ABI", "" + Build.CPU_ABI);
        map.put("cpu_ABI2", "" + Build.CPU_ABI2);


        StringBuffer sb = new StringBuffer();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key).append(" = ").append(value).append("\n");
        }

        return sb;
    }

    private String obtainExceptionInfo(Throwable throwable) {
        StringWriter mStringWriter = new StringWriter();
        PrintWriter mPrintWriter = new PrintWriter(mStringWriter);
        throwable.printStackTrace(mPrintWriter);
        mPrintWriter.close();

        Timber.e(mStringWriter.toString());
        return mStringWriter.toString();
    }

    private void reportToBugly(Throwable ex) {
        Crashlytics.getInstance().recordException(ex);
        //java.net.UnknownHostException不会被记录，如果发现这里堆栈为空，那么就是这个异常，网络不可用
        Timber.e(ex, "report");
    }

    /**
     * 主线程有一些异常捕获了界面或者核心功能也会无法使用,这类的还是需要crash
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
                //主线程异常拦截
                while (true) {
                    try {
                        Looper.loop();//主线程的异常会从这里抛出
                    } catch (final Error ex) {
                        //error一般都是严重错误还是不要捕获
                        mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                    } catch (Throwable ex) {
                        if (needCrash(ex)) {
                            mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                        } else {
                            //下面这些代码来源于https://github.com/didi/booster/blob/master/booster-android-instrument-activity-thread/src/main/java/com/didiglobal/booster/instrument/ActivityThreadCallback.java
                            //作用未知
                            if (ex instanceof NullPointerException) {
                                if (hasStackTraceElement(ex, ASSET_MANAGER_GET_RESOURCE_VALUE, LOADED_APK_GET_ASSETS)) {
                                    mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                                } else {
                                    reportToBugly(ex);
                                }
                            } else if (ex instanceof Resources.NotFoundException) {
                                mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                            } else {
                                final Throwable cause = ex.getCause();
                                if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && isCausedBy(cause, DeadSystemException.class))
                                        || (isCausedBy(cause, NullPointerException.class) && hasStackTraceElement(ex, LOADED_APK_GET_ASSETS))) {
                                    mDefaultHandler.uncaughtException(Looper.getMainLooper().getThread(), ex);
                                } else {
                                    reportToBugly(ex);
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
     * 直接忽略生命周期的异常的话会导致黑屏，目前
     * 会调用ActivityManager的finishActivity结束掉生命周期抛出异常的Activity
     */
    private void initActivityKiller() {
        //各版本android的ActivityManager获取方式，finishActivity的参数，token(binder对象)的获取不一样
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
            Timber.e(t2, "ActivityThread.sCurrentActivityThread is inaccessible");
            try {
                thread = getStaticFieldValue(activityThreadClass, "sCurrentActivityThread");
            } catch (final Throwable t1) {
                Timber.e(t1, "ActivityThread.sCurrentActivityThread is inaccessible");
            }
        }

        if (null == thread) {
            Timber.w("ActivityThread instance is inaccessible");
            return;
        }

        try {
            final Handler handler = getHandler(thread);
            if (null == handler || !(hooked = setFieldValue(handler, "mCallback", new ActivityThreadCallback(handler)))) {
                Timber.i("Hook ActivityThread.mH.mCallback failed");
            }
        } catch (final Throwable t) {
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
            if (Build.VERSION.SDK_INT >= 28) {//android P 生命周期全部走这
                final int EXECUTE_TRANSACTION = 159;
                if (msg.what == EXECUTE_TRANSACTION) {
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        Timber.e(throwable, "");
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
                        Timber.e(throwable, "");
                        sActivityKiller.finishLaunchActivity(msg);
                    }
                    return true;
                case RESUME_ACTIVITY://回到activity onRestart onStart onResume
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        Timber.e(throwable, "");
                        sActivityKiller.finishResumeActivity(msg);
                    }
                    return true;
                case PAUSE_ACTIVITY_FINISHING://按返回键 onPause
                case PAUSE_ACTIVITY://开启新页面时，旧页面执行 activity.onPause
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        Timber.e(throwable, "");
                        sActivityKiller.finishPauseActivity(msg);
                    }
                    return true;
                case STOP_ACTIVITY_HIDE://开启新页面时，旧页面执行 activity.onStop
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        Timber.e(throwable, "");
                        sActivityKiller.finishStopActivity(msg);
                    }
                    return true;
                case DESTROY_ACTIVITY:// 关闭activity onStop  onDestroy
                    try {
                        mhHandler.handleMessage(msg);
                    } catch (Throwable throwable) {
                        Timber.e(throwable, "");
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
