package allen.town.podcast.common.util


import android.text.TextUtils
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Type
import java.util.*

// detekt: Gson turns malformed input into a wide range of unchecked exceptions. Every
// method here documents an empty/null fallback rather than propagating a parse failure.
@Suppress("TooGenericExceptionCaught")
object JsonHelper {

    private val gson = Gson()

    internal var stringType = object : TypeToken<ArrayList<String>>() {

    }.type

    @JvmStatic
    fun toJSONString(`object`: Any?): String {
        if (`object` == null) {
            // Otherwise null is serialized as the string "null"
            return ""
        }
        try {
            return gson.toJson(`object`)
        } catch (e: Exception) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        }

    }

    @JvmStatic
    fun toJSONString(`object`: Any?, typeOfT: Type): String {
        if (`object` == null) {
            // Otherwise null is serialized as the string "null"
            return ""
        }
        try {
            return gson.toJson(`object`, typeOfT)
        } catch (e: Exception) {
            Timber.e(e, "toJSONString failed cause")
            return ""
        }

    }

    @JvmStatic
    fun <T> parseObject(text: String?, clazz: Class<T>?): T? {

        val type = clazz ?: return null
        try {
            return gson.fromJson(text, type)
        } catch (e: Exception) {
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
                // Malformed JSON: null is the documented "could not parse" answer, see below.
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
                // Malformed JSON: null is the documented "could not parse" answer, see below.
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
                // Malformed JSON: null is the documented "could not parse" answer, see below.
                Timber.e(e, "parseIDArray failed cause")
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
                // Malformed JSON: null is the documented "could not parse" answer, see below.
                Timber.e(e, "parseID failed cause")
            }

        }
        return null
    }

    @JvmStatic
    fun parseMap(json: String?, type: Type): Map<String, Int>? {
        try {
            return gson.fromJson(json, type)
        } catch (e: Exception) {
            Timber.e(e, "parseObjectList failed cause")
            return null
        }
    }

    @JvmStatic
    fun getGson(): Gson {
        return gson
    }

}
