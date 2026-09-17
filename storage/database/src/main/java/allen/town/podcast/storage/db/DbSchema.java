package allen.town.podcast.storage.db;

import static allen.town.podcast.model.feed.FeedPreferences.SPEED_USE_GLOBAL;

/**
 * Holds the static shape of the podcast database: the table and column names, the CREATE TABLE and
 * CREATE INDEX statements executed when the database is first created, the projection used for feed
 * rows and the pre-built SELECT/JOIN fragments shared by the DAOs in this package. It owns no state
 * and performs no queries; {@link Db} re-exports the names that callers outside this module use, and
 * the DAOs build their statements from the fragments declared here.
 */
final class DbSchema {

    public static final String DATABASE_NAME = "focusPodcastApp.db";
    public static final int VERSION = 4;

    /**
     * Maximum number of arguments for IN-operator.
     */
    static final int IN_OPERATOR_MAXIMUM = 800;

    // Key-constants
    public static final String KEY_TITLE = "title";
    public static final String KEY_ID = "id";
    public static final String KEY_FILE_URL = "file_path";
    public static final String KEY_CUSTOM_TITLE = "custom_title";
    public static final String KEY_LINK = "link";
    public static final String KEY_POSITION = "position";
    public static final String KEY_DOWNLOAD_URL = "rss_url";
    public static final String KEY_PUBDATE = "pub_date";
    public static final String KEY_READ = "read";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_SIZE = "file_size";
    public static final String KEY_IMAGE_URL = "image_url";
    public static final String KEY_FEED = "feed";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_MEDIA = "media";
    public static final String KEY_DOWNLOADED = "is_downloaded";
    public static final String KEY_MIME_TYPE = "mime_type";
    public static final String KEY_FEEDFILE = "feed_file";
    public static final String KEY_REASON = "reason";
    public static final String KEY_SUCCESSFUL = "is_successful";
    public static final String KEY_LASTUPDATE = "last_update";
    public static final String KEY_COMPLETION_DATE = "completion_date";
    public static final String KEY_FEEDITEM = "feeditem";
    public static final String KEY_PAYMENT_LINK = "payment_link";
    public static final String KEY_FEEDFILETYPE = "feedfile_type";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_HAS_CHAPTERS = "has_chapters";
    public static final String KEY_START = "start";
    public static final String KEY_TYPE = "type";
    public static final String KEY_PLAYBACK_COMPLETION_DATE = "playback_completion_date";
    public static final String KEY_ITEM_IDENTIFIER = "item_identifier";
    public static final String KEY_DOWNLOADSTATUS_TITLE = "title";
    public static final String KEY_FEED_IDENTIFIER = "feed_identifier";
    public static final String KEY_REASON_DETAILED = "reason_detail";
    public static final String KEY_SKIP_SILENCE_ENABLED = "skip_silence";
    public static final String KEY_AUTO_DOWNLOAD_ATTEMPTS = "auto_download";
    public static final String KEY_AUTO_DOWNLOAD_ENABLED = "auto_download"; // Both tables use the same key
    public static final String KEY_IS_SUBSCRIBED = "is_subscribed";
    public static final String KEY_ITUNES_FEED_ID = "itunes_feed_id";
    public static final String KEY_USE_FEED_EFFECT = "use_feed_effect";
    public static final String KEY_LOUDNESS_ENABLED = "loudness";
    public static final String KEY_MONO_ENABLED = "mono";
    public static final String KEY_PLAYED_DURATION = "played_duration";
    public static final String KEY_KEEP_UPDATED = "keep_updated";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_FEED_VOLUME_ADAPTION = "feed_volume_adaption";
    public static final String KEY_AUTO_DELETE_ACTION = "auto_delete_action";
    public static final String KEY_MINIMAL_DURATION_FILTER = "minimal_duration_filter";
    public static final String KEY_IS_PAGED = "is_paged";
    public static final String KEY_NEXT_PAGE_LINK = "next_page_link";
    public static final String KEY_HIDE = "hide";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_LAST_UPDATE_FAILED = "last_update_failed";
    public static final String KEY_HAS_EMBEDDED_PICTURE = "has_embedded_picture";
    public static final String KEY_LAST_PLAYED_TIME = "last_played_time";
    public static final String KEY_SORT_ORDER = "sort_order";
    public static final String KEY_EXCLUDE_FILTER = "exclude_filter";
    public static final String KEY_PODCASTINDEX_CHAPTER_URL = "podcastindex_chapter_url";
    public static final String KEY_INCLUDE_FILTER = "include_filter";
    public static final String KEY_FEED_SKIP_INTRO = "feed_skip_intro";
    public static final String KEY_FEED_SKIP_ENDING = "feed_skip_ending";
    public static final String KEY_FEED_TAGS = "tags";
    public static final String KEY_EPISODE_NOTIFICATION = "episode_notification";
    public static final String KEY_FEED_PLAYBACK_SPEED = "feed_playback_speed";
    public static final String KEY_FEED_AD_SKIP = "feed_ad_skip";
    public static final String KEY_AD_START_MS = "start_ms";
    public static final String KEY_AD_END_MS = "end_ms";
    public static final String KEY_AD_SOURCE = "source";
    public static final String KEY_AD_CONFIDENCE = "confidence";
    public static final String KEY_AD_ENABLED = "enabled";

