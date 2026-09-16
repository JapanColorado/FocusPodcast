package allen.town.focus_common.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import java.io.File;

import allen.town.focus_common.R;


public class Intents {
    public static boolean launchUrl(Context context, String str) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(str));
            intent.setFlags(268435456);
            return startActivity(context, intent);
        } catch (Exception exception) {
            Timber.e(exception, "launchUrl");
            return false;
        }
    }


    public static void shareText(Context context, String url, String title) {
        Intent intent = new Intent("android.intent.action.SEND");
        intent.setType("text/plain");
        intent.putExtra("android.intent.extra.TEXT", url);

        if (!TextUtils.isEmpty(title)) {
            intent.putExtra("android.intent.extra.TITLE", title);
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)));
    }

    public static void shareTextFile(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType("text/*");
        context.startActivity(Intent.createChooser(intent, ""));

    }


    public static boolean startActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Timber.w("not found activity to handle");
            return false;
        }
        return true;
    }

    public static void startNewTask(Context context, Class cls) {
        Intent intent = new Intent(context, cls);
        intent.setFlags(268468224);
        context.startActivity(intent);
    }


    private Intents() {
    }

    private static boolean contains(String a, String b) {
        if (!TextUtils.isEmpty(a) && !TextUtils.isEmpty(b)) {
            return a.toLowerCase().contains(b);
        } else {
            return false;
        }
    }


}
