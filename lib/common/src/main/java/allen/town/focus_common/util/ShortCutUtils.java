package allen.town.focus_common.util;


import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class ShortCutUtils {
    public static void install(Context context, String str, int i, Intent intent, boolean z) {
        Intent intent2 = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        intent2.putExtra("android.intent.extra.shortcut.NAME", str);
        intent2.putExtra("duplicate", z);
        intent2.putExtra("android.intent.extra.shortcut.ICON_RESOURCE", Intent.ShortcutIconResource.fromContext(context, i));
        intent.setAction("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.LAUNCHER");
        intent2.putExtra("android.intent.extra.shortcut.INTENT", intent);
        context.sendBroadcast(intent2);
    }

    /**
     * 创建shortcut快捷方式
     * @param context
     * @param str
     * @param bitmap
     * @param intent
     * @param z
     */
    public static void install(Context context, String str, Bitmap bitmap, Intent intent, boolean z) {
        if (Build.VERSION.SDK_INT >= 26) {
            installO(context, str, bitmap, intent);
            return;
        }
        Intent intent2 = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        intent2.putExtra(Intent.EXTRA_SHORTCUT_NAME, str);
        intent2.putExtra("duplicate", z);
        intent2.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
        intent.setAction("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.LAUNCHER");
        intent2.putExtra("android.intent.extra.shortcut.INTENT", intent);
        context.sendBroadcast(intent2);
    }

    /**
     * 创建shortcut快捷方式
     * @param context
     * @param str
     * @param drawable
     * @param intent
     * @param z
     */
    public static void install(Context context, String str, Drawable drawable, Intent intent, boolean z) {
        Bitmap bitmap = ImageUtils.drawableToBitmap(drawable);
        install(context, str, bitmap, intent, z);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void installO(Context context, String str, Bitmap bitmap, Intent intent) {
        ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        if (shortcutManager.isRequestPinShortcutSupported()) {
            intent.setAction("android.intent.action.VIEW");
            shortcutManager.requestPinShortcut(new ShortcutInfo.Builder(context, System.currentTimeMillis() + "").setIcon(Icon.createWithBitmap(bitmap)).setShortLabel(str).setIntent(intent).build(), null);
        }
    }

    public static void delete(Context context, String str, Intent intent, boolean z) {
        Intent intent2 = new Intent("com.android.launcher.action.UNINSTALL_SHORTCUT");
        intent2.putExtra("android.intent.extra.shortcut.NAME", str);
        intent2.putExtra("duplicate", z);
        intent2.putExtra("android.intent.extra.shortcut.INTENT", intent);
        context.sendBroadcast(intent2);
    }
}
