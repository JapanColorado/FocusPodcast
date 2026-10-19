package dev.russell.focuspodcast.core.util.comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NaturalOrderComparatorTest {

    private static int compare(String a, String b) {
        return NaturalOrderComparator.INSTANCE.compare(a, b);
    }

    @Test
    public void numbersCompareByValue() {
        assertTrue(compare("Ch 2", "Ch 10") < 0);
        assertTrue(compare("Ch 10", "Ch 2") > 0);
        assertTrue(compare("track9.mp3", "track10.mp3") < 0);
    }

    @Test
    public void lettersCompareIgnoringCase() {
        assertTrue(compare("apple", "Banana") < 0);
        assertTrue(compare("Apple", "banana") < 0);
    }

    @Test
    public void leadingZerosDoNotChangeTheValue() {
        assertTrue(compare("01 Intro", "2 Next") < 0);
        assertTrue(compare("009", "10") < 0);
    }

    @Test
    public void equalUnderTheRulesStillGivesATotalOrder() {
        assertTrue(compare("a", "A") != 0);
        assertTrue(compare("1", "01") < 0);
        assertEquals(0, compare("same", "same"));
        assertEquals(-Integer.signum(compare("a", "A")), Integer.signum(compare("A", "a")));
    }

    @Test
    public void prefixSortsFirst() {
        assertTrue(compare("Chapter", "Chapter 1") < 0);
    }

    @Test
    public void veryLongNumbersDoNotOverflow() {
        assertTrue(compare("x99999999999999999999", "x100000000000000000000") < 0);
    }

    @Test
    public void sortsAnAudiobookFolder() {
        List<String> names = new ArrayList<>(Arrays.asList(
                "CD2/Track 1.mp3", "CD10/Track 1.mp3", "CD1/Track 10.mp3", "CD1/track 2.mp3", "CD1/Track 1.mp3"));
        Collections.sort(names, NaturalOrderComparator.INSTANCE);
        assertEquals(Arrays.asList("CD1/Track 1.mp3", "CD1/track 2.mp3", "CD1/Track 10.mp3",
                "CD2/Track 1.mp3", "CD10/Track 1.mp3"), names);
    }
}
