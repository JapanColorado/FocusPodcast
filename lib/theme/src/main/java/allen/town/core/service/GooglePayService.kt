package allen.town.core.service

import com.wyjson.router.interfaces.IService


interface GooglePayService : IService {

    //price
    fun getMonthId(): String
    fun getQuarterlyId(): String
    fun getYearlyId(): String
    fun getWeeklyId(): String
    fun getRemoveAdsId(): List<String>

    //very important
    fun getPublicKey(): String

    //Compatible with older versions
    fun getOldMonthId(): String{
        return ""
    }
    fun getOldQuarterlyId(): String{
        return ""
    }
    fun getOldYearlyId(): String{
        return ""
    }

}