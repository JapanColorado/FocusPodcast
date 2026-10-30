package dev.russell.yagi.fragment.pref;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;

import dev.russell.yagi.BuildConfig;
import dev.russell.yagi.R;
import dev.russell.yagi.activity.SettingsActivity;
import dev.russell.yagi.common.ui.customtabs.BrowserLauncher;
import dev.russell.yagi.common.util.LogUtils;
import dev.russell.yagi.core.util.ProjectLinks;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** About dialog, changelog, issue tracker and bug report links. */
public class AboutSupportPrefFragment extends AbsSettingsFragment {
    private static final String TAG = "AboutSupportPrefFragment";
    private static final String PREF_ABOUT = "pref_about";
    private static final String PREF_CHANGE_LOG = "pref_changelog";
    private static final String PREF_VIEW_FORUM = "prefViewForum";
    private static final String PREF_SEND_BUG_REPORT = "pref_report_bug";

    /** Opens the bug report form once the share sheet for the log is closed, however it was closed. */
    private final ActivityResultLauncher<Intent> shareLogLauncher =
            registerForActivityResult(new StartActivityForResult(), result -> openBugReportForm());
    private Disposable logDisposable;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_about);
        findPreference(PREF_ABOUT).setOnPreferenceClickListener(preference -> {
            new AboutFragment().show(getActivity().getSupportFragmentManager(), null);
            return true;
        });
        findPreference(PREF_CHANGE_LOG).setOnPreferenceClickListener(preference -> {
            BrowserLauncher.openUrl(getContext(), ProjectLinks.RELEASES);
            return true;
        });
        findPreference(PREF_VIEW_FORUM).setOnPreferenceClickListener(preference -> {
            BrowserLauncher.openUrl(getActivity(), ProjectLinks.ISSUES);
            return true;
        });
        findPreference(PREF_SEND_BUG_REPORT).setOnPreferenceClickListener(preference -> {
            reportBug();
            return true;
        });
    }

    /**
     * Writes the log to a file and offers it in a share sheet (to save it or send it somewhere it
     * can be attached from), then opens the bug report form on GitHub.
     */
    private void reportBug() {
        if (logDisposable != null) {
            logDisposable.dispose();
        }
        final String authority = getString(R.string.provider_authority);
        final String subject = getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " log";
        logDisposable = Observable.<Uri>create(emitter -> LogUtils.logToSd(emitter, requireContext(), authority))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(uri -> shareLogLauncher.launch(LogUtils.shareLogIntent(requireContext(), uri, subject)),
                        error -> {
                            Log.e(TAG, "Could not write the log for the bug report", error);
                            openBugReportForm();
                        });
    }

    private void openBugReportForm() {
        if (getContext() != null) {
            BrowserLauncher.openUrl(getContext(), ProjectLinks.newBugReport(BuildConfig.VERSION_NAME));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (logDisposable != null) {
            logDisposable.dispose();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.pref_about_support_title);
    }

    @Override
    public void invalidateSettings() {

    }
}
