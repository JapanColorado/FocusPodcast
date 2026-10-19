package dev.russell.focuspodcast.storage.db;

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

    /** Escape character of {@link #LIKE_ESCAPED}; see {@link #likeContains}. */
    private static final char LIKE_ESCAPE = '\\';

    /** A bound "contains" match: {@code column + LIKE_ESCAPED} with {@link #likeContains} as its argument. */
    protected static final String LIKE_ESCAPED = " LIKE ? ESCAPE '" + LIKE_ESCAPE + "'";

    /** The words of a search query, split on whitespace; none for a blank query. */
    protected static String[] searchWords(String query) {
        String trimmed = query == null ? "" : query.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    /**
     * The LIKE pattern matching any text that contains {@code word} literally: {@code %} and
     * {@code _} in the word are escaped (with {@link #LIKE_ESCAPE}, see {@link #LIKE_ESCAPED}) so
     * they do not act as wildcards. Bind it as an argument; quotes need no escaping then.
     */
    protected static String likeContains(String word) {
        StringBuilder builder = new StringBuilder(word.length() + 2).append('%');
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                builder.append(LIKE_ESCAPE);
            }
            builder.append(c);
        }
        return builder.append('%').toString();
    }
}
