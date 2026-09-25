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
 * {@code IF NOT EXISTS} and every {@code ALTER TABLE} is guarded by {@link #hasColumn}, which
 * checks {@code PRAGMA table_info}, since SQLite has no {@code IF NOT EXISTS} form for it.</p>
 */
class DBUpgrade {
    /**
     * Upgrades the given database to a new schema version
     */
    static void upgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (oldVersion < 4) {
            // Ad auto-skip: the per-episode advertisement ranges. Version 4 also added the per-feed
            // opt-out column, which version 5 replaced; a database coming from before 4 goes
            // straight to the version-5 column below.
            db.execSQL(DbSchema.CREATE_TABLE_AD_SEGMENTS);
            db.execSQL(DbSchema.CREATE_INDEX_AD_SEGMENTS_FEEDITEM);
        }
        if (oldVersion < 5) {
            upgradeFeedAdSkipToOverride(db);
        }
    }

    /**
     * Version 5: the per-feed ad-skip switch became an override of the global default.
     *
     * <p>Version 4 stored an opt-out, {@code feed_ad_skip INTEGER DEFAULT 1}, under a global
     * master switch. Version 5 stores {@code feed_ad_skip_override}: NULL follows the global
     * default, 1 and 0 are the feed's own choice. The old column cannot be reused because of its
     * {@code DEFAULT 1} (SQLite cannot alter a column's default), which would turn every feed row
     * inserted without its preferences into an explicit "on".
     *
     * <p>The mapping keeps what each feed did before: 1 ("not opted out") under the old master
     * switch meant "whatever the global switch says", so it becomes NULL; 0 stays 0. The copy only
     * runs when this step created the new column, so re-running the ladder never overwrites a
     * choice made after the upgrade.
     */
    static void upgradeFeedAdSkipToOverride(final SQLiteDatabase db) {
        if (hasColumn(db, DbSchema.TABLE_NAME_FEEDS, DbSchema.KEY_FEED_AD_SKIP_OVERRIDE)) {
            return;
        }
        db.execSQL("ALTER TABLE " + DbSchema.TABLE_NAME_FEEDS + " ADD COLUMN "
                + DbSchema.KEY_FEED_AD_SKIP_OVERRIDE + " INTEGER");
        if (hasColumn(db, DbSchema.TABLE_NAME_FEEDS, DbSchema.KEY_FEED_AD_SKIP_V4)) {
            db.execSQL("UPDATE " + DbSchema.TABLE_NAME_FEEDS
                    + " SET " + DbSchema.KEY_FEED_AD_SKIP_OVERRIDE + " = 0"
                    + " WHERE " + DbSchema.KEY_FEED_AD_SKIP_V4 + " = 0");
        }
    }

    /**
     * Whether {@code table} has {@code column}. SQLite offers no
     * {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS}, and repeating the ALTER would abort the
     * whole upgrade with a "duplicate column name" error, so every ALTER checks this first.
     */
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
