package allen.town.podcast.parser.feed.namespace;

import android.text.TextUtils;
import allen.town.podcast.parser.feed.HandlerState;
import allen.town.podcast.parser.feed.element.SyndElement;
import org.xml.sax.Attributes;
import allen.town.podcast.model.feed.FeedFunding;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.Person;
import allen.town.podcast.model.feed.Soundbite;
import allen.town.podcast.parser.feed.transcript.TranscriptFormat;

public class PodcastIndex extends Namespace {

    public static final String NSTAG = "podcast";
    public static final String NSURI = "https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md";
    public static final String NSURI2 = "https://podcastindex.org/namespace/1.0";
    private static final String URL = "url";
    private static final String FUNDING = "funding";
    private static final String CHAPTERS = "chapters";
    private static final String TRANSCRIPT = "transcript";
    private static final String TYPE = "type";
    private static final String PERSON = "person";
    private static final String SOUNDBITE = "soundbite";
    private static final String ROLE = "role";
    private static final String GROUP = "group";
    private static final String IMG = "img";
    private static final String HREF = "href";
    private static final String START_TIME = "startTime";
    private static final String DURATION = "duration";
    /** Keys of {@link HandlerState#getTempObjects()} holding the attributes until the text arrives. */
    private static final String TEMP_PERSON = "podcast:person";
    private static final String TEMP_SOUNDBITE = "podcast:soundbite";

    @Override
    public SyndElement handleElementStart(String localName, HandlerState state,
                                          Attributes attributes) {
        if (FUNDING.equals(localName)) {
            String href = attributes.getValue(URL);
            FeedFunding funding = new FeedFunding(href, "");
            state.setCurrentFunding(funding);
            state.getFeed().addPayment(state.getCurrentFunding());
        } else if (CHAPTERS.equals(localName)) {
            String href = attributes.getValue(URL);
            if (!TextUtils.isEmpty(href)) {
                state.getCurrentItem().setPodcastIndexChapterUrl(href);
            }
        } else if (TRANSCRIPT.equals(localName)) {
            handleTranscript(state, attributes);
        } else if (PERSON.equals(localName)) {
            state.getTempObjects().put(TEMP_PERSON, new String[]{
                attributes.getValue(ROLE), attributes.getValue(GROUP),
                attributes.getValue(IMG), attributes.getValue(HREF)});
        } else if (SOUNDBITE.equals(localName)) {
            long[] range = parseSoundbiteRange(attributes);
            if (state.getCurrentItem() != null && range != null) {
                state.getTempObjects().put(TEMP_SOUNDBITE, range);
            }
        }
        return new SyndElement(localName, this);
    }

    /**
     * An item may list its transcript in several formats; the one with the best
     * {@link TranscriptFormat#rankOf rank} wins, the first listed on a tie.
     */
    private static void handleTranscript(HandlerState state, Attributes attributes) {
        FeedItem item = state.getCurrentItem();
        String href = attributes.getValue(URL);
        if (item == null || TextUtils.isEmpty(href)) {
            return;
        }
        String type = attributes.getValue(TYPE);
        if (item.getTranscriptUrl() == null
                || TranscriptFormat.rankOf(type) < TranscriptFormat.rankOf(item.getTranscriptType())) {
            item.setTranscript(href.trim(), type == null ? null : type.trim());
        }
    }

    /**
     * The start and duration of a soundbite in milliseconds, or null when either attribute is
     * missing or not a number, the start is negative or the duration not positive. The namespace
     * gives both as (possibly fractional) seconds.
     */
    static long[] parseSoundbiteRange(Attributes attributes) {
        double start = parseSeconds(attributes.getValue(START_TIME));
        double duration = parseSeconds(attributes.getValue(DURATION));
        if (Double.isNaN(start) || Double.isNaN(duration) || start < 0 || duration <= 0) {
            return null;
        }
        return new long[]{Math.round(start * 1000), Math.round(duration * 1000)};
    }

    /** Seconds as a double, NaN when the value is missing, not a number or not finite. */
    private static double parseSeconds(String value) {
        if (TextUtils.isEmpty(value)) {
            return Double.NaN;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            return Double.isInfinite(seconds) ? Double.NaN : seconds;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * A {@code <podcast:person>} inside an item belongs to that episode, one directly in the
     * channel to the podcast, and any other is dropped, as is one without a name. A soundbite counts only inside an item.
     */
    private static void finishPersonOrSoundbite(String localName, HandlerState state, String content) {
        if (PERSON.equals(localName)) {
            String[] attrs = (String[]) state.getTempObjects().remove(TEMP_PERSON);
            String name = content == null ? "" : content.trim();
            if (attrs == null || name.isEmpty()) {
                return;
            }
            Person person = new Person(name, attrs[0], attrs[1], attrs[2], attrs[3]);
            if (state.getCurrentItem() != null) {
                state.getCurrentItem().addPerson(person);
            } else if (state.getFeed() != null && isChannelChild(state)) {
                state.getFeed().addPerson(person);
            }
        } else if (SOUNDBITE.equals(localName)) {
            long[] range = (long[]) state.getTempObjects().remove(TEMP_SOUNDBITE);
            if (range != null && state.getCurrentItem() != null) {
                state.getCurrentItem().addSoundbite(new Soundbite(range[0], range[1], content));
            }
        }
    }

    /** Whether the element being closed sits directly in the RSS channel or Atom feed (not, say, a liveItem). */
    private static boolean isChannelChild(HandlerState state) {
        if (state.getTagstack().size() < 2) {
            return false;
        }
        String parent = state.getSecondTag().getName();
        return "channel".equals(parent) || "feed".equals(parent);
    }

    @Override
    public void handleElementEnd(String localName, HandlerState state) {
        String content = state.getContentBuf() == null ? null : state.getContentBuf().toString();
        finishPersonOrSoundbite(localName, state, content);
        if (content == null) {
            return;
        }
        if (FUNDING.equals(localName) && state.getCurrentFunding() != null && !TextUtils.isEmpty(content)) {
            state.getCurrentFunding().setContent(content);
        }
    }
}