    // Table names
    public static final String TABLE_NAME_FEEDS = "feeds";
    public static final String TABLE_NAME_FEED_ITEMS = "episodes";
    public static final String TABLE_NAME_FEED_MEDIA = "medias";
    public static final String TABLE_NAME_DOWNLOAD_LOG = "download_log";
    public static final String TABLE_NAME_QUEUE = "playlist";
    public static final String TABLE_NAME_SIMPLECHAPTERS = "chapters";
    public static final String TABLE_NAME_FAVORITES = "favorites";
    public static final String TABLE_NAME_AD_SEGMENTS = "ad_segments";

    // SQL Statements for creating new tables
    static final String TABLE_PRIMARY_KEY = KEY_ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT ,";

    static final String CREATE_TABLE_FEEDS = "CREATE TABLE "
            + TABLE_NAME_FEEDS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_CUSTOM_TITLE + " TEXT," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT,"
            + KEY_LINK + " TEXT,"
            + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
            + KEY_LASTUPDATE + " TEXT," + KEY_LANGUAGE + " TEXT," + KEY_AUTHOR
            + " TEXT," + KEY_IMAGE_URL + " TEXT," + KEY_TYPE + " TEXT,"
            + KEY_SKIP_SILENCE_ENABLED + " INTEGER DEFAULT 0,"
            + KEY_IS_SUBSCRIBED + " INTEGER DEFAULT 0,"
            + KEY_FEED_IDENTIFIER + " TEXT," + KEY_AUTO_DOWNLOAD_ENABLED + " INTEGER DEFAULT 1,"
            + KEY_USE_FEED_EFFECT + " INTEGER DEFAULT 0,"
            + KEY_LOUDNESS_ENABLED + " INTEGER DEFAULT 0,"
            + KEY_ITUNES_FEED_ID + " TEXT,"
            + KEY_USERNAME + " TEXT,"
            + KEY_MONO_ENABLED + " INTEGER DEFAULT 0,"
            + KEY_INCLUDE_FILTER + " TEXT DEFAULT '',"
            + KEY_EXCLUDE_FILTER + " TEXT DEFAULT '',"
            + KEY_PASSWORD + " TEXT,"
            + KEY_KEEP_UPDATED + " INTEGER DEFAULT 1,"
            + KEY_SORT_ORDER + " TEXT,"
            + KEY_HIDE + " TEXT,"
            + KEY_IS_PAGED + " INTEGER DEFAULT 0,"
            + KEY_MINIMAL_DURATION_FILTER + " INTEGER DEFAULT -1,"
            + KEY_NEXT_PAGE_LINK + " TEXT,"
            + KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0,"
            + KEY_FEED_PLAYBACK_SPEED + " REAL DEFAULT " + SPEED_USE_GLOBAL + ","
            + KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0,"
            + KEY_FEED_SKIP_ENDING + " INTEGER DEFAULT 0,"
            + KEY_FEED_VOLUME_ADAPTION + " INTEGER DEFAULT 0,"
            + KEY_FEED_TAGS + " TEXT,"
            + KEY_EPISODE_NOTIFICATION + " INTEGER DEFAULT 0,"
            + KEY_FEED_SKIP_INTRO + " INTEGER DEFAULT 0,"
            + KEY_FEED_AD_SKIP + " INTEGER DEFAULT 1)";

