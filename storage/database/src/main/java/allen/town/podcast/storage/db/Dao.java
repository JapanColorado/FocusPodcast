package allen.town.podcast.storage.db;

import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Common base for the per-table helpers in this package. It owns nothing but the writable
 * {@link SQLiteDatabase} handle opened by {@link Db} and the shared transaction wrapper: every
 * multi-statement write in the DAOs runs through {@link #inTransaction}, which begins a
 * non-exclusive transaction, marks it successful only if the body completed, logs and swallows a
 * {@link SQLException} so that a failed write rolls back without taking the process down, and always
 * ends the transaction. Subclasses add the statements for one table each.
 */
abstract class Dao {

    protected static final String TAG = "Db";

    protected final SQLiteDatabase db;

    Dao(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * Runs {@code body} inside a non-exclusive transaction. If it throws, the transaction is rolled
     * back: the caller's in-memory model is unchanged and the next write of the same data retries.
     */
    protected void inTransaction(String operation, Runnable body) {
        try {
            db.beginTransactionNonExclusive();
            body.run();
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            // Log and continue: setTransactionSuccessful() was never reached, so the finally
            // below rolls the whole statement back. The caller's in-memory model is unchanged
            // and the next write of the same data retries it.
            Log.e(TAG, operation + " failed, transaction rolled back", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Uses DatabaseUtils to escape a search query and removes ' at the
     * beginning and the end of the string returned by the escape method.
     */
    protected String[] prepareSearchQuery(String query) {
        String[] queryWords = query.split("\\s+");
        for (int i = 0; i < queryWords.length; ++i) {
            StringBuilder builder = new StringBuilder();
            DatabaseUtils.appendEscapedSQLString(builder, queryWords[i]);
            builder.deleteCharAt(0);
            builder.deleteCharAt(builder.length() - 1);
            queryWords[i] = builder.toString();
        }

        return queryWords;
    }
}
