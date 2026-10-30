package dev.russell.yagi.model.feed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSON form in which {@link Person} and {@link Soundbite} lists are stored in the database
 * (one TEXT column per list). A null or empty list is stored as NULL; a value that cannot be read
 * back (hand-edited, truncated) reads as null rather than failing the whole row, and single entries
 * without the required fields are skipped.
 */
public final class PodcastingJson {

    private static final String NAME = "name";
    private static final String ROLE = "role";
    private static final String GROUP = "group";
    private static final String IMG = "img";
    private static final String HREF = "href";
    private static final String START = "start";
    private static final String DURATION = "duration";
    private static final String TITLE = "title";

    private PodcastingJson() {
    }

    @Nullable
    public static String personsToJson(@Nullable List<Person> persons) {
        if (persons == null || persons.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray();
            for (Person person : persons) {
                JSONObject object = new JSONObject();
                object.put(NAME, person.getName());
                object.put(ROLE, person.getRole());
                object.put(GROUP, person.getGroup());
                putIfNotNull(object, IMG, person.getImageUrl());
                putIfNotNull(object, HREF, person.getHref());
                array.put(object);
            }
            return array.toString();
        } catch (JSONException e) {
            // only thrown for non-finite numbers, which a person has none of
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    public static List<Person> personsFromJson(@Nullable String json) {
        JSONArray array = parseArray(json);
        if (array == null) {
            return null;
        }
        List<Person> persons = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            String name = object == null ? "" : object.optString(NAME, "").trim();
            if (object == null || name.isEmpty()) {
                continue;
            }
            persons.add(new Person(name, optString(object, ROLE), optString(object, GROUP),
                    optString(object, IMG), optString(object, HREF)));
        }
        return persons.isEmpty() ? null : persons;
    }

    @Nullable
    public static String soundbitesToJson(@Nullable List<Soundbite> soundbites) {
        if (soundbites == null || soundbites.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray();
            for (Soundbite soundbite : soundbites) {
                JSONObject object = new JSONObject();
                object.put(START, soundbite.getStartMs());
                object.put(DURATION, soundbite.getDurationMs());
                putIfNotNull(object, TITLE, soundbite.getTitle());
                array.put(object);
            }
            return array.toString();
        } catch (JSONException e) {
            // only thrown for non-finite numbers; these are longs
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    public static List<Soundbite> soundbitesFromJson(@Nullable String json) {
        JSONArray array = parseArray(json);
        if (array == null) {
            return null;
        }
        List<Soundbite> soundbites = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null || !object.has(START) || !object.has(DURATION)) {
                continue;
            }
            long start = object.optLong(START, -1);
            long duration = object.optLong(DURATION, -1);
            if (start < 0 || duration <= 0) {
                continue;
            }
            soundbites.add(new Soundbite(start, duration, optString(object, TITLE)));
        }
        return soundbites.isEmpty() ? null : soundbites;
    }

    @Nullable
    private static JSONArray parseArray(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            // a damaged value reads as "none" instead of failing the row it belongs to
            return null;
        }
    }

    @Nullable
    private static String optString(@NonNull JSONObject object, @NonNull String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private static void putIfNotNull(@NonNull JSONObject object, @NonNull String key,
                                     @Nullable String value) throws JSONException {
        if (value != null) {
            object.put(key, value);
        }
    }
}
