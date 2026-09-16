package allen.town.podcast.core.sync.queue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

import allen.town.podcast.core.sync.SynchronizationSettings;
import allen.town.podcast.sync.model.EpisodeAction;

public class SynchronizationQueueStorage {

    private static final String TAG = "SyncQueueStorage";
    private static final String NAME = "synchronization";
    private static final String QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions";
    private static final String QUEUED_FEEDS_REMOVED = "sync_removed";
    private static final String QUEUED_FEEDS_ADDED = "sync_added";
    private final SharedPreferences sharedPreferences;

    public SynchronizationQueueStorage(Context context) {
        this.sharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public ArrayList<EpisodeAction> getQueuedEpisodeActions() {
        ArrayList<EpisodeAction> actions = new ArrayList<>();
        try {
            String json = getSharedPreferences()
                    .getString(QUEUED_EPISODE_ACTIONS, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                actions.add(EpisodeAction.readFromJsonObject(queue.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Safe to continue with whatever parsed: this queue is written by this class only, so
            // corruption means the stored blob is unusable. Syncing the readable prefix is better
            // than failing the whole sync, and the queue is cleared after a successful sync anyway.
            Log.e(TAG, "Stored episode action queue is corrupt, syncing what could be read", e);
        }
        return actions;
    }

    public ArrayList<String> getQueuedRemovedFeeds() {
        ArrayList<String> removedFeedUrls = new ArrayList<>();
        try {
            String json = getSharedPreferences()
                    .getString(QUEUED_FEEDS_REMOVED, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                removedFeedUrls.add(queue.getString(i));
            }
        } catch (JSONException e) {
            // Safe to continue with whatever parsed; see getQueuedEpisodeActions().
            Log.e(TAG, "Stored removed-feed queue is corrupt, syncing what could be read", e);
        }
        return removedFeedUrls;

    }

    public ArrayList<String> getQueuedAddedFeeds() {
        ArrayList<String> addedFeedUrls = new ArrayList<>();
        try {
            String json = getSharedPreferences()
                    .getString(QUEUED_FEEDS_ADDED, "[]");
            JSONArray queue = new JSONArray(json);
            for (int i = 0; i < queue.length(); i++) {
                addedFeedUrls.add(queue.getString(i));
            }
        } catch (JSONException e) {
            // Safe to continue with whatever parsed; see getQueuedEpisodeActions().
            Log.e(TAG, "Stored added-feed queue is corrupt, syncing what could be read", e);
        }
        return addedFeedUrls;
    }

    public void clearEpisodeActionQueue() {
        getSharedPreferences().edit()
                .putString(QUEUED_EPISODE_ACTIONS, "[]").apply();

    }

    public void clearFeedQueues() {
        getSharedPreferences().edit()
                .putString(QUEUED_FEEDS_ADDED, "[]")
                .putString(QUEUED_FEEDS_REMOVED, "[]")
                .apply();
    }

    protected void clearQueue() {
        SynchronizationSettings.resetTimestamps();
        getSharedPreferences().edit()
                .putString(QUEUED_EPISODE_ACTIONS, "[]")
                .putString(QUEUED_FEEDS_ADDED, "[]")
                .putString(QUEUED_FEEDS_REMOVED, "[]")
                .apply();

    }

    protected void enqueueFeedAdded(String downloadUrl) {
        SharedPreferences sharedPreferences = getSharedPreferences();
        String json = sharedPreferences
                .getString(QUEUED_FEEDS_ADDED, "[]");
        try {
            JSONArray queue = new JSONArray(json);
            queue.put(downloadUrl);
            sharedPreferences
                    .edit().putString(QUEUED_FEEDS_ADDED, queue.toString()).apply();

        } catch (JSONException jsonException) {
            // The subscription change is dropped from the sync queue. Not worth failing the caller
            // (a DB write that already succeeded); the next full subscription sync reconciles it.
            Log.e(TAG, "Could not enqueue added feed for sync: " + downloadUrl, jsonException);
        }
    }

    protected void enqueueFeedRemoved(String downloadUrl) {
        SharedPreferences sharedPreferences = getSharedPreferences();
        String json = sharedPreferences.getString(QUEUED_FEEDS_REMOVED, "[]");
        try {
            JSONArray queue = new JSONArray(json);
            queue.put(downloadUrl);
            sharedPreferences.edit().putString(QUEUED_FEEDS_REMOVED, queue.toString())
                    .apply();
        } catch (JSONException jsonException) {
            // See enqueueFeedAdded(): dropped from the queue, reconciled by the next full sync.
            Log.e(TAG, "Could not enqueue removed feed for sync: " + downloadUrl, jsonException);
        }
    }

    protected void enqueueEpisodeAction(EpisodeAction action) {
        SharedPreferences sharedPreferences = getSharedPreferences();
        String json = sharedPreferences.getString(QUEUED_EPISODE_ACTIONS, "[]");
        try {
            JSONArray queue = new JSONArray(json);
            queue.put(action.writeToJsonObject());
            sharedPreferences.edit().putString(
                    QUEUED_EPISODE_ACTIONS, queue.toString()
            ).apply();
        } catch (JSONException jsonException) {
            // The single episode action is dropped. Play position is also persisted in the local
            // database, so only the remote copy misses this one update.
            Log.e(TAG, "Could not enqueue episode action for sync: " + action, jsonException);
        }
    }

    private SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }
}
