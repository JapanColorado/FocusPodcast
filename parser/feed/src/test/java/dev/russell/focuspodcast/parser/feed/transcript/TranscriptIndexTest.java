package dev.russell.focuspodcast.parser.feed.transcript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.russell.focuspodcast.model.transcript.Transcript;
import dev.russell.focuspodcast.model.transcript.TranscriptSegment;

/** {@link Transcript#indexAt}: the line being spoken at a playback position. */
public class TranscriptIndexTest {

    private static final Transcript THREE = new Transcript(Arrays.asList(
            line(1000, 2000), line(2000, 5000), line(8000, 9000)));

    @Test
    public void beforeTheFirstLineIsNone() {
        assertEquals(-1, THREE.indexAt(0));
        assertEquals(-1, THREE.indexAt(999));
    }

    @Test
    public void aLineStaysCurrentUntilTheNextStarts() {
        assertEquals(0, THREE.indexAt(1000));
        assertEquals(0, THREE.indexAt(1999));
        assertEquals(1, THREE.indexAt(2000));
        assertEquals(1, THREE.indexAt(7999)); // in the gap after its end
        assertEquals(2, THREE.indexAt(8000));
        assertEquals(2, THREE.indexAt(Long.MAX_VALUE));
    }

    @Test
    public void matchesALinearScanOnALongTranscript() {
        List<TranscriptSegment> lines = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            long start = i * 1500L + (i % 3) * 100L;
            lines.add(line(start, start + 1000));
        }
        Transcript transcript = new Transcript(lines);
        for (long position = 0; position < 1_600_000; position += 37) {
            int expected = -1;
            for (int i = 0; i < lines.size() && lines.get(i).getStartMs() <= position; i++) {
                expected = i;
            }
            assertEquals("at " + position, expected, transcript.indexAt(position));
        }
    }

    @Test
    public void linesStartingTogetherResolveToTheLast() {
        Transcript transcript = new Transcript(Arrays.asList(line(0, 1000), line(1000, 1500), line(1000, 2000)));
        assertEquals(2, transcript.indexAt(1200));
    }

    @Test
    public void untimedOrOutOfOrderTranscriptsHaveNoCurrentLine() {
        Transcript untimed = new Transcript(Arrays.asList(
                new TranscriptSegment(TranscriptSegment.UNTIMED, TranscriptSegment.UNTIMED, null, "a"),
                new TranscriptSegment(TranscriptSegment.UNTIMED, TranscriptSegment.UNTIMED, null, "b")));
        assertFalse(untimed.isTimed());
        assertEquals(-1, untimed.indexAt(5000));

        Transcript outOfOrder = new Transcript(Arrays.asList(line(5000, 6000), line(1000, 2000)));
        assertFalse(outOfOrder.isTimed());
        assertEquals(-1, outOfOrder.indexAt(5500));

        assertEquals(-1, Transcript.empty().indexAt(0));
    }

    private static TranscriptSegment line(long start, long end) {
        return new TranscriptSegment(start, end, null, "line at " + start);
    }
}
