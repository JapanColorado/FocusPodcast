package dev.russell.focuspodcast.parser.feed.transcript;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.model.transcript.TranscriptSegment;

/**
 * The Podcast Index JSON transcript: {@code {"version": "1.0.0", "segments": [{"speaker",
 * "startTime", "endTime", "body"}]}} with times in seconds. A bare array of segments is accepted
 * too. Segments without a body are skipped; a segment without a readable start time leaves the
 * whole transcript untimed rather than out of order.
 */
final class JsonTranscriptParser {

    private JsonTranscriptParser() {
    }

    /** @throws JSONException when the text is not JSON of either shape */
    @NonNull
    static List<TranscriptSegment> parse(@NonNull String text) throws JSONException {
        Object root = new JSONTokener(TranscriptFormat.stripBom(text).trim()).nextValue();
        JSONArray array;
        if (root instanceof JSONObject) {
            array = ((JSONObject) root).optJSONArray("segments");
        } else if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else {
            throw new JSONException("not a transcript");
        }
        List<TranscriptSegment> segments = new ArrayList<>();
        if (array == null) {
            return segments;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String body = TranscriptText.collapse(object.optString("body", ""));
            if (body.isEmpty()) {
                continue;
            }
            String speaker = object.isNull("speaker") ? null : object.optString("speaker", null);
            long start = seconds(object, "startTime");
            long end = seconds(object, "endTime");
            segments.add(new TranscriptSegment(start, end >= start ? end : TranscriptSegment.UNTIMED,
                    speaker, body));
        }
        return segments;
    }

    /** A time given as seconds (number or numeric string) or as a clock string; -1 when absent. */
    private static long seconds(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value instanceof Number) {
            double seconds = ((Number) value).doubleValue();
            return seconds >= 0 && !Double.isNaN(seconds) ? Math.round(seconds * 1000) : -1;
        } else if (value instanceof String) {
            return TranscriptTime.parse((String) value);
        }
        return -1;
    }
}
