package allen.town.podcast.parser.feed.namespace;

import android.text.TextUtils;
import allen.town.podcast.parser.feed.HandlerState;
import allen.town.podcast.parser.feed.element.SyndElement;
import org.xml.sax.Attributes;
import allen.town.podcast.model.feed.FeedFunding;
import allen.town.podcast.model.feed.FeedItem;
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

    @Override
    public void handleElementEnd(String localName, HandlerState state) {
        if (state.getContentBuf() == null) {
            return;
        }
        String content = state.getContentBuf().toString();
        if (FUNDING.equals(localName) && state.getCurrentFunding() != null && !TextUtils.isEmpty(content)) {
            state.getCurrentFunding().setContent(content);
        }
    }
}
