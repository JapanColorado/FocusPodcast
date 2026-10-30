package dev.russell.yagi.core.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.format.Formatter;
import android.util.Log;
import dev.russell.yagi.core.R;
import dev.russell.yagi.storage.db.Db;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.List;

public class DatabaseExporter {
    private static final String TAG = "DatabaseExporter";
    private static final String TEMP_DB_NAME = Db.DATABASE_NAME + "_tmp";
    private static final String EXPORT_DB_NAME = Db.DATABASE_NAME + "_export";

    public static void exportToDocument(Uri uri, Context context) throws IOException {
        ParcelFileDescriptor pfd = null;
        FileOutputStream fileOutputStream = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "wt");
            fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
            exportToStream(fileOutputStream, context);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw e;
        } finally {
            IOUtils.closeQuietly(fileOutputStream);

            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException e) {
                    Log.d(TAG, "Unable to close ParcelFileDescriptor");
                }
            }
        }
    }

    /**
     * Writes a copy of the database with the user's settings added to it (see
     * {@link PreferencesBackup}). The settings go into a temporary copy, never the live database.
     */
    public static void exportToStream(FileOutputStream outFileStream, Context context) throws IOException {
        File exportDB = new File(context.getCacheDir(), EXPORT_DB_NAME);
        FileChannel src = null;
        FileChannel dst = null;
        try {
            File currentDB = context.getDatabasePath(Db.DATABASE_NAME);
            if (!currentDB.exists()) {
                throw new IOException("Can not access current database");
            }
            SQLiteDatabase.deleteDatabase(exportDB);
            FileUtils.copyFile(currentDB, exportDB);
            SQLiteDatabase db = SQLiteDatabase.openDatabase(exportDB.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READWRITE);
            try {
                // Keep the copy in rollback-journal mode like the live database, so that
                // everything is in the one file that gets exported.
                db.disableWriteAheadLogging();
                PreferencesBackup.writeTo(db, context);
            } finally {
                db.close();
            }

            src = new FileInputStream(exportDB).getChannel();
            dst = outFileStream.getChannel();
            long srcSize = src.size();
            dst.transferFrom(src, 0, srcSize);

            long newDstSize = dst.size();
            if (newDstSize != srcSize) {
                throw new IOException(String.format(
                        "Unable to write entire database. Expected to write %s, but wrote %s.",
                        Formatter.formatShortFileSize(context, srcSize),
                        Formatter.formatShortFileSize(context, newDstSize)));
            }
        } catch (IOException | SQLiteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw e;
        } finally {
            IOUtils.closeQuietly(src);
            IOUtils.closeQuietly(dst);
            SQLiteDatabase.deleteDatabase(exportDB);
        }
    }

    public static void importBackup(Uri inputUri, Context context) throws IOException {
        InputStream inputStream = null;
        try {
            File tempDB = context.getDatabasePath(TEMP_DB_NAME);
            inputStream = context.getContentResolver().openInputStream(inputUri);
            FileUtils.copyInputStreamToFile(inputStream, tempDB);

            List<PreferencesBackup.Row> settings;
            SQLiteDatabase db = SQLiteDatabase.openDatabase(tempDB.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READWRITE);
            try {
                if (db.getVersion() > Db.VERSION) {
                    throw new IOException(context.getString(R.string.import_no_downgrade));
                }
                db.disableWriteAheadLogging();
                settings = PreferencesBackup.takeFrom(db);
            } finally {
                db.close();
            }
            deleteJournalFiles(tempDB);

            File currentDB = context.getDatabasePath(Db.DATABASE_NAME);
            boolean success = currentDB.delete();
            if (!success) {
                throw new IOException("Unable to delete old database");
            }
            FileUtils.moveFile(tempDB, currentDB);
            // Applied on the next start, which the import's success dialog forces
            if (settings != null) {
                PreferencesBackup.savePendingRestore(context, settings);
            }
        } catch (IOException | SQLiteException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw e;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    /** Removes what SQLite may leave next to a database file that is about to be moved. */
    private static void deleteJournalFiles(File db) {
        for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
            FileUtils.deleteQuietly(new File(db.getPath() + suffix));
        }
    }
}
