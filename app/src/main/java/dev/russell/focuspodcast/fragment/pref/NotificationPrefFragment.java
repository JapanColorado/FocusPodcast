package dev.russell.focuspodcast.fragment.pref;

import static dev.russell.focuspodcast.core.pref.Prefs.PREF_GPODNET_NOTIFICATIONS;

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

import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.activity.SettingsActivity;
import dev.russell.focuspodcast.common.util.TopSnackbarUtil;
import dev.russell.focuspodcast.common.views.AccentMaterialDialog;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.service.playback.NotificationCustomAction;
import dev.russell.focuspodcast.core.sync.SynchronizationSettings;

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
        findPreference(Prefs.PREF_NOTIFICATION_CUSTOM_BUTTONS).setOnPreferenceClickListener(preference -> {
            showCustomButtonsDialog();
            return true;
        });
        updateCustomButtonsSummary();
    }

    private static int customButtonLabel(NotificationCustomAction action) {
        switch (action) {
            case NEXT_CHAPTER:
                return R.string.custom_action_next_chapter;
            case CYCLE_SPEED:
                return R.string.custom_action_cycle_speed;
            case SLEEP_TIMER_EXTEND:
                return R.string.custom_action_sleep_extend;
            case SKIP_AD:
            default:
                return R.string.custom_action_skip_ad;
        }
    }

    /** Lists the chosen extra buttons in their order, or explains the setting when none is. */
    private void updateCustomButtonsSummary() {
        Preference preference = findPreference(Prefs.PREF_NOTIFICATION_CUSTOM_BUTTONS);
        if (preference == null) {
            return;
        }
        List<NotificationCustomAction> selected = Prefs.getNotificationCustomButtons();
        if (selected.isEmpty()) {
            preference.setSummary(R.string.pref_custom_notification_buttons_sum);
            return;
        }
        List<String> names = new ArrayList<>();
        for (NotificationCustomAction action : selected) {
            names.add(getString(customButtonLabel(action)));
        }
        preference.setSummary(android.text.TextUtils.join(", ", names));
    }

    /**
     * Picks up to {@link NotificationCustomAction#MAX_SELECTED} extra buttons. They appear in the
     * order they were ticked.
     */
    private void showCustomButtonsDialog() {
        final Context context = requireContext();
        final NotificationCustomAction[] all = NotificationCustomAction.values();
        final List<NotificationCustomAction> selected = new ArrayList<>(Prefs.getNotificationCustomButtons());
        final String[] names = new String[all.length];
        final boolean[] checked = new boolean[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = context.getString(customButtonLabel(all[i]));
            checked[i] = selected.contains(all[i]);
        }
        final int max = NotificationCustomAction.MAX_SELECTED;

        AlertDialog.Builder builder = new AccentMaterialDialog(context, R.style.MaterialAlertDialogTheme);
        builder.setTitle(context.getString(R.string.pref_compact_notification_buttons_dialog_title, max));
        builder.setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
            if (!isChecked) {
                checked[which] = false;
                selected.remove(all[which]);
            } else if (selected.size() < max) {
                checked[which] = true;
                selected.add(all[which]);
            } else {
                checked[which] = false;
                ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                TopSnackbarUtil.showSnack(getActivity(),
                        context.getString(R.string.pref_compact_notification_buttons_dialog_error, max),
                        Toast.LENGTH_SHORT);
            }
        });
        builder.setPositiveButton(R.string.confirm_label, (dialog, which) -> {
            Prefs.setNotificationCustomButtons(selected);
            updateCustomButtonsSummary();
        });
        builder.setNegativeButton(R.string.cancel_label, null);
        builder.create().show();
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
