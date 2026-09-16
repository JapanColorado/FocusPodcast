package allen.town.focus_common.common.prefs.supportv7;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import allen.town.focus_common.common.prefs.supportv7.dialogs.ATEListPreferenceDialogFragmentCompat;
import allen.town.focus_common.common.prefs.supportv7.dialogs.ATEMultiListPreferenceDialogFragmentCompat;
import allen.town.focus_common.common.prefs.supportv7.dialogs.ATEPreferenceDialogFragment;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public abstract class ATEPreferenceFragmentCompat extends PreferenceFragmentCompat {
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (getCallbackFragment() instanceof OnPreferenceDisplayDialogCallback) {
            ((OnPreferenceDisplayDialogCallback) getCallbackFragment()).onPreferenceDisplayDialog(this, preference);
            return;
        }

        if (this.getActivity() instanceof OnPreferenceDisplayDialogCallback) {
            ((OnPreferenceDisplayDialogCallback) this.getActivity()).onPreferenceDisplayDialog(this, preference);
            return;
        }

        if (getFragmentManager().findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") == null) {
            DialogFragment dialogFragment = onCreatePreferenceDialog(preference);

            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(this.getFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
                return;
            }
        }

        super.onDisplayPreferenceDialog(preference);
    }

    @Nullable
    public DialogFragment onCreatePreferenceDialog(Preference preference) {
        if (preference instanceof ListPreference) {
            return ATEListPreferenceDialogFragmentCompat.getInstance(preference.getKey());
        } else if (preference instanceof ATEDialogPreference) {
            return ATEPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ATEMultiListPreference) {
            return ATEMultiListPreferenceDialogFragmentCompat.getInstance(preference.getKey());
        }
        return null;
    }
}