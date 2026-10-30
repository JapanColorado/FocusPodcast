package dev.russell.yagi.parser.feed.transcript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import dev.russell.yagi.model.transcript.Transcript;
import dev.russell.yagi.model.transcript.TranscriptSegment;

/** Every transcript format, its edge cases, and garbage in. */
@RunWith(RobolectricTestRunner.class)
public class TranscriptParserTest {

    // ------------------------------------------------------------------ JSON

    @Test
    public void podcastIndexJson() {
        String json = "{\"version\":\"1.0.0\",\"segments\":["
                + "{\"speaker\":\"Alice\",\"startTime\":0.5,\"endTime\":2.25,\"body\":\"Hello there.\"},"
                + "{\"speaker\":\"Bob\",\"startTime\":2.5,\"endTime\":4,\"body\":\"Hi Alice.\"}]}";
        Transcript transcript = TranscriptParser.parse(json, "application/json");
        assertTrue(transcript.isTimed());
        assertEquals(2, transcript.size());
        assertSegment(transcript.getSegments().get(0), 500, 2250, "Alice", "Hello there.");
        assertSegment(transcript.getSegments().get(1), 2500, 4000, "Bob", "Hi Alice.");
    }

    @Test
    public void jsonWithoutSpeakersAndWithWordChunksIsMergedIntoSentences() {
        String json = "{\"segments\":["
                + "{\"startTime\":0,\"endTime\":0.4,\"body\":\"so today\"},"
                + "{\"startTime\":0.4,\"endTime\":0.9,\"body\":\"we talk\"},"
                + "{\"startTime\":0.9,\"endTime\":1.3,\"body\":\"about bees.\"},"
                + "{\"startTime\":1.5,\"endTime\":2.0,\"body\":\"Next\"},"
                + "{\"startTime\":2.0,\"endTime\":2.2,\"body\":\"\"}]}";
        Transcript transcript = TranscriptParser.parse(json, "application/json");
        assertEquals(2, transcript.size());
        assertSegment(transcript.getSegments().get(0), 0, 1300, null, "so today we talk about bees.");
        assertSegment(transcript.getSegments().get(1), 1500, 2000, null, "Next");
    }

    @Test
    public void jsonBareArrayAndStringTimes() {
        String json = "[{\"startTime\":\"00:01:02.5\",\"endTime\":\"63\",\"body\":\"One.\"}]";
        Transcript transcript = TranscriptParser.parse(json, null);
        assertSegment(transcript.getSegments().get(0), 62_500, 63_000, null, "One.");
    }

    @Test
    public void jsonWithoutSegmentsIsEmptyNotShownAsText() {
        assertTrue(TranscriptParser.parse("{\"version\":\"1.0.0\"}", "application/json").isEmpty());
    }

    @Test
    public void jsonWithoutStartTimesIsUntimed() {
        Transcript transcript = TranscriptParser.parse(
                "{\"segments\":[{\"body\":\"First.\"},{\"body\":\"Second.\"}]}", "application/json");
        assertEquals(2, transcript.size());
        assertFalse(transcript.isTimed());
    }

    @Test
    public void brokenJsonFallsBackToText() {
        Transcript transcript = TranscriptParser.parse("{not json at all", "application/json");
        assertEquals(1, transcript.size());
        assertEquals("{not json at all", transcript.getSegments().get(0).getText());
    }

    // ---------------------------------------------------------------- WebVTT

