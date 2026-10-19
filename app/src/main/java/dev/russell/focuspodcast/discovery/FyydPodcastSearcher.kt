package dev.russell.focuspodcast.discovery

import dev.russell.focuspodcast.core.service.download.PodcastHttpClient
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Searches fyyd.de. This used to go through the fyydlin library, whose Kotlin model classes
 * Moshi 1.15 refuses to read without kotlin-reflect; the API is one GET, so it is now read
 * with org.json like the iTunes search.
 */
class FyydPodcastSearcher : PodcastSearcher {
    override fun search(query: String?): Single<List<PodcastSearchResult?>?>? {
        // Nothing to search for, so answer with no hits rather than calling the API.
        val searchQuery = query ?: return Single.just<List<PodcastSearchResult?>?>(emptyList())
        return Single.create(
            SingleOnSubscribe { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
                val url = SEARCH_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("term", searchQuery)
                    .addQueryParameter("count", RESULT_COUNT.toString())
                    .build()
                val request = Request.Builder().url(url).build()
                try {
                    PodcastHttpClient.getHttpClient().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            subscriber.onError(IOException(response.toString()))
                            return@SingleOnSubscribe
                        }
                        val hits = JSONObject(response.body.string()).optJSONArray("data")
                        val searchResults = ArrayList<PodcastSearchResult?>()
                        if (hits != null) {
                            for (i in 0 until hits.length()) {
                                searchResults.add(PodcastSearchResult.fromFyyd(hits.getJSONObject(i)))
                            }
                        }
                        subscriber.onSuccess(searchResults)
                    }
                } catch (e: IOException) {
                    subscriber.tryOnError(e)
                } catch (e: JSONException) {
                    subscriber.tryOnError(e)
                }
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String> {
        return Single.just(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override val name: String
        get() = "fyyd"
    companion object{
        const val SEARCH_ENGINE_TAG = "fyyd_podcast_search"
        private const val SEARCH_URL = "https://api.fyyd.de/0.2/search/podcast"
        private const val RESULT_COUNT = 10
    }
}
