package allen.town.focus_common.common.prefs.supportv7.dialogs;

import android.content.Context;
import android.widget.ListAdapter;

import androidx.appcompat.app.AlertDialog;

import allen.town.focus_common.R;
import allen.town.focus_common.views.AccentMaterialDialog;

public class PreferenceListDialog {
    protected Context context;
    private String title;
    private OnPreferenceChangedListener onPreferenceChangedListener;
    private int selectedPos = 0;

    public PreferenceListDialog(Context context, String title) {
        this.context = context;
        this.title = title;
    }

    public interface OnPreferenceChangedListener {
        /**
         * Notified when user confirms preference
         *
         * @param pos The index of the item that was selected
         */

        void preferenceChanged(int pos);
    }

    public void setSelection(int position){
        selectedPos = position;
    }

    public void openDialog(String[] items) {

        AlertDialog.Builder builder = new AccentMaterialDialog(
                    context,
                    R.style.MaterialAlertDialogTheme
            );
        builder.setTitle(title);
        builder.setSingleChoiceItems(items, selectedPos, (dialog, which) -> {
            selectedPos = which;
            dialog.dismiss();
            if (onPreferenceChangedListener != null && selectedPos >= 0) {
                onPreferenceChangedListener.preferenceChanged(selectedPos);
            }
        });
        builder.create().show();
    }

    public void openDialog(ListAdapter listAdapter) {

        AlertDialog.Builder builder = new AccentMaterialDialog(
                context,
                R.style.MaterialAlertDialogTheme
        );
        builder.setTitle(title);
        builder.setSingleChoiceItems(listAdapter, selectedPos, (dialog, which) -> {
            selectedPos = which;
            dialog.dismiss();
            if (onPreferenceChangedListener != null && selectedPos >= 0) {
                onPreferenceChangedListener.preferenceChanged(selectedPos);
            }
        });
        builder.create().show();
    }

    public void setOnPreferenceChangedListener(OnPreferenceChangedListener onPreferenceChangedListener) {
        this.onPreferenceChangedListener = onPreferenceChangedListener;
    }
}
