package allen.town.focus_common.http.bean

import androidx.annotation.Keep

@Keep
data class LeanNotifyBean(val content_cn: String?, val content_en: String?, val version: Int, val enable: Boolean)
