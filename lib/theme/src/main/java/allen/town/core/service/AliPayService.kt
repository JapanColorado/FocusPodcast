package allen.town.core.service

import com.wyjson.router.interfaces.IService


interface AliPayService : IService {


    //价格
    fun getWeeklyPrice(): String
    fun getMonthPrice(): String
    fun getQuarterlyPrice(): String
    fun getYearlyPrice(): String
    fun getRemoveAdPrice(): String

    //key相关
    fun getAppId(): String
    fun getPid():String
    //沙箱
    fun getSandboxPrivateKey(): String
    fun getSandboxPublicKey(): String
    fun getSandboxAlipayPublicKey(): String
    //生产
    fun getPrivateKey(): String
    fun getPublicKey(): String
    fun getAlipayPublicKey(): String

}