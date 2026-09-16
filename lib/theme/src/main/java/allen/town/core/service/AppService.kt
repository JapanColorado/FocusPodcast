package allen.town.core.service

import com.wyjson.router.interfaces.IService


interface AppService : IService {
    fun isForeground(): Boolean
}