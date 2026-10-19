package dev.russell.focuspodcast.parser.feed.transcript;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import dev.russell.focuspodcast.model.transcript.TranscriptSegment;

/**
 * HTML transcripts as the Podcast Index describes them: {@code <cite>Speaker:</cite>},
 * {@code <time>00:00:00</time>} and {@code <p>words</p>}, in that order, the speaker and time
 * applying to the paragraphs that follow (the speaker until the next {@code <cite>}, the time to
 * the next paragraph only). The cite and time may also sit inside the paragraph. A paragraph
 * without its own time inherits the previous one, so a partly timed transcript stays in order.
 * Without any paragraph the text is read as plain text, line breaks kept.
 */
final class HtmlTranscriptParser {

    private HtmlTranscriptParser() {
    }

    @NonNull
    static List<TranscriptSegment> parse(@NonNull String html) {
        Document document = Jsoup.parse(html);
        List<TranscriptSegment> segments = new ArrayList<>();
        String speaker = null;
        long pendingTime = -1;
        long lastTime = -1;
        boolean anyTime = false;
        for (Element element : document.select("cite, time, p")) {
            String tag = element.normalName();
            if (!"p".equals(tag)) {
                if (element.closest("p") != null) {
                    continue; // read with its paragraph
                }
                if ("cite".equals(tag)) {
                    speaker = speakerOf(element);
                } else {
                    pendingTime = timeOf(element);
                }
                continue;
            }
            Element paragraph = element.clone();
            for (Element cite : paragraph.select("cite")) {
                speaker = speakerOf(cite);
                cite.remove();
            }
            for (Element time : paragraph.select("time")) {
                pendingTime = timeOf(time);
                time.remove();
            }
            String text = TranscriptText.collapse(paragraph.text());
            if (text.isEmpty()) {
                continue;
            }
            long start = pendingTime >= 0 ? pendingTime : lastTime;
            anyTime |= pendingTime >= 0;
            pendingTime = -1;
            lastTime = start;
            segments.add(new TranscriptSegment(start, TranscriptSegment.UNTIMED, speaker, text));
        }
        if (segments.isEmpty()) {
            document.select("br").after("\n");
            for (Element block : document.select("div, li, h1, h2, h3, h4, h5, h6")) {
                block.after("\n\n");
            }
            return PlainTextTranscriptParser.parse(document.body().wholeText());
        }
        return anyTime ? segments : untimed(segments);
    }

    @Nullable
    private static String speakerOf(Element cite) {
        String name = TranscriptText.collapse(cite.text());
        while (name.endsWith(":")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        return name.isEmpty() ? null : name;
    }

    private static long timeOf(Element time) {
        long value = TranscriptTime.parse(time.text());
        return value >= 0 ? value : TranscriptTime.parse(time.attr("datetime"));
    }

    private static List<TranscriptSegment> untimed(List<TranscriptSegment> segments) {
        List<TranscriptSegment> result = new ArrayList<>(segments.size());
        for (TranscriptSegment segment : segments) {
            result.add(new TranscriptSegment(TranscriptSegment.UNTIMED, TranscriptSegment.UNTIMED,
                    segment.getSpeaker(), segment.getText()));
        }
        return result;
    }
}
