package allen.town.focus_common.http

import androidx.annotation.Keep

@Keep
abstract class LeanResponseBase {
    var code: String? = null
    var error: String? = null
}