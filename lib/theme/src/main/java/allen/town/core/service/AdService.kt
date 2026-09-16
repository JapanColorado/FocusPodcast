package allen.town.core.service

import com.wyjson.router.interfaces.IService


interface AdService : IService {
    fun getOpenAdUnitId(): String
    fun getRewardedAdUnitId(): String
    fun getBannerAdUnitId(): String
    fun getInterstitialAdUnitId(): String
}