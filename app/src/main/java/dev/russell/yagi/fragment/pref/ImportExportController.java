package dev.russell.yagi.fragment.pref;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.russell.yagi.common.util.TopSnackbarUtil;
import dev.russell.yagi.common.views.AccentMaterialDialog;
import dev.russell.yagi.common.views.AccentProgressDialog;
import dev.russell.yagi.MyApp;
import dev.russell.yagi.R;
import dev.russell.yagi.activity.ImportOPMLActivity;
import dev.russell.yagi.asynctask.DocumentFileExportWorker;
import dev.russell.yagi.asynctask.ExportWorker;
import dev.russell.yagi.core.export.ExportWriter;
import dev.russell.yagi.core.export.opml.OpmlWriter;
import dev.russell.yagi.core.storage.DatabaseExporter;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Database backup/restore and OPML import/export for the settings screen that shows them. Create
 * it as a field of that fragment, so the activity-result launchers are registered before the
 * fragment is created, call {@link #bind} once its preferences are inflated and {@link #dispose}
 * from its {@code onStop}.
 */
public class ImportExportController {
    private static final String TAG = "ImportExportPrefs";
    private static final String PREF_OPML_EXPORT = "prefOpmlExport";
    private static final String PREF_OPML_IMPORT = "prefOpmlImport";
    private static final String PREF_DATABASE_IMPORT = "prefDatabaseImport";
    private static final String PREF_DATABASE_EXPORT = "prefDatabaseExport";
    private static final String DEFAULT_OPML_OUTPUT_NAME = "yagi_feeds_%s.opml";
    //a stricter type prevents some apps from importing
    private static final String CONTENT_TYPE_OPML = "text/*";
    private static final String DATABASE_EXPORT_FILENAME = "yagiBackup-%s.db";
    private final Fragment fragment;
    private final ActivityResultLauncher<Intent> chooseOpmlExportPathLauncher;
    private final ActivityResultLauncher<Intent> restoreDatabaseLauncher;
    private final ActivityResultLauncher<String> backupDatabaseLauncher;
    private final ActivityResultLauncher<String> chooseOpmlImportPathLauncher;
    private Disposable disposable;
    private ProgressDialog progressDialog;

    public ImportExportController(@NonNull Fragment fragment) {
        this.fragment = fragment;
        chooseOpmlExportPathLauncher = fragment.registerForActivityResult(
                new StartActivityForResult(), this::chooseOpmlExportPathResult);
        restoreDatabaseLauncher = fragment.registerForActivityResult(
                new StartActivityForResult(), this::restoreDatabaseResult);
        backupDatabaseLauncher = fragment.registerForActivityResult(
                new BackupDatabase(), this::backupDatabaseResult);
        chooseOpmlImportPathLauncher = fragment.registerForActivityResult(
                new GetContent(), this::chooseOpmlImportPathResult);
    }

    /** Cancels a running export or import; its progress dialog goes with the screen. */
    public void dispose() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private String dateStampFilename(String fname) {
        return String.format(fname, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
    }

    /** Wires the four backup preferences of {@code screen}, which must be the owning fragment. */
    public void bind(@NonNull PreferenceFragmentCompat screen) {
        progressDialog = AccentProgressDialog.instance(fragment.requireContext(), "",
                fragment.getString(R.string.please_wait), true, true, null);
        screen.findPreference(PREF_OPML_EXPORT).setOnPreferenceClickListener(
                preference -> {
                    openExportPathPicker(Export.OPML, chooseOpmlExportPathLauncher, new OpmlWriter());
                    return true;
                }
        );
        screen.findPreference(PREF_OPML_IMPORT).setOnPreferenceClickListener(
                preference -> {
                    try {
                        chooseOpmlImportPathLauncher.launch("*/*");
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "no file manager to pick an OPML file with", e);
                        TopSnackbarUtil.showSnack(fragment.requireActivity(),
                                R.string.unable_to_start_system_file_manager, Toast.LENGTH_LONG);
                    }
                    return true;
                });
        screen.findPreference(PREF_DATABASE_IMPORT).setOnPreferenceClickListener(
                preference -> {
                    importDatabase();
                    return true;
                });
        screen.findPreference(PREF_DATABASE_EXPORT).setOnPreferenceClickListener(
                preference -> {
                    exportDatabase();
                    return true;
                });
    }

    private void exportWithWriter(ExportWriter exportWriter, Uri uri, Export exportType) {
        Context context = fragment.requireActivity();
        progressDialog.show();
        if (uri == null) {
            Observable<File> observable = new ExportWorker(exportWriter, fragment.requireContext()).exportObservable();
            disposable = observable.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(output -> {
                        Uri fileUri = FileProvider.getUriForFile(context.getApplicationContext(),
                                context.getString(R.string.provider_authority), output);
                        showExportSuccessDialog(output.toString(), fileUri, exportType);
                    }, this::showExportErrorDialog, progressDialog::dismiss);
        } else {
            DocumentFileExportWorker worker = new DocumentFileExportWorker(exportWriter, context, uri);
            disposable = worker.exportObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(output ->
                            showExportSuccessDialog(output.getUri().toString(), output.getUri(), exportType),
                            this::showExportErrorDialog, progressDialog::dismiss);
        }
    }

    private void exportDatabase() {
        backupDatabaseLauncher.launch(dateStampFilename(DATABASE_EXPORT_FILENAME));
    }

    private void importDatabase() {
        // setup the alert builder
        AlertDialog.Builder builder = new AccentMaterialDialog(
                fragment.requireActivity(),
                R.style.MaterialAlertDialogTheme
        );
        builder.setTitle(R.string.database_import_label);
        builder.setMessage(R.string.database_import_warning);

        // add a button
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.confirm_label, (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            restoreDatabaseLauncher.launch(intent);
        });

        // create and show the alert dialog
        builder.show();
    }

    private void showDatabaseImportSuccessDialog() {
        AlertDialog.Builder builder =            new AccentMaterialDialog(
                    fragment.requireContext(),
                    R.style.MaterialAlertDialogTheme
            );
        builder.setTitle(R.string.successful_import_label);
        builder.setMessage(R.string.import_ok);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> MyApp.forceRestart());
        builder.show();
    }

    private void showExportSuccessDialog(String path, Uri streamUri, Export exportType) {
        final AlertDialog.Builder alert =            new AccentMaterialDialog(
                    fragment.requireContext(),
                    R.style.MaterialAlertDialogTheme
            );
        alert.setNeutralButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
        alert.setTitle(R.string.export_success_title);
        alert.setPositiveButton(R.string.share_label, (dialog, which) -> {
            new ShareCompat.IntentBuilder(fragment.requireContext())
                    .setType(exportType.contentType)
                    .setSubject(fragment.getString(exportType.labelResId))
                    .addStream(streamUri)
                    .setChooserTitle(R.string.share_label)
                    .startChooser();
        });
        alert.create().show();
    }

    private void showExportErrorDialog(final Throwable error) {
        Log.e(TAG, "import/export failed", error);
        progressDialog.dismiss();
        final AlertDialog.Builder alert =            new AccentMaterialDialog(
                    fragment.requireContext(),
                    R.style.MaterialAlertDialogTheme
            );
        alert.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
        alert.setTitle(R.string.export_error_label);
        alert.setMessage(error.getMessage());
        alert.show();
    }

    private void chooseOpmlExportPathResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        final Uri uri = result.getData().getData();
        exportWithWriter(new OpmlWriter(), uri, Export.OPML);
    }



    private void restoreDatabaseResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        final Uri uri = result.getData().getData();
        progressDialog.show();
        final Context appContext = fragment.requireContext().getApplicationContext();
        // Missing-file reconciliation for the imported database is deliberately NOT done here:
        // importBackup() swaps the file under the open Db singleton and the success dialog asks
        // for a restart; the once-per-process check in MainActivity runs after that restart.
        disposable = Completable.fromAction(() -> DatabaseExporter.importBackup(uri, appContext))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    showDatabaseImportSuccessDialog();
                    progressDialog.dismiss();
                }, this::showExportErrorDialog);
    }

    private void backupDatabaseResult(final Uri uri) {
        if (uri == null) {
            return;
        }
        progressDialog.show();
        final Context appContext = fragment.requireContext().getApplicationContext();
        disposable = Completable.fromAction(() -> DatabaseExporter.exportToDocument(uri, appContext))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    TopSnackbarUtil.showSnack(fragment.requireActivity(), R.string.export_success_title, Toast.LENGTH_LONG);
                    progressDialog.dismiss();
                }, this::showExportErrorDialog);
    }

    private void chooseOpmlImportPathResult(final Uri uri) {
        if (uri == null) {
            return;
        }
        final Intent intent = new Intent(fragment.requireContext(), ImportOPMLActivity.class);
        intent.setData(uri);
        fragment.startActivity(intent);
    }

    private void openExportPathPicker(Export exportType, ActivityResultLauncher<Intent> result, ExportWriter writer) {
        String title = dateStampFilename(exportType.outputNameTemplate);

        Intent intentPickAction = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(exportType.contentType)
                .putExtra(Intent.EXTRA_TITLE, title);

        // Creates an implicit intent to launch a file manager which lets
        // the user choose a specific directory to export to.
        try {
            result.launch(intentPickAction);
            return;
        } catch (ActivityNotFoundException e) {
            // safe to continue: the legacy export below writes to the app's own folder instead
            Log.w(TAG, "no file manager to pick an export folder with, exporting to storage", e);
        }

        // If we are using a SDK lower than API 21 or the implicit intent failed
        // fallback to the legacy export process
        exportWithWriter(writer, null, exportType);
    }

    private static class BackupDatabase extends ActivityResultContracts.CreateDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context, @NonNull final String input) {
            return super.createIntent(context, input)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/x-sqlite3");
        }
    }

    private enum Export {
        OPML(CONTENT_TYPE_OPML, DEFAULT_OPML_OUTPUT_NAME, R.string.opml_export_label);

        final String contentType;
        final String outputNameTemplate;
        @StringRes
        final int labelResId;

        Export(String contentType, String outputNameTemplate, int labelResId) {
            this.contentType = contentType;
            this.outputNameTemplate = outputNameTemplate;
            this.labelResId = labelResId;
        }
    }
}