    @Test
    public void webVttWithHeaderNotesStylesVoicesAndTags() {
        String vtt = (char) 0xFEFF + "WEBVTT - some title\r\nKind: captions\r\n\r\n"
                + "STYLE\n::cue { color: red }\n\n"
                + "NOTE this is a comment\nspanning lines\n\n"
                + "REGION\nid:fred\n\n"
                + "1\n00:00:01.000 --> 00:00:03.500 align:start position:10%\n<v Alice>Hello <b>world</b>.</v>\n\n"
                + "intro-2\n00:03.600 --> 00:05.000\n<v.loud Bob Smith>First line\nsecond line.\n\n"
                + "00:05.000 --> 00:06.000\n<c.yellow>Tom &amp; Jerry&nbsp;rock.</c>\n\n"
                + "00:06.000 --> 00:07.000\n<00:06.100><c>karaoke</c><00:06.500><c> words.</c>\n";
        Transcript transcript = TranscriptParser.parse(vtt, "text/vtt");
        assertTrue(transcript.isTimed());
        List<TranscriptSegment> segments = transcript.getSegments();
        assertEquals(4, segments.size());
        assertSegment(segments.get(0), 1000, 3500, "Alice", "Hello world.");
        assertSegment(segments.get(1), 3600, 5000, "Bob Smith", "First line second line.");
        assertSegment(segments.get(2), 5000, 6000, null, "Tom & Jerry rock.");
        assertSegment(segments.get(3), 6000, 7000, null, "karaoke words.");
    }

    @Test
    public void webVttHoursLessTimestampsAndLongHours() {
        String vtt = "WEBVTT\n\n01:02.003 --> 01:04.5\nShort.\n\n100:00:00.000 --> 100:00:01.000\nLong.\n";
        List<TranscriptSegment> segments = TranscriptParser.parse(vtt, "text/vtt").getSegments();
        assertSegment(segments.get(0), 62_003, 64_500, null, "Short.");
        assertSegment(segments.get(1), 360_000_000L, 360_001_000L, null, "Long.");
    }

    @Test
    public void webVttCuesWithUnreadableTimesOrNoTextAreDropped() {
        String vtt = "WEBVTT\n\nxx:yy --> 00:01.000\nBad.\n\n00:02.000 --> 00:03.000\n\n"
                + "00:04.000 --> 00:05.000\n<i></i>\n\n00:06.000 --> 00:07.000\nGood.\n";
        List<TranscriptSegment> segments = TranscriptParser.parse(vtt, "text/vtt").getSegments();
        assertEquals(1, segments.size());
        assertEquals("Good.", segments.get(0).getText());
    }

    @Test
    public void webVttIsRecognisedWhateverTheMimeType() {
        Transcript transcript = TranscriptParser.parse("WEBVTT\n\n00:01.000 --> 00:02.000\nHi.\n", "text/plain");
        assertTrue(transcript.isTimed());
        assertEquals("Hi.", transcript.getSegments().get(0).getText());
    }

    @Test
    public void autoCaptionChunksOfTheSameSpeakerMergeUpToASentenceEnd() {
        String vtt = "WEBVTT\n\n"
                + "00:00.000 --> 00:00.800\n<v A>this is\n\n"
                + "00:00.800 --> 00:01.600\n<v A>a sentence.\n\n"
                + "00:01.600 --> 00:02.400\n<v A>and another\n\n"
                + "00:02.400 --> 00:03.000\n<v B>by someone else\n\n"
                + "00:09.000 --> 00:10.000\n<v B>after a pause\n";
        List<TranscriptSegment> segments = TranscriptParser.parse(vtt, "text/vtt").getSegments();
        assertEquals(4, segments.size());
        assertSegment(segments.get(0), 0, 1600, "A", "this is a sentence.");
        assertSegment(segments.get(1), 1600, 2400, "A", "and another");
        assertSegment(segments.get(2), 2400, 3000, "B", "by someone else");
        assertSegment(segments.get(3), 9000, 10_000, "B", "after a pause");
    }

    @Test
    public void mergingStopsAtTheLengthLimit() {
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
        for (int i = 0; i < 60; i++) {
            vtt.append(String.format(java.util.Locale.US, "00:%02d.000 --> 00:%02d.900\nword word\n\n", i / 4, i / 4));
        }
        List<TranscriptSegment> segments = TranscriptParser.parse(vtt.toString(), "text/vtt").getSegments();
        assertTrue(segments.size() > 1);
        for (TranscriptSegment segment : segments) {
            assertTrue(segment.getText().length() <= TranscriptSegmentMerger.MAX_LINE_CHARS);
        }
    }

