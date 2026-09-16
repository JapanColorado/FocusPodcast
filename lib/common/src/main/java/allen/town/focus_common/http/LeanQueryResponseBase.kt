package allen.town.focus_common.http

import androidx.annotation.Keep

@Keep
class LeanQueryResponseBase<T> : LeanResponseBase() {
    var results: ArrayList<T>? = null
}