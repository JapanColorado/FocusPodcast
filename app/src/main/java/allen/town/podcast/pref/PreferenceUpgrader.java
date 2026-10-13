package allen.town.podcast.pref;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import allen.town.podcast.BuildConfig;
import allen.town.podcast.common.model.CategoryInfo;
import allen.town.podcast.common.util.BasePreferenceUtil;
import allen.town.podcast.core.pref.Prefs;
import allen.town.podcast.core.util.download.AutoUpdateManager;

/**
 * Checks for updates
 */
public class PreferenceUpgrader {
    private static final String PREF_CONFIGURED_VERSION = "version_code";
    private static final String PREF_NAME = "app_version";

    private static SharedPreferences prefs;

    public static void checkUpgrades(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1);
        int newVersion = BuildConfig.VERSION_CODE;

        // Not tied to a version code: debug builds reuse it, and the steps are idempotent and cheap.
        syncLibraryCategories();
        Prefs.migrateAudiobookShelfStatus();

        if (oldVersion != newVersion) {
            AutoUpdateManager.restartUpdateAlarm(context);

            upgrade(oldVersion, context);
            upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply();
        }
    }

    /**
     * The drawer pages are stored as a list ({@link BasePreferenceUtil#getLibraryCategory()}) once
     * the user reorders or hides one, so a page added in a later version would never show up for
     * them, not even in the "Categories" dialog, and a page dropped from the drawer (Discover,
     * Audiobooks, History, the subscription list) would linger. Drops stored pages that are no longer defaults, inserts every
     * default page missing from the stored list right after the default page that precedes it,
     * with its default visibility, and leaves the user's order and choices alone.
     */
    private static void syncLibraryCategories() {
        List<CategoryInfo> defaults = BasePreferenceUtil.INSTANCE.getDefaultCategories();
        List<CategoryInfo> merged = mergeCategories(BasePreferenceUtil.getLibraryCategory(), defaults);
        if (merged != null) {
            BasePreferenceUtil.setLibraryCategory(merged);
        }
    }

    /**
     * @return the stored list without entries that are no longer defaults and with the missing
     *     default entries inserted, or null when nothing changed
     */
    @Nullable
    static List<CategoryInfo> mergeCategories(@NonNull List<CategoryInfo> stored,
                                              @NonNull List<CategoryInfo> defaults) {
        Set<String> defaultTags = new HashSet<>();
        for (CategoryInfo info : defaults) {
            defaultTags.add(info.getTag());
        }
        Set<String> storedTags = new HashSet<>();
        List<CategoryInfo> result = new ArrayList<>();
        boolean changed = false;
        for (CategoryInfo info : stored) {
            if (defaultTags.contains(info.getTag())) {
                storedTags.add(info.getTag());
                result.add(info);
            } else {
                changed = true;
            }
        }
        for (int i = 0; i < defaults.size(); i++) {
            CategoryInfo missing = defaults.get(i);
            if (storedTags.contains(missing.getTag())) {
                continue;
            }
            int insertAt = 0;
            for (int j = i - 1; j >= 0; j--) {
                int index = indexOfTag(result, defaults.get(j).getTag());
                if (index >= 0) {
                    insertAt = index + 1;
                    break;
                }
            }
            result.add(insertAt, new CategoryInfo(missing.getTag(), missing.getVisible(), missing.getDragAble()));
            storedTags.add(missing.getTag());
            changed = true;
        }
        if (changed && !hasVisiblePage(result)) {
            // the only visible page was dropped: show the first default page so the drawer is not empty
            result.get(indexOfTag(result, defaults.get(0).getTag())).setVisible(true);
        }
        return changed ? result : null;
    }

    private static boolean hasVisiblePage(List<CategoryInfo> list) {
        for (CategoryInfo info : list) {
            if (info.getVisible()) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfTag(List<CategoryInfo> list, String tag) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getTag().equals(tag)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Migrates preferences from older versions
     * @param oldVersion
     * @param context
     */
    private static void upgrade(int oldVersion, Context context) {
        if (oldVersion == -1) {
            //New installation
            return;
        }
/*        if (oldVersion < 1070196) {
            // migrate episode cleanup value (unit changed from days to hours)
            int oldValueInDays = UserPreferences.getEpisodeCleanupValue();
            if (oldValueInDays > 0) {
                UserPreferences.setEpisodeCleanupValue(oldValueInDays * 24);
            } // else 0 or special negative values, no change needed
        }
        if (oldVersion < 1070197) {
            if (prefs.getBoolean("prefMobileUpdate", false)) {
                prefs.edit().putString("prefMobileUpdateAllowed", "everything").apply();
            }
        }
        if (oldVersion < 1070300) {
            prefs.edit().putString(UserPreferences.PREF_MEDIA_PLAYER,
                    UserPreferences.PREF_MEDIA_PLAYER_EXOPLAYER).apply();

            if (prefs.getBoolean("prefEnableAutoDownloadOnMobile", false)) {
                UserPreferences.setAllowMobileAutoDownload(true);
            }
            switch (prefs.getString("prefMobileUpdateAllowed", "images")) {
                case "everything":
                    UserPreferences.setAllowMobileFeedRefresh(true);
                    UserPreferences.setAllowMobileEpisodeDownload(true);
                    UserPreferences.setAllowMobileImages(true);
                    break;
                case "images":
                    UserPreferences.setAllowMobileImages(true);
                    break;
                case "nothing":
                    UserPreferences.setAllowMobileImages(false);
                    break;
            }
        }
        if (oldVersion < 2010300) {
            // Migrate hardware button preferences
            if (prefs.getBoolean("pref_hardware_previous_buttonRestarts", false)) {
                prefs.edit().putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS)).apply();
            }
        }
        if (oldVersion < 2050000) {
            prefs.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true).apply();
        }*/
    }
}
