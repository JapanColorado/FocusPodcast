package allen.town.focus_common.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;



/**
 * Created by Administrator on 2017/1/3.
 */

public class PackageUtils {

    public static final String TAG = "PackageUtils";

    /** Guards {@link #getAppName(Context)}; private so no outside code can hold this lock. */
    private static final Object APP_NAME_LOCK = new Object();

    public static PackageInfo getPackageInfo(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            return packageManager.getPackageInfo(context.getPackageName(), 0);
        } catch (Exception e) {
            // Our own package always resolves, so this only fires while the app is being
            // uninstalled. null is the documented answer; callers already null-check.
            Log.w(TAG, "getPackageInfo failed", e);
            return null;
        }
    }

    private static String packageName;

    public static String getPackageName(Context context) {
        if (!TextUtils.isEmpty(packageName)) {
            return packageName;
        }
        PackageInfo packageInfo = getPackageInfo(context);
        if (packageInfo != null) {
            return packageName = packageInfo.packageName;
        } else {
            return null;
        }
    }

    /**
     * Get the application name.
     */
    private static String appName;

    public static String getAppName(Context context) {
        synchronized (APP_NAME_LOCK) {
            try {
                if (!TextUtils.isEmpty(appName)) {
                    return appName;
                }
                PackageManager packageManager = context.getPackageManager();
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(), 0);
                int labelRes = packageInfo.applicationInfo.labelRes;
                return appName = context.getResources().getString(labelRes);
            } catch (Exception e) {
                // Same as getPackageInfo: only reachable mid-uninstall. null is the documented
                // answer for "name unavailable"; the cache stays empty so a later call retries.
                Log.w(TAG, "getAppName failed", e);
            }
            return null;
        }
    }

}
