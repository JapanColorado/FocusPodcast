package allen.town.focus_common.views;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.widget.ProgressBar;

import java.lang.reflect.Field;

import allen.town.focus_common.util.Timber;
import code.name.monkey.appthemehelper.ThemeStore;


public class AccentProgressDialog {

    public static ProgressDialog show(Context context, CharSequence content) {
        return show(context, "", content, false);
    }

    public static ProgressDialog show(Context context, CharSequence charSequence, CharSequence charSequence2) {
        return show(context, charSequence, charSequence2, false);
    }

    public static ProgressDialog show(Context context, CharSequence charSequence, CharSequence charSequence2, boolean z) {
        return show(context, charSequence, charSequence2, z, false, null);
    }

    public static ProgressDialog show(Context context, CharSequence charSequence, CharSequence charSequence2, boolean z, boolean z2) {
        return show(context, charSequence, charSequence2, z, z2, null);
    }

    public static ProgressDialog show(Context context, CharSequence charSequence, CharSequence charSequence2, boolean z, boolean z2, DialogInterface.OnCancelListener onCancelListener) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(charSequence);
        progressDialog.setMessage(charSequence2);
        progressDialog.setIndeterminate(z);
        progressDialog.setCancelable(z2);
        progressDialog.setOnCancelListener(onCancelListener);
        progressDialog.show();
        //init放在show之后才有效果
        init(progressDialog);
        return progressDialog;
    }

    public static ProgressDialog instance(Context context, CharSequence charSequence, CharSequence charSequence2, boolean z, boolean z2, DialogInterface.OnCancelListener onCancelListener) {
        ProgressDialog progressDialog = new ProgressDialogImpl(context);
        progressDialog.setTitle(charSequence);
        progressDialog.setMessage(charSequence2);
        progressDialog.setIndeterminate(z);
        progressDialog.setCancelable(z2);
        progressDialog.setOnCancelListener(onCancelListener);
        return progressDialog;
    }

    static class ProgressDialogImpl extends ProgressDialog{

        public ProgressDialogImpl(Context context) {
            super(context);
        }

        public ProgressDialogImpl(Context context, int theme) {
            super(context, theme);
        }

        @Override
        public void show() {
            super.show();
            init(this);
        }

    }

    private static void init(final ProgressDialog progressDialog) {
        try {
            Field declaredField = ProgressDialog.class.getDeclaredField("mProgress");
            declaredField.setAccessible(true);
            ((ProgressBar) declaredField.get(progressDialog)).getIndeterminateDrawable().setColorFilter(ThemeStore.accentColor(progressDialog.getContext()), PorterDuff.Mode.SRC_ATOP);
        } catch (Exception unused) {
            Timber.e(unused, "refresh");
        }
    }
}
