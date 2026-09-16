package allen.town.focus_common.http.service

import allen.town.focus_common.http.LeanQueryResponseBase
import allen.town.focus_common.http.bean.*
import retrofit2.http.GET
import retrofit2.http.Path
import io.reactivex.Observable

interface LeanService {
    @GET("upgrade/{objectId}")
    fun getUpgradeInfoById(@Path("objectId") objectId: String?): Observable<LeanUpgradeBean?>

    @GET("upgrade_google/{objectId}")
    fun getUpgradeGoogleInfoById(@Path("objectId") objectId: String?): Observable<LeanUpgradeBean?>

    @GET("feedly_key")
    fun getFeedlyInfo(): Observable<LeanQueryResponseBase<LeanFeelyKeyBean?>>

    @GET("notify")
    fun getNotify(): Observable<LeanQueryResponseBase<LeanNotifyBean?>>

    @GET("notify_new/{objectId}")
    fun getNewNotify(@Path("objectId") objectId: String?): Observable<LeanNotifyBean?>

    @GET("ads_id/{objectId}")
    fun getAdmob(@Path("objectId") objectId: String?): Observable<LeanAdmobBean?>

    @GET("twitter_api_key/{objectId}")
    fun getTwitterApiKey(@Path("objectId") objectId: String?): Observable<LeanTwitterApiKeyBean?>
}