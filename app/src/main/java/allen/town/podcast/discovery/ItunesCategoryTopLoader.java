package allen.town.podcast.discovery;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import allen.town.podcast.R;
import allen.town.podcast.core.service.download.PodcastHttpClient;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the iTunes top list
 */
public class ItunesCategoryTopLoader {
    private static final String TAG = "ITunesTopListLoader";
    private final Context context;
    public static final String PREF_KEY_COUNTRY_CODE = "country_code";
    public static final String PREF_KEY_CATEGORY_CODE = "category_code";
    public static final String PREFS = "CountryRegionPrefs";
    public static final String DISCOVER_HIDE_FAKE_COUNTRY_CODE = "00";
    public static final String COUNTRY_CODE_UNSET = "99";

    public ItunesCategoryTopLoader(Context context) {
        this.context = context;
    }

    public Single<List<PodcastSearchResult>> loadToplist(String country, int limit, int categoryCode) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) emitter -> {
            OkHttpClient client = PodcastHttpClient.getHttpClient();
            String feedString;
            String loadCountry = country;
            if (COUNTRY_CODE_UNSET.equals(country)) {
                loadCountry = Locale.getDefault().getCountry();
            }
            try {
                feedString = getTopListFeed(client, loadCountry, limit, categoryCode);
            } catch (IOException e) {
                if (COUNTRY_CODE_UNSET.equals(country)) {
                    feedString = getTopListFeed(client, "US", limit, categoryCode);
                } else {
                    emitter.onError(e);
                    return;
                }
            }

            List<PodcastSearchResult> podcasts = parseFeed(feedString);
            emitter.onSuccess(podcasts);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }


    /**
     * @param client
     * @param country
     * @param limit
     * @return
     * @throws IOException
     */
    private String getTopListFeed(OkHttpClient client, String country, int limit, int categoryCode) throws IOException {
        /*https://itunes.apple.com/CN/rss/toppodcasts/genre=1306/limit=12/json - the contents differ per parameter. "genre" selects the genre and is optional; presumably it defaults to "all genres", which does not exist as its own id.

        1301 Arts
        1302 Society & Culture
        1303 Comedy
        1304 Education
        1305 Kids & Family
        1306 Health & Fitness
        1307
        1309 TV & Film
        1310 Music
        1311 News & Politics
        1314 Religion & Spirituality
        1315 Science & Medicine (apparently wrong)
        1316 Sports & Recreation (apparently wrong)
        1318 Technology
        1320 Travel
        1321 Business
        1323 Games & Hobbies (apparently wrong)
        1324 Society & Culture
        1325 Government (apparently wrong)
        The new API is documented at https://affiliate.itunes.apple.com/resources/documentation/itunes-store-web-service-search-api/
        (that link appears to 404 now)
        https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html#//apple_ref/doc/uid/TP40017632-CH5-SW1
        Fetch the category list first via https://itunes.apple.com/WebObjects/MZStoreServices.woa/ws/genres?id=26

         */
        String url = "https://itunes.apple.com/%s/rss/toppodcasts" + (categoryCode > 0 ? "/genre=" + categoryCode : "") + "/limit=" + limit + "/explicit=true/json";
        Log.d(TAG, "final feed url  ->  " + String.format(url, country));
        Request.Builder httpReq = new Request.Builder()
                .cacheControl(new CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build())
                .url(String.format(url, country));

        try (Response response = client.newCall(httpReq.build()).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            }
            if (response.code() == 400) {
                throw new IOException(context.getString(R.string.not_found_on_itunes));
            }
            String prefix = context.getString(R.string.error_msg_prefix);
            throw new IOException(prefix + response);
        }
    }

    private List<PodcastSearchResult> parseFeed(String jsonString) throws JSONException {
        JSONObject result = new JSONObject(jsonString);
        JSONObject feed;
        JSONArray entries;
        try {
            feed = result.getJSONObject("feed");
            entries = feed.getJSONArray("entry");
        } catch (JSONException e) {
            return new ArrayList<>();
        }

        List<PodcastSearchResult> results = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject json = entries.getJSONObject(i);
            results.add(PodcastSearchResult.fromItunesToplist(json));
        }

        return results;
    }

    public static List<EnumItuneCategory> enumItuneCategoryList = new ArrayList<EnumItuneCategory>() {
        {
            add(EnumItuneCategory.Podcast_All);
            add(EnumItuneCategory.Podcast_Art);
            add(EnumItuneCategory.Podcast_Business);
            add(EnumItuneCategory.Podcast_Comedy);
            add(EnumItuneCategory.Podcast_Education);
            add(EnumItuneCategory.Podcast_Society);
            add(EnumItuneCategory.Podcast_Fiction);
            add(EnumItuneCategory.Podcast_Government);
            add(EnumItuneCategory.Podcast_Health);
            add(EnumItuneCategory.Podcast_News);
            add(EnumItuneCategory.Podcast_KidsFamily);
            add(EnumItuneCategory.Podcast_History);
            add(EnumItuneCategory.Podcast_Science);
            add(EnumItuneCategory.Podcast_Film);
            add(EnumItuneCategory.Podcast_Leisure);
            add(EnumItuneCategory.Podcast_Music);
            add(EnumItuneCategory.Podcast_Sports);
            add(EnumItuneCategory.Podcast_Technology);
            add(EnumItuneCategory.Podcast_TrueCrime);
            add(EnumItuneCategory.Podcast_Religion);
        }
    };


}
