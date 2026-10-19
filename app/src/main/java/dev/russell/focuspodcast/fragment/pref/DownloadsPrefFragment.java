package dev.russell.focuspodcast.fragment.pref;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import java.io.File;

import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.activity.SettingsActivity;
import dev.russell.focuspodcast.core.pref.Prefs;
import dev.russell.focuspodcast.core.storage.DBTasks;
import dev.russell.focuspodcast.core.util.download.AutoUpdateManager;
import dev.russell.focuspodcast.dialog.ChooseStorageFolderDialog;
import dev.russell.focuspodcast.dialog.ProxyDialog;
import dev.russell.focuspodcast.fragment.storage.StorageSnapshot;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Automatic downloads, what may use mobile data, when episodes are deleted and where they live. */
public class DownloadsPrefFragment extends AbsSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "DownloadsPrefFragment";
    private static final String PREF_MOBILE_UPDATE_TYPES = "pref_mobile_update_types";
    private static final String PREF_PROXY = "prefProxy";
    private static final String PREF_CHOOSE_DATA_DIR = "prefChooseDataDir";
    private static final String PREF_STORAGE_USAGE = "prefStorageUsage";

    private Disposable storageDisposable;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_downloads);
        setupAutoDownloadPreferences();
        setupAdvancedPreferences();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.pref_downloads_storage_title);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAutodownloadItemVisibility(Prefs.isEnableAutodownload());
        setParallelDownloadsText(Prefs.getParallelDownloads());
        setDataFolderText();
        loadStorageSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (storageDisposable != null) {
            storageDisposable.dispose();
        }
    }

    /** "1.8 GB used · 23 GB free" under Storage usage; measuring the files needs a background thread. */
    private void loadStorageSummary() {
        if (storageDisposable != null) {
            storageDisposable.dispose();
        }
        storageDisposable = Single.fromCallable(StorageSnapshot::load)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(snapshot -> {
                    Context context = getContext();
                    if (context == null) {
                        return;
                    }
                    findPreference(PREF_STORAGE_USAGE).setSummary(getString(R.string.storage_usage_summary,
                            Formatter.formatShortFileSize(context, snapshot.getOverview().bytes),
                            Formatter.formatShortFileSize(context, snapshot.getFreeBytes())));
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void setupAutoDownloadPreferences() {
        findPreference(Prefs.PREF_ENABLE_AUTODL).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    if (newValue instanceof Boolean) {
                        checkAutodownloadItemVisibility((Boolean) newValue);
                    }
                    return true;
                });
        buildEpisodeCleanupPreference();
    }

    private void checkAutodownloadItemVisibility(boolean autoDownload) {
        findPreference(Prefs.PREF_EPISODE_CACHE_SIZE).setEnabled(autoDownload);
        findPreference(Prefs.PREF_ENABLE_AUTODL_ON_BATTERY).setEnabled(autoDownload);
        findPreference(Prefs.PREF_EPISODE_CLEANUP).setEnabled(autoDownload);
    }

    private void buildEpisodeCleanupPreference() {
        final Resources res = getActivity().getResources();

        ListPreference pref = findPreference(Prefs.PREF_EPISODE_CLEANUP);
        String[] values = res.getStringArray(
                R.array.episode_cleanup_values);
        String[] entries = new String[values.length];
        for (int x = 0; x < values.length; x++) {
            int v = Integer.parseInt(values[x]);
            if (v == Prefs.EPISODE_CLEANUP_EXCEPT_FAVORITE) {
                entries[x] =  res.getString(R.string.episode_cleanup_except_favorite_removal);
            } else if (v == Prefs.EPISODE_CLEANUP_QUEUE) {
                entries[x] = res.getString(R.string.episode_cleanup_queue_removal);
            } else if (v == Prefs.EPISODE_CLEANUP_NULL){
                entries[x] = res.getString(R.string.episode_cleanup_never);
            } else if (v == 0) {
                entries[x] = res.getString(R.string.episode_cleanup_after_listening);
            } else if (v > 0 && v < 24) {
                entries[x] = res.getQuantityString(R.plurals.episode_cleanup_hours_after_listening, v, v);
            } else {
                int numDays = v / 24; // assume underlying value will be NOT fraction of days, e.g., 36 (hours)
                entries[x] = res.getQuantityString(R.plurals.episode_cleanup_days_after_listening, numDays, numDays);
            }
        }
        pref.setEntries(entries);
    }

    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    private void setupAdvancedPreferences() {
        // the number picker itself keeps the value between 1 and 50 (inclusive)
        findPreference(Prefs.PREF_PARALLEL_DOWNLOADS)
                .setOnPreferenceChangeListener(
                        (preference, o) -> {
                            if (o instanceof Integer) {
                                setParallelDownloadsText((Integer) o);
                            }
                            return true;
                        }
                );
        findPreference(PREF_STORAGE_USAGE).setOnPreferenceClickListener(preference -> {
            ((SettingsActivity) requireActivity()).openStorage();
            return true;
        });
        findPreference(PREF_PROXY).setOnPreferenceClickListener(preference -> {
            ProxyDialog dialog = new ProxyDialog(getActivity());
            dialog.show();
            return true;
        });
        findPreference(PREF_CHOOSE_DATA_DIR).setOnPreferenceClickListener(
                preference -> {
                    ChooseStorageFolderDialog.showDialog(getContext(), path -> {
                        Prefs.setDataFolder(path);
                        setDataFolderText();
                        // Existing downloads keep their absolute paths in the old folder and are
                        // not moved, so re-check which of them are still reachable.
                        final Context appContext = requireContext().getApplicationContext();
                        Completable.fromAction(() -> DBTasks.checkMissingMediaFiles(appContext, true))
                                .subscribeOn(Schedulers.io())
                                .subscribe(() -> { },
                                        error -> Log.e(TAG, Log.getStackTraceString(error)));
                    });
                    return true;
                }
        );
    }

    private void setParallelDownloadsText(int downloads) {
        final Resources res = getActivity().getResources();
        String s = res.getString(R.string.parallel_downloads, downloads);
        findPreference(Prefs.PREF_PARALLEL_DOWNLOADS).setSummary(s);
    }

    private void setDataFolderText() {
        File f = Prefs.getDataFolder(null);
        if (f != null) {
            findPreference(PREF_CHOOSE_DATA_DIR).setSummary(f.getAbsolutePath());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_MOBILE_UPDATE_TYPES.equals(key) && getContext() != null) {
            // the multi-select writes the pref directly; the periodic refresh worker bakes
            // the metered/unmetered constraint in at schedule time, so reschedule it
            AutoUpdateManager.restartUpdateAlarm(getContext().getApplicationContext());
        }
    }

    @Override
    public void invalidateSettings() {

    }
}
