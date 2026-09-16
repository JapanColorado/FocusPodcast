package allen.town.focus_common.ui.customtabs;

import android.content.Context;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

import allen.town.focus_common.R;
import allen.town.focus_common.util.Intents;
import allen.town.focus_common.util.Timber;
import code.name.monkey.appthemehelper.util.ATHUtil;


public class BrowserLauncher {
    private BrowserLauncher() {
    }

    public static void openUrl(Context context, String str) {
        if (!launchCustomTabs(context, str)) {
            launchExternalBrowser(context, str);
        }
    }

    private static boolean launchCustomTabs(Context context, String str) {
        return openCustomTab(context, new CustomTabsIntent.Builder(null)
                .setToolbarColor(ATHUtil.resolveColor(context, androidx.appcompat.R.attr.colorPrimary)).addDefaultShareMenuItem().setShowTitle(true).enableUrlBarHiding().build(), Uri.parse(str));
    }

    private static void launchExternalBrowser(Context context, String str) {
        Intents.launchUrl(context, str);
    }

    private static boolean openCustomTab(Context context, CustomTabsIntent customTabsIntent, Uri uri) {
        try {
            String packageNameToUse = CustomTabsHelper.getPackageNameToUse(context);
            if (packageNameToUse == null) {
                return false;
            }
            customTabsIntent.intent.setPackage(packageNameToUse);
            customTabsIntent.launchUrl(context, uri);
        } catch (Exception e) {
            //https://blog.csdn.net/baodinglaolang/article/details/52192414
            //发现这个问题android.os.FileUriExposedException: file://player.bilibili.com/player.html?aid=245158683&bvid=BV1Uv41167HC&cid=254301627&page=1 exposed beyond app through Intent.getData()
            //导致打开网页白屏并且打开其他的文章也是白屏加载进度总是10%所以捕获异常
            Timber.e(e, "openCustomTab");
            return false;
        }
        return true;
    }
}