    // ------------------------------------------------------------------- SRT

    @Test
    public void srtWithCommasTagsAndMultipleLines() {
        String srt = "1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>Hello</i>\r\nworld.\r\n\r\n"
                + "2\r\n00:00:03,000 --> 00:00:04,000\r\n{\\an8}<font color=\"red\">Bye.</font>\r\n\r\n\r\n"
                + "3\n00:01:00,5 --> 00:01:01,000\nLast.";
        Transcript transcript = TranscriptParser.parse(srt, "application/x-subrip");
        assertTrue(transcript.isTimed());
        List<TranscriptSegment> segments = transcript.getSegments();
        assertEquals(3, segments.size());
        assertSegment(segments.get(0), 1000, 2500, null, "Hello world.");
        assertSegment(segments.get(1), 3000, 4000, null, "Bye.");
        assertSegment(segments.get(2), 60_500, 61_000, null, "Last.");
    }

    @Test
    public void srtIsRecognisedWithAGenericMimeType() {
        Transcript transcript = TranscriptParser.parse(
                "1\n00:00:01,000 --> 00:00:02,000\nHi.\n", "application/octet-stream");
        assertTrue(transcript.isTimed());
        assertEquals(1, transcript.size());
    }

    // ------------------------------------------------------------------ HTML

    @Test
    public void podcastIndexHtmlWithSpeakersAndTimes() {
        String html = "<cite>Kevin:</cite>\n<time>0:00</time>\n<p>Welcome to the show.</p>\n"
                + "<time>0:05</time>\n<p>Today we talk about &quot;HTML&quot;.</p>\n"
                + "<cite>Adam:</cite>\n<time>00:01:10</time>\n<p>Thanks, Kevin.</p>";
        Transcript transcript = TranscriptParser.parse(html, "text/html");
        assertTrue(transcript.isTimed());
        List<TranscriptSegment> segments = transcript.getSegments();
        assertEquals(3, segments.size());
        assertSegment(segments.get(0), 0, TranscriptSegment.UNTIMED, "Kevin", "Welcome to the show.");
        assertSegment(segments.get(1), 5000, TranscriptSegment.UNTIMED, "Kevin", "Today we talk about \"HTML\".");
        assertSegment(segments.get(2), 70_000, TranscriptSegment.UNTIMED, "Adam", "Thanks, Kevin.");
    }

    @Test
    public void htmlWithSpeakerAndTimeInsideTheParagraph() {
        String html = "<html><body><p><cite>Ann:</cite> <time>00:00:03</time> Inside.</p>"
                + "<p>No time of its own.</p></body></html>";
        List<TranscriptSegment> segments = TranscriptParser.parse(html, "text/html").getSegments();
        assertEquals(2, segments.size());
        assertSegment(segments.get(0), 3000, TranscriptSegment.UNTIMED, "Ann", "Inside.");
        assertSegment(segments.get(1), 3000, TranscriptSegment.UNTIMED, "Ann", "No time of its own.");
    }

    @Test
    public void htmlWithoutTimesIsUntimedParagraphs() {
        String html = "<p>First paragraph.</p><p>  </p><p>Second\n paragraph.</p>";
        Transcript transcript = TranscriptParser.parse(html, "text/html");
        assertFalse(transcript.isTimed());
        assertEquals(2, transcript.size());
        assertEquals("Second paragraph.", transcript.getSegments().get(1).getText());
        assertEquals(-1, transcript.indexAt(10_000));
    }

    @Test
    public void htmlWithoutParagraphsKeepsLineBreaks() {
        Transcript transcript = TranscriptParser.parse("<div>One line<br>another line</div>", "text/html");
        assertEquals(2, transcript.size());
        assertEquals("another line", transcript.getSegments().get(1).getText());
    }

    // ------------------------------------------------------------ plain text

