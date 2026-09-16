package allen.town.focus_common.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;

/**
 * 这个版本就不需要rate了
 */
public class RatingDialog {

    private RatingDialog(){}

    private static final String TAG = RatingDialog.class.getSimpleName();
    private static final int AFTER_DAYS = 7;

    private static WeakReference<Context> mContext;
    private static SharedPreferences mPreferences;
    private static Dialog mDialog;

    private static final String PREFS_NAME = "RatingPrefs";
    private static final String KEY_RATED = "KEY_WAS_RATED";
    private static final String KEY_FIRST_START_DATE = "KEY_FIRST_HIT_DATE";

    public static void init(Context context) {
        mContext = new WeakReference<>(context);
        mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        long firstDate = mPreferences.getLong(KEY_FIRST_START_DATE, 0);
        if (firstDate == 0) {
            resetStartDate();
        }
    }

    public static void check() {
    }

    private static void rateNow() {
    }

    private static boolean rated() {
        return mPreferences.getBoolean(KEY_RATED, false);
    }

    @VisibleForTesting
    public static void saveRated() {
    }

    private static void resetStartDate() {
    }

    private static boolean shouldShow() {
        return false;
    }

}