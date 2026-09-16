package allen.town.focus_common.http

import allen.town.focus_common.BuildConfig
import allen.town.focus_common.http.data.AutoParcelAdapterFactory
import allen.town.focus_common.http.service.LeanService
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.*

object LeanHttpClientBase {
    private var leanService: LeanService? = null
    const val ENDPOINT = "https://lean.api.focus.hk.cn/1.1/classes/"

    private var abroadLeanService: LeanService? = null
    const val ABROAD_ENDPOINT = "https://juynt4gt.api.lncldglobal.com/1.1/classes/"


    /**
     * 国内版
     */
    @JvmStatic
    fun getInstance(): LeanService {
        if (leanService == null) {
            leanService = getBaseLeanService(false)
        }

        return leanService!!

    }

    /**
     * 海外版，显示了国内ip返回了403无法正常使用，所以测试时需要翻墙
     */
    @JvmStatic
    fun getAbroadInstance(): LeanService {
        if (abroadLeanService == null) {
            abroadLeanService = getBaseLeanService(true)
        }

        return abroadLeanService!!

    }


    private fun getBaseLeanService(isAbroad: Boolean = false): LeanService {
        return Retrofit.Builder().baseUrl(if (isAbroad) ABROAD_ENDPOINT else ENDPOINT)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                        .registerTypeAdapterFactory(AutoParcelAdapterFactory())
                        .registerTypeAdapter(Date::class.java, object : TypeAdapter<Date?>() {
                            @Throws(IOException::class)
                            override fun write(jsonWriter: JsonWriter, date: Date?) {
                                if (date == null) {
                                    jsonWriter.nullValue()
                                } else {
                                    jsonWriter.value((date.time / 1000).toString())
                                }
                            }

                            @Throws(IOException::class)  // com.google.gson.TypeAdapter
                            override fun read(jsonReader: JsonReader): Date? {
                                if (jsonReader.peek() != JsonToken.NULL) {
                                    return Date(jsonReader.nextLong() * 1000)
                                }
                                jsonReader.nextNull()
                                return null
                            }
                        }).create()
                )
            ).addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                // okhttp3.Interceptor
                val originalResponse =
                    chain.proceed(if (isAbroad) getAbroadAuthHeader(chain) else getAuthHeader(chain))
                return@addInterceptor originalResponse
            }.build()).build().create(LeanService::class.java)


    }


    private fun getAuthHeader(chain: Interceptor.Chain): Request {
        return chain.request().newBuilder()
            .addHeader("X-LC-Id", BuildConfig.LEAN_ID)
            .addHeader("X-LC-Key", BuildConfig.LEAN_KEY)
            .build()
    }

    private fun getAbroadAuthHeader(chain: Interceptor.Chain): Request {
        return chain.request().newBuilder()
            .addHeader("X-LC-Id", BuildConfig.LEAN_ABROAD_ID)
            .addHeader("X-LC-Key", BuildConfig.LEAN_ABROAD_KEY)
            .build()
    }
}