    static final String CREATE_TABLE_FEED_ITEMS = "CREATE TABLE "
            + TABLE_NAME_FEED_ITEMS + " (" + TABLE_PRIMARY_KEY
            + KEY_READ + " INTEGER," + KEY_LINK + " TEXT,"
            + KEY_TITLE + " TEXT," + KEY_PUBDATE + " INTEGER,"
            + KEY_MEDIA + " INTEGER," + KEY_FEED + " INTEGER,"
            + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
            + KEY_AUTO_DOWNLOAD_ATTEMPTS + " INTEGER,"
            + KEY_IMAGE_URL + " TEXT,"
            + KEY_HAS_CHAPTERS + " INTEGER," + KEY_ITEM_IDENTIFIER + " TEXT,"
            + KEY_PODCASTINDEX_CHAPTER_URL + " TEXT)";

    static final String CREATE_TABLE_FEED_MEDIA = "CREATE TABLE "
            + TABLE_NAME_FEED_MEDIA + " (" + TABLE_PRIMARY_KEY + KEY_DURATION
            + " INTEGER," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL
            + " TEXT," + KEY_DOWNLOADED + " INTEGER," + KEY_POSITION
            + " INTEGER," + KEY_SIZE + " INTEGER," + KEY_MIME_TYPE + " TEXT,"
            + KEY_FEEDITEM + " INTEGER,"
            + KEY_PLAYBACK_COMPLETION_DATE + " INTEGER,"
            + KEY_HAS_EMBEDDED_PICTURE + " INTEGER,"
            + KEY_LAST_PLAYED_TIME + " INTEGER,"
            + KEY_PLAYED_DURATION + " INTEGER" + ")";

    static final String CREATE_TABLE_DOWNLOAD_LOG = "CREATE TABLE "
            + TABLE_NAME_DOWNLOAD_LOG + " (" + TABLE_PRIMARY_KEY + KEY_FEEDFILE
            + " INTEGER," + KEY_FEEDFILETYPE + " INTEGER," + KEY_REASON
            + " INTEGER," + KEY_SUCCESSFUL + " INTEGER," + KEY_COMPLETION_DATE
            + " INTEGER," + KEY_DOWNLOADSTATUS_TITLE + " TEXT,"
            + KEY_REASON_DETAILED + " TEXT)";

