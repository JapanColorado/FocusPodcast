package allen.town.focus_common.inappupdate;

import android.content.Context;
import android.content.SharedPreferences;

public class InAppLastUpdatePreference {
    private static final String LAST_CHECKED_APP_VERSION = "last_play_in_app_checked_app_version";
    private final SharedPreferences preferences;

    public InAppLastUpdatePreference(Context context) {
        this.preferences = context.getSharedPreferences("in_app_sp", 0);
    }


    public int getVersionCode() {
        return this.preferences.getInt(LAST_CHECKED_APP_VERSION, 0);
    }

    /**
     * 检查该版本是否被用户取消过更新
     *
     * @param newVersion
     * @return
     */
    public boolean lastVersionChecked(int newVersion) {
        return getVersionCode() == newVersion;
    }

    public void setVersionCode(int versionCode) {
        this.preferences.edit().putInt(LAST_CHECKED_APP_VERSION, versionCode).commit();
    }

}
