package allen.town.focus_common.util


import android.net.Uri
import android.text.TextUtils
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Type
import java.util.*

object JsonHelper {

    private val gson = Gson()

    internal var stringType = object : TypeToken<ArrayList<String>>() {

    }.type

    @JvmStatic
    fun toJSONString(`object`: Any?): String {
        if (`object` == null) {
            //不然null会返回“null”
            return ""
        }
        try {
            return gson.toJson(`object`)
        } catch (e: Exception) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        } catch (e: Throwable) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        }

    }

    @JvmStatic
    fun toJSONString(`object`: Any?, typeOfT: Type): String {
        if (`object` == null) {
            //不然null会返回“null”
            return ""
        }
        try {
            return gson.toJson(`object`, typeOfT)
        } catch (e: Exception) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        } catch (e: Throwable) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        }

    }

    @JvmStatic
    fun <T> parseObject(text: String?, clazz: Class<T>?): T? {

        try {
            return gson.fromJson(text, clazz!!)
        } catch (e: Exception) {
            Timber.e(e, "parseObject failed cause")
            return null
        } catch (e: Throwable) {
            Timber.e(e, "parseObject failed cause")
            return null
        }

    }

    @JvmStatic
    fun <T> parseObjectList(text: String?, typeOfT: Type): T? {

        try {
            return gson.fromJson<T>(text, typeOfT)
        } catch (e: Exception) {
            Timber.e(e, "parseObjectList failed cause")
            return null
        } catch (e: Throwable) {
            Timber.e(e, "toJSONString failed cause")
            return null
        }

    }

    @JvmStatic
    fun parseStringList(json: String?): List<String> {
        try {
            var list: List<String>? = gson.fromJson<List<String>>(json, stringType)
            if (list == null) {
                list = ArrayList()
            }
            return list
        } catch (e: Exception) {
            Timber.e(e, "parseObjectList failed cause")
            return ArrayList()
        } catch (e: Throwable) {
            Timber.e(e, "toJSONString failed cause")
            return ArrayList()
        }

    }

    /**
     * @param json is a string such as [12, 2, 32]
     * @return int[]
     */
    @JvmStatic
    fun parseIntArray(json: String?): IntArray? {
        if (json != null && json.length > 0) {
            val ja: JSONArray
            try {
                ja = JSONArray(json)
                val res = IntArray(ja.length())
                for (i in res.indices) {
                    res[i] = ja.getInt(i)
                }
                return res
            } catch (e: JSONException) {
                Timber.e(e, "parseIntArray failed cause")
            }

        }
        return null
    }

    @JvmStatic
    fun parseStringArray(json: String?): Array<String?>? {
        if (json != null && json.length > 0) {
            val ja: JSONArray
            try {
                ja = JSONArray(json)
                val res = arrayOfNulls<String>(ja.length())
                for (i in res.indices) {
                    res[i] = ja.getString(i)
                }
                return res
            } catch (e: JSONException) {
                Timber.e(e, "parseStringArray failed cause")
            }

        }
        return null
    }

    @JvmStatic
    fun parseIDArray(json: String?): IntArray? {
        if (json != null && json.length > 0) {
            val ja: JSONArray
            try {
                ja = JSONArray(json)
                val res = IntArray(ja.length())
                for (i in res.indices) {
                    val jsonObject = ja.getJSONObject(i)
                    res[i] = jsonObject.getInt("id")
                }
                return res
            } catch (e: JSONException) {
                Timber.e(e, "parseIntArray failed cause")
            }

        }
        return null
    }

    @JvmStatic
    fun parseID(json: String?, key: String): String? {
        if (json != null && json.length > 0) {
            val jsonObject: JSONObject
            try {
                jsonObject = JSONObject(json)
                if (!TextUtils.isEmpty(key)) {
                    return jsonObject.optString(key)
                }
            } catch (e: JSONException) {
                Timber.e(e, "parseIntArray failed cause")
            }

        }
        return null
    }

    /**
     * for serializer Uri
     */
    private class UriSerializer : JsonSerializer<Uri> {
        override fun serialize(
            src: Uri, typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }

    /**
     * for deserializer Uri
     */
    private class UriDeserializer : JsonDeserializer<Uri> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            src: JsonElement, srcType: Type,
            context: JsonDeserializationContext
        ): Uri {
            return Uri.parse(src.asString)
        }
    }

    @JvmStatic
    fun parseMap(json: String?, type: Type): Map<String, Int>? {
        try {
            return gson.fromJson(json, type)
        } catch (e: Exception) {
            Timber.e(e, "parseObjectList failed cause")
            return null
        } catch (e: Throwable) {
            Timber.e(e, "toJSONString failed cause")
            return null
        }
    }

    @JvmStatic
    fun getGson(): Gson {
        return gson
    }

}
