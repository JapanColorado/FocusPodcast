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

    public static PackageInfo getPackageInfo(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            return packageManager.getPackageInfo(context.getPackageName(), 0);
        } catch (Exception e) {
            Log.w(TAG, "getPackageInfo exception " + e.fillInStackTrace());
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

    public static synchronized String getAppName(Context context) {
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
            Log.e("", "", e);
        }
        return null;
    }

}
