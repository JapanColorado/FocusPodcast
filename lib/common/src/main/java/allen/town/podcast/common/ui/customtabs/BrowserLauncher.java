package allen.town.podcast.common.ui.customtabs;

import android.content.Context;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

import allen.town.podcast.common.R;
import allen.town.podcast.common.util.Intents;
import allen.town.podcast.common.util.Timber;
import allen.town.podcast.theme.util.ATHUtil;


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
            // Seen in the wild: android.os.FileUriExposedException: file://player.bilibili.com/player.html?aid=245158683&bvid=BV1Uv41167HC&cid=254301627&page=1 exposed beyond app through Intent.getData()
            // which leaves the page blank and makes every other article load blank stuck at 10%, so catch it
            Timber.e(e, "openCustomTab");
            return false;
        }
        return true;
    }
}
