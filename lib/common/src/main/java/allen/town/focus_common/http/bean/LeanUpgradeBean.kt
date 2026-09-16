package allen.town.focus_common.http.bean

import allen.town.focus_common.http.LeanResponseBase
import androidx.annotation.Keep

@Keep
data class LeanUpgradeBean(val change_log_en: String?, val change_log_cn: String?,
                           val version_name: String?, val version_code: Int, val download_url: String?,
                           val force_upgrade: Boolean, val app_size: String?, val md5: String?) : LeanResponseBase()
