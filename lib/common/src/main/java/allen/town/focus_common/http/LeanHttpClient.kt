package allen.town.focus_common.http

import allen.town.focus_common.http.bean.*
import io.reactivex.Observable

object LeanHttpClient {

    @JvmStatic
    fun getUpgradeInfo(objectChinaId: String ,objectPlayId: String,isChina: Boolean): Observable<LeanUpgradeBean?> {
        if (isChina)
            return LeanHttpClientBase.getInstance().getUpgradeInfoById(objectChinaId)
        else return /*getUpgradeGoogleInfoById(objectPlayId)*/Observable.just(LeanUpgradeBean(null,null,null,0,null,false,null,null))
    }

    @JvmStatic
    private fun getUpgradeGoogleInfoById(objectId: String): Observable<LeanUpgradeBean?> {
        return LeanHttpClientBase.getInstance().getUpgradeGoogleInfoById(objectId)
    }

    //reader的 begin
    @JvmStatic
    fun getFeedlyInfo(): Observable<LeanQueryResponseBase<LeanFeelyKeyBean?>> {
        return LeanHttpClientBase.getInstance().getFeedlyInfo()
    }


    @JvmStatic
    fun getNotify(): Observable<LeanQueryResponseBase<LeanNotifyBean?>> {
        return LeanHttpClientBase.getInstance().getNotify()
    }
    //reader的 end


    //podcast的
    @JvmStatic
    fun getNewNotify(objectId: String): Observable<LeanNotifyBean?> {
        return LeanHttpClientBase.getInstance().getNewNotify(objectId)
    }

    /**
     * admob 广告信息，用海外版
     */
    @JvmStatic
    fun getAdmob(objectId: String): Observable<LeanAdmobBean?> {
        return LeanHttpClientBase.getAbroadInstance().getAdmob(objectId)
    }


    //twitter
    @JvmStatic
    fun getTwitterApiKey(objectId: String): Observable<LeanTwitterApiKeyBean?> {
        return LeanHttpClientBase.getAbroadInstance().getTwitterApiKey(objectId)
    }

}

