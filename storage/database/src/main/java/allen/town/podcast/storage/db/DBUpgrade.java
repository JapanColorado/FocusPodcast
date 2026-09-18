package allen.town.podcast.storage.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * The hand-written schema upgrade ladder. Each step is guarded by {@code oldVersion < N} so that a
 * database opened from any earlier version walks through every step in order up to
 * {@link DbSchema#VERSION}.
 *
 * <p>Every statement here is written to be safe to run against a database that already has the
 * objects it creates: the fork renumbered the schema, so a database stamped with an old
 * {@code user_version} may still carry a newer physical layout. The CREATE statements therefore use
 * {@code IF NOT EXISTS} and {@link #addColumnIfMissing} checks {@code PRAGMA table_info} before
 * issuing an {@code ALTER TABLE}, which SQLite has no {@code IF NOT EXISTS} form for.</p>
 */
class DBUpgrade {
    /**
     * Upgrades the given database to a new schema version
     */
    static void upgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (oldVersion < 4) {
            // Ad auto-skip: the per-episode advertisement ranges and the per-feed switch.
            db.execSQL(DbSchema.CREATE_TABLE_AD_SEGMENTS);
            db.execSQL(DbSchema.CREATE_INDEX_AD_SEGMENTS_FEEDITEM);
            addColumnIfMissing(db, DbSchema.TABLE_NAME_FEEDS, DbSchema.KEY_FEED_AD_SKIP,
                    "INTEGER DEFAULT 1");
        }
    }

    /**
     * Adds {@code column} to {@code table} unless the table already has it. SQLite offers no
     * {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS}, and repeating the ALTER would abort the
     * whole upgrade with a "duplicate column name" error.
     */
    private static void addColumnIfMissing(final SQLiteDatabase db, final String table,
                                           final String column, final String definition) {
        if (!hasColumn(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean hasColumn(final SQLiteDatabase db, final String table, final String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) {
                return false;
            }
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

}
