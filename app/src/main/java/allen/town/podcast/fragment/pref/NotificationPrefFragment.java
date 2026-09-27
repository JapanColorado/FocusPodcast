package allen.town.podcast.fragment.pref;

import static allen.town.podcast.core.pref.Prefs.PREF_GPODNET_NOTIFICATIONS;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import java.util.List;

import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.common.util.TopSnackbarUtil;
import allen.town.podcast.common.views.AccentMaterialDialog;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.sync.SynchronizationSettings;

/**
 * The playback notification's options on every Android version, plus either a link to the
 * system's per-channel settings (Android 8+) or the in-app switches that stand in for the
 * channels on older versions.
 */
public class NotificationPrefFragment extends AbsSettingsFragment {
    private static final String TAG = "NotificationPrefs";
    private static final String PREF_SYSTEM_NOTIFICATION_SETTINGS = "prefSystemNotificationSettings";
    private static final String PREF_CATEGORY_NEWS = "notificationCategoryNews";
    private static final String PREF_CATEGORY_ERRORS = "notificationCategoryErrors";
    private static final int MAX_COMPACT_BUTTONS = 2;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_notifications);
        setUpScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.notification_pref_fragment);
    }

    private void setUpScreen() {
        boolean hasChannels = Build.VERSION.SDK_INT >= 26;
        findPreference(PREF_SYSTEM_NOTIFICATION_SETTINGS).setVisible(hasChannels);
        findPreference(PREF_CATEGORY_NEWS).setVisible(!hasChannels);
        findPreference(PREF_CATEGORY_ERRORS).setVisible(!hasChannels);
        findPreference(Prefs.PREF_EXPANDED_NOTIFICATION).setVisible(!hasChannels);

        findPreference(PREF_GPODNET_NOTIFICATIONS).setEnabled(SynchronizationSettings.isProviderConnected());
        findPreference(PREF_SYSTEM_NOTIFICATION_SETTINGS).setOnPreferenceClickListener(preference -> {
            openSystemNotificationSettings();
            return true;
        });
        findPreference(Prefs.PREF_COMPACT_NOTIFICATION_BUTTONS).setOnPreferenceClickListener(preference -> {
            showNotificationButtonsDialog();
            return true;
        });
    }

    private void openSystemNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "no system screen for the app's notification settings", e);
        }
    }

    private void showNotificationButtonsDialog() {
        final Context context = getActivity();
        final List<Integer> preferredButtons = Prefs.getCompactNotificationButtons();
        final String[] allButtonNames = context.getResources().getStringArray(
                R.array.compact_notification_buttons_options);
        boolean[] checked = new boolean[allButtonNames.length]; // booleans default to false in java

        for (int i = 0; i < checked.length; i++) {
            if (preferredButtons.contains(i)) {
                checked[i] = true;
            }
        }

        AlertDialog.Builder builder = new AccentMaterialDialog(
                context,
                R.style.MaterialAlertDialogTheme
        );
        builder.setTitle(String.format(context.getResources().getString(
                R.string.pref_compact_notification_buttons_dialog_title), MAX_COMPACT_BUTTONS));
        builder.setMultiChoiceItems(allButtonNames, checked, (dialog, which, isChecked) -> {
            checked[which] = isChecked;

            if (isChecked) {
                if (preferredButtons.size() < MAX_COMPACT_BUTTONS) {
                    preferredButtons.add(which);
                } else {
                    // Only allow a maximum of two selections. This is because the notification
                    // on the lock screen can only display 3 buttons, and the play/pause button
                    // is always included.
                    checked[which] = false;
                    ListView selectionView = ((AlertDialog) dialog).getListView();
                    selectionView.setItemChecked(which, false);
                    TopSnackbarUtil.showSnack(
                            getActivity(),
                            String.format(context.getResources().getString(
                                    R.string.pref_compact_notification_buttons_dialog_error), MAX_COMPACT_BUTTONS),
                            Toast.LENGTH_SHORT);
                }
            } else {
                preferredButtons.remove((Integer) which);
            }
        });
        builder.setPositiveButton(R.string.confirm_label, (dialog, which) ->
                Prefs.setCompactNotificationButtons(preferredButtons));
        builder.setNegativeButton(R.string.cancel_label, null);
        builder.create().show();
    }

    @Override
    public void invalidateSettings() {

    }
}