    static final String CREATE_TABLE_QUEUE = "CREATE TABLE "
            + TABLE_NAME_QUEUE + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
            + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)";

    static final String CREATE_TABLE_SIMPLECHAPTERS = "CREATE TABLE "
            + TABLE_NAME_SIMPLECHAPTERS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_START + " INTEGER," + KEY_FEEDITEM + " INTEGER,"
            + KEY_IMAGE_URL + " TEXT," + KEY_LINK + " TEXT)";

    /**
     * One row per advertisement range of one episode. Written by the detector, the chapter
     * scanner and the player's "mark as ad" action; read back whenever an episode starts.
     */
    static final String CREATE_TABLE_AD_SEGMENTS = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME_AD_SEGMENTS + " (" + TABLE_PRIMARY_KEY
            + KEY_FEEDITEM + " INTEGER," + KEY_AD_START_MS + " INTEGER,"
            + KEY_AD_END_MS + " INTEGER," + KEY_AD_SOURCE + " INTEGER,"
            + KEY_AD_CONFIDENCE + " REAL,"
            + KEY_AD_ENABLED + " INTEGER DEFAULT 1)";

    // SQL Statements for creating indexes
    static final String CREATE_INDEX_FEEDITEMS_FEED = "CREATE INDEX "
            + TABLE_NAME_FEED_ITEMS + "_" + KEY_FEED + " ON " + TABLE_NAME_FEED_ITEMS + " ("
            + KEY_FEED + ")";

    static final String CREATE_INDEX_FEEDITEMS_PUBDATE = "CREATE INDEX "
            + TABLE_NAME_FEED_ITEMS + "_" + KEY_PUBDATE + " ON " + TABLE_NAME_FEED_ITEMS + " ("
            + KEY_PUBDATE + ")";

    static final String CREATE_INDEX_FEEDITEMS_READ = "CREATE INDEX "
            + TABLE_NAME_FEED_ITEMS + "_" + KEY_READ + " ON " + TABLE_NAME_FEED_ITEMS + " ("
            + KEY_READ + ")";

    static final String CREATE_INDEX_QUEUE_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_QUEUE + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_QUEUE + " ("
            + KEY_FEEDITEM + ")";

    static final String CREATE_INDEX_FEEDMEDIA_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_FEED_MEDIA + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_FEED_MEDIA + " ("
            + KEY_FEEDITEM + ")";

    static final String CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_SIMPLECHAPTERS + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_SIMPLECHAPTERS + " ("
            + KEY_FEEDITEM + ")";

    static final String CREATE_INDEX_AD_SEGMENTS_FEEDITEM = "CREATE INDEX IF NOT EXISTS "
            + TABLE_NAME_AD_SEGMENTS + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_AD_SEGMENTS + " ("
            + KEY_FEEDITEM + ")";

    static final String CREATE_TABLE_FAVORITES = "CREATE TABLE "
            + TABLE_NAME_FAVORITES + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
            + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)";

    /**
     * Select all columns from the feed-table
     */
    static final String[] FEED_SEL_STD = {
            TABLE_NAME_FEEDS + "." + KEY_TITLE,
            TABLE_NAME_FEEDS + "." + KEY_CUSTOM_TITLE,
            TABLE_NAME_FEEDS + "." + KEY_FILE_URL,
            TABLE_NAME_FEEDS + "." + KEY_LANGUAGE,
            TABLE_NAME_FEEDS + "." + KEY_LINK,
            TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL,
            TABLE_NAME_FEEDS + "." + KEY_PAYMENT_LINK,
            TABLE_NAME_FEEDS + "." + KEY_ID,
            TABLE_NAME_FEEDS + "." + KEY_AUTO_DOWNLOAD_ENABLED,
            TABLE_NAME_FEEDS + "." + KEY_DESCRIPTION,
            TABLE_NAME_FEEDS + "." + KEY_LASTUPDATE,
            TABLE_NAME_FEEDS + "." + KEY_TYPE,
            TABLE_NAME_FEEDS + "." + KEY_AUTHOR,
            TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL,
            TABLE_NAME_FEEDS + "." + KEY_SKIP_SILENCE_ENABLED,
            TABLE_NAME_FEEDS + "." + KEY_USE_FEED_EFFECT,
            TABLE_NAME_FEEDS + "." + KEY_IS_SUBSCRIBED,
            TABLE_NAME_FEEDS + "." + KEY_IS_PAGED,
            TABLE_NAME_FEEDS + "." + KEY_LOUDNESS_ENABLED,
            TABLE_NAME_FEEDS + "." + KEY_FEED_IDENTIFIER,
            TABLE_NAME_FEEDS + "." + KEY_KEEP_UPDATED,
            TABLE_NAME_FEEDS + "." + KEY_ITUNES_FEED_ID,
            TABLE_NAME_FEEDS + "." + KEY_NEXT_PAGE_LINK,
            TABLE_NAME_FEEDS + "." + KEY_MONO_ENABLED,
            TABLE_NAME_FEEDS + "." + KEY_PASSWORD,
            TABLE_NAME_FEEDS + "." + KEY_HIDE,
            TABLE_NAME_FEEDS + "." + KEY_SORT_ORDER,
            TABLE_NAME_FEEDS + "." + KEY_LAST_UPDATE_FAILED,
            TABLE_NAME_FEEDS + "." + KEY_AUTO_DELETE_ACTION,
            TABLE_NAME_FEEDS + "." + KEY_INCLUDE_FILTER,
            TABLE_NAME_FEEDS + "." + KEY_FEED_VOLUME_ADAPTION,
            TABLE_NAME_FEEDS + "." + KEY_USERNAME,
            TABLE_NAME_FEEDS + "." + KEY_MINIMAL_DURATION_FILTER,
            TABLE_NAME_FEEDS + "." + KEY_FEED_PLAYBACK_SPEED,
            TABLE_NAME_FEEDS + "." + KEY_FEED_TAGS,
            TABLE_NAME_FEEDS + "." + KEY_EXCLUDE_FILTER,
            TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_INTRO,
            TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_ENDING,
            TABLE_NAME_FEEDS + "." + KEY_EPISODE_NOTIFICATION,
            TABLE_NAME_FEEDS + "." + KEY_FEED_AD_SKIP
    };

    /**
     * All the tables in the database
     */
    static final String[] ALL_TABLES = {
            TABLE_NAME_FEEDS,
            TABLE_NAME_FEED_ITEMS,
            TABLE_NAME_FEED_MEDIA,
            TABLE_NAME_DOWNLOAD_LOG,
            TABLE_NAME_QUEUE,
            TABLE_NAME_SIMPLECHAPTERS,
            TABLE_NAME_FAVORITES,
            TABLE_NAME_AD_SEGMENTS
    };

    public static final String SELECT_KEY_ITEM_ID = "item_id";
    public static final String SELECT_KEY_MEDIA_ID = "media_id";

    static final String KEYS_FEED_ITEM_WITHOUT_DESCRIPTION =
            TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " AS " + SELECT_KEY_ITEM_ID + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_MEDIA + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_LINK + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_PAYMENT_LINK + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_HAS_CHAPTERS + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_AUTO_DOWNLOAD_ATTEMPTS + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE_URL + ", "
            + TABLE_NAME_FEED_ITEMS + "." + KEY_PODCASTINDEX_CHAPTER_URL;

    static final String KEYS_FEED_MEDIA =
            TABLE_NAME_FEED_MEDIA + "." + KEY_ID + " AS " + SELECT_KEY_MEDIA_ID + ", "
            + TABLE_NAME_FEED_MEDIA + "." + KEY_FILE_URL + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_URL + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + ", "
            + TABLE_NAME_FEED_MEDIA + "." + KEY_MIME_TYPE + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYBACK_COMPLETION_DATE + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + ", "
            + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + ", "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_HAS_EMBEDDED_PICTURE + ", "
            + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME;

    static final String JOIN_FEED_ITEM_AND_MEDIA = " LEFT JOIN " + TABLE_NAME_FEED_MEDIA
            + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + " ";

    static final String SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION =
            "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_DESCRIPTION
            + " FROM " + TABLE_NAME_FEED_ITEMS
            + JOIN_FEED_ITEM_AND_MEDIA;
    static final String SELECT_FEED_ITEMS_AND_MEDIA =
            "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA
            + " FROM " + TABLE_NAME_FEED_ITEMS
            + JOIN_FEED_ITEM_AND_MEDIA;

    static final String JOIN_FEED_ITEM_AND_MEDIA_AND_DOWNLOADLOG = " LEFT JOIN " + TABLE_NAME_DOWNLOAD_LOG
            + " ON " + TABLE_NAME_DOWNLOAD_LOG + "." + KEY_FEEDFILE  + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_ID + " LEFT JOIN " + TABLE_NAME_FEED_MEDIA
            + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + " ";

    static final String JOIN_FEED_ITEM_AND_FEED = " LEFT JOIN " + TABLE_NAME_FEEDS
            + " ON " + TABLE_NAME_FEEDS + "." + KEY_ID + " = " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + " ";

    static final String SELECT_FEED_ITEMS_AND_MEDIA_AND_DOWNLOADLOG =
            "SELECT " + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                    + " FROM " + TABLE_NAME_FEED_ITEMS
                    + JOIN_FEED_ITEM_AND_MEDIA;

    private DbSchema() {
    }
}
