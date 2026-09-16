package allen.town.core.service

import android.content.Context
import com.wyjson.router.interfaces.IService

interface PayService : IService {
    fun isAliPay(): Boolean
    fun isAdBlocker(): Boolean
    fun isPurchase(context: Context?,gotoPro:Boolean = true): Boolean
    fun setPurchase(purchase: Boolean)
    fun setRemoveAdPurchase(purchase: Boolean)

    //对于新app这里是订阅表，旧app就是完整的表，就是为了兼容focusreader的数据库结构
    fun purchaseDbName(): String
    fun dbVersion(): Int

    //新app这里返回空既可
    fun mergeDbTables(): Array<Class<out Any>>?{
        return null
    }

    fun isThemeFree(): Boolean{
        return false
    }
}