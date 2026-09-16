package allen.town.focus_common.http.bean

import androidx.annotation.Keep

@Keep
data class LocalTwitterApiKeyBean(
    val consumer_key: String,
    val consumer_secret: String,
    val callbackUrl: String?,
    val apiVersion: Int,
    val auth_type: String,
    val same_oauth_url: Boolean,
    val no_version_suffix: Boolean,
    val clientName: String?,
    val versionName: String?,
    val twitterApiVersion: String?,
    val internalVersionName: String?,
    val platformName: String?,
    val platformVersion: String?,
    val platformArchitecture: String?,
    val client_type: String
)