    @Test
    public void plainTextParagraphs() {
        Transcript transcript = TranscriptParser.parse("First para\ncontinues.\n\n\nSecond para.\n", "text/plain");
        assertFalse(transcript.isTimed());
        assertEquals(2, transcript.size());
        assertEquals("First para continues.", transcript.getSegments().get(0).getText());
    }

    @Test
    public void plainTextWithoutBlankLinesSplitsAtLineBreaks() {
        Transcript transcript = TranscriptParser.parse("One.\nTwo.\nThree.", "text/plain");
        assertEquals(3, transcript.size());
    }

    @Test
    public void unknownTypeIsSniffed() {
        assertEquals(2, TranscriptParser.parse("<p>a</p><p>b</p>", "application/x-unknown").size());
        assertEquals(1, TranscriptParser.parse("just words", null).size());
    }

    // ---------------------------------------------------------------- garbage

    @Test
    public void blankOrNullInputIsEmpty() {
        assertTrue(TranscriptParser.parse(null, "text/vtt").isEmpty());
        assertTrue(TranscriptParser.parse("   \n ", "text/vtt").isEmpty());
        assertTrue(TranscriptParser.parse("WEBVTT\n\n", "text/vtt").isEmpty());
    }

    @Test
    public void binaryGarbageDoesNotThrow() {
        Transcript transcript = TranscriptParser.parse("\u0000\u0001-->\u0002\n\n<<<>>>", "text/vtt");
        assertFalse(transcript.isTimed());
    }

    @Test
    public void plainTextOfATranscriptHasSpeakersAndNoMarkup() {
        String vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\n<v Al>Hi.\n\n00:02.000 --> 00:03.000\nThere.\n";
        assertEquals("Al: Hi.\nThere.", TranscriptParser.parse(vtt, "text/vtt").toPlainText());
    }

    // ------------------------------------------------------------ MIME types

    @Test
    public void mimeTypeRanking() {
        assertEquals(TranscriptFormat.JSON, TranscriptFormat.fromMimeType("application/json; charset=utf-8"));
        assertEquals(TranscriptFormat.VTT, TranscriptFormat.fromMimeType("TEXT/VTT"));
        assertEquals(TranscriptFormat.SRT, TranscriptFormat.fromMimeType("application/srt"));
        assertNull(TranscriptFormat.fromMimeType("audio/mpeg"));
        assertTrue(TranscriptFormat.rankOf("application/json") < TranscriptFormat.rankOf("text/vtt"));
        assertTrue(TranscriptFormat.rankOf("text/vtt") < TranscriptFormat.rankOf("application/x-subrip"));
        assertTrue(TranscriptFormat.rankOf("application/x-subrip") < TranscriptFormat.rankOf("text/html"));
        assertTrue(TranscriptFormat.rankOf("text/html") < TranscriptFormat.rankOf("text/plain"));
        assertTrue(TranscriptFormat.rankOf("text/plain") < TranscriptFormat.rankOf(null));
    }

    @Test
    public void timestamps() {
        assertEquals(3_723_456L, TranscriptTime.parse("01:02:03.456"));
        assertEquals(123_400L, TranscriptTime.parse("02:03.4"));
        assertEquals(1_000L, TranscriptTime.parse("00:00:01,000"));
        assertEquals(12_500L, TranscriptTime.parse("12.5"));
        assertEquals(0L, TranscriptTime.parse("0:00"));
        assertEquals(-1L, TranscriptTime.parse("00:61"));
        assertEquals(-1L, TranscriptTime.parse("abc"));
        assertEquals(-1L, TranscriptTime.parse(null));
    }

    private static void assertSegment(TranscriptSegment segment, long start, long end, String speaker, String text) {
        assertEquals(segment.toString(), start, segment.getStartMs());
        assertEquals(segment.toString(), end, segment.getEndMs());
        assertEquals(segment.toString(), speaker, segment.getSpeaker());
        assertEquals(segment.toString(), text, segment.getText());
    }
}
