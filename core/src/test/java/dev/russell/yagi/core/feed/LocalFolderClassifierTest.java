package dev.russell.yagi.core.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The pure part of {@link LocalFolderClassifier}: the audiobook-or-podcast guess. */
public class LocalFolderClassifierTest {

    private static LocalFolderClassifier.Tags tags(String album, String genre, int track) {
        return new LocalFolderClassifier.Tags(album, genre, track);
    }

    @Test
    public void emptyFolderIsAPodcast() {
        assertFalse(LocalFolderClassifier.looksLikeAudiobook(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void m4bIsAnAudiobook() {
        assertTrue(LocalFolderClassifier.looksLikeAudiobook(Arrays.asList("a.mp3", "Book.M4B"),
                Arrays.asList(tags(null, "Podcast", 0))));
    }

    @Test
    public void podcastGenreWinsOverASingleFile() {
        assertFalse(LocalFolderClassifier.looksLikeAudiobook(Collections.singletonList("ep.mp3"),
                Collections.singletonList(tags("Show", "Podcast", 1))));
    }

    @Test
    public void audiobookGenre() {
        assertTrue(LocalFolderClassifier.looksLikeAudiobook(Arrays.asList("1.mp3", "2.mp3"),
                Arrays.asList(tags(null, "Audiobook", 0), tags(null, null, 0))));
    }

    @Test
    public void singleUntaggedFileIsAnAudiobook() {
        assertTrue(LocalFolderClassifier.looksLikeAudiobook(Collections.singletonList("book.mp3"),
                Collections.singletonList(tags(null, null, 0))));
    }

    @Test
    public void oneAlbumWithTrackNumbersIsAnAudiobook() {
        assertTrue(LocalFolderClassifier.looksLikeAudiobook(Arrays.asList("01.mp3", "02.mp3"),
                Arrays.asList(tags("Dune", null, 1), tags(" dune ", null, 2))));
    }

    @Test
    public void sharedAlbumWithoutTrackNumbersIsAPodcast() {
        // podcast downloads usually carry the show's name as their album
        assertFalse(LocalFolderClassifier.looksLikeAudiobook(Arrays.asList("ep1.mp3", "ep2.mp3"),
                Arrays.asList(tags("Show", null, 0), tags("Show", null, 0))));
    }

    @Test
    public void differentAlbumsAreAPodcast() {
        assertFalse(LocalFolderClassifier.looksLikeAudiobook(Arrays.asList("a.mp3", "b.mp3"),
                Arrays.asList(tags("A", null, 1), tags("B", null, 2))));
    }

    @Test
    public void sampleSpreadsFromFirstToLast() {
        List<Integer> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(i);
        }
        List<Integer> sample = LocalFolderClassifier.sample(files);
        assertEquals(LocalFolderClassifier.MAX_SAMPLED_FILES, sample.size());
        assertEquals(Integer.valueOf(0), sample.get(0));
        assertEquals(Integer.valueOf(99), sample.get(sample.size() - 1));
        assertEquals(files.subList(0, 3), LocalFolderClassifier.sample(files.subList(0, 3)));
    }
}
