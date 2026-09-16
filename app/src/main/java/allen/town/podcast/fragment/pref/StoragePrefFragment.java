package allen.town.podcast.fragment.pref;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import allen.town.podcast.R;
import allen.town.podcast.activity.SettingsActivity;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.storage.DBTasks;
import allen.town.podcast.dialog.ChooseStorageFolderDialog;

import java.io.File;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

public class StoragePrefFragment extends AbsSettingsFragment {
    private static final String TAG = "StoragePrefFragment";
    private static final String PREF_CHOOSE_DATA_DIR = "prefChooseDataDir";
    private static final String PREF_IMPORT_EXPORT = "prefImportExport";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_storage);
        setupStorageScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).setTitle(R.string.storage_pref);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDataFolderText();
    }

    @SuppressLint("CheckResult") // fire-and-forget: app-scoped DB work with its own onError; nothing to dispose
    private void setupStorageScreen() {
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
        findPreference(PREF_IMPORT_EXPORT).setOnPreferenceClickListener(
                preference -> {
                    ((SettingsActivity) getActivity()).openScreen(R.xml.pref_import_export);
                    return true;
                }
        );
    }

    private void setDataFolderText() {
        File f = Prefs.getDataFolder(null);
        if (f != null) {
            findPreference(PREF_CHOOSE_DATA_DIR).setSummary(f.getAbsolutePath());
        }
    }

    @Override
    public void invalidateSettings() {

    }
}
