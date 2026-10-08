package allen.town.podcast.core.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import allen.town.podcast.model.feed.Feed;
import allen.town.podcast.model.feed.FeedItem;
import allen.town.podcast.model.feed.FeedMedia;

/** The pure parts of {@link LocalFeedUpdater}: cover choice, tag numbers and tag voting. */
@RunWith(RobolectricTestRunner.class)
public class LocalFeedUpdaterTest {

    private static final String FALLBACK = Feed.PREFIX_GENERATIVE_COVER + "content://tree";
    private final Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://tree", null, "Book");

    private static LocalFolderScanner.LocalFile image(String path, int depth, String mime) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new LocalFolderScanner.LocalFile(Uri.parse("content://doc/" + path), path, name, mime, 1, 0, depth);
    }

    private FeedItem track(String path, int trackNumber, boolean embeddedPicture) {
        FeedItem item = new FeedItem(trackNumber, path, "guid" + path, path, new Date(0), FeedItem.UNPLAYED, feed);
        item.setTrackNumber(trackNumber);
        FeedMedia media = new FeedMedia(trackNumber, item, 0, 0, 0, "audio/mpeg",
                "content://doc/" + path, "content://doc/" + path, false, null, 0, 0);
        media.setHasEmbeddedPicture(embeddedPicture);
        item.setMedia(media);
        return item;
    }

    @Test
    public void preferredCoverNameWinsCaseInsensitively() {
        List<LocalFolderScanner.LocalFile> images = Arrays.asList(
                image("aaa.jpg", 0, "image/jpeg"),
                image("CD1/Folder.JPG", 1, "image/jpeg"),
                image("Cover.png", 0, "image/png"));
        assertEquals("content://doc/Cover.png",
                LocalFeedUpdater.getImageUrl(images, Collections.emptyList(), FALLBACK));
    }

    @Test
    public void preferredCoverInASubfolderBeatsAnyOtherImage() {
        List<LocalFolderScanner.LocalFile> images = Arrays.asList(
                image("scan.jpg", 0, "image/jpeg"),
                image("CD1/folder.jpg", 1, "image/jpeg"));
        assertEquals("content://doc/CD1/folder.jpg",
                LocalFeedUpdater.getImageUrl(images, Collections.emptyList(), FALLBACK));
    }

    @Test
    public void otherwiseTheFirstImageClosestToTheTop() {
        List<LocalFolderScanner.LocalFile> images = Arrays.asList(
                image("CD1/a.jpg", 1, "image/jpeg"),
                image("page 10.png", 0, "image/png"),
                image("page 2.png", 0, "image/png"),
                image("vector.svg", 0, "image/svg+xml"));
        assertEquals("content://doc/page 2.png",
                LocalFeedUpdater.getImageUrl(images, Collections.emptyList(), FALLBACK));
    }

    @Test
    public void otherwiseTheEmbeddedPictureOfTheFirstTrack() {
        List<FeedItem> items = Arrays.asList(track("b.mp3", 2, false), track("a.mp3", 1, true));
        assertEquals(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + "content://doc/a.mp3",
                LocalFeedUpdater.getImageUrl(Collections.emptyList(), items, FALLBACK));
    }

    @Test
    public void otherwiseTheGeneratedCover() {
        List<FeedItem> items = Arrays.asList(track("b.mp3", 2, true), track("a.mp3", 1, false));
        assertEquals(FALLBACK, LocalFeedUpdater.getImageUrl(Collections.emptyList(), items, FALLBACK));
        assertEquals(FALLBACK, LocalFeedUpdater.getImageUrl(Collections.emptyList(), Collections.emptyList(), FALLBACK));
    }

    @Test
    public void parsesTrackAndDiscTags() {
        assertEquals(3, LocalFeedUpdater.parseLeadingNumber("3"));
        assertEquals(3, LocalFeedUpdater.parseLeadingNumber("03/12"));
        assertEquals(12, LocalFeedUpdater.parseLeadingNumber(" 12 of 20"));
        assertEquals(0, LocalFeedUpdater.parseLeadingNumber("A1"));
        assertEquals(0, LocalFeedUpdater.parseLeadingNumber(""));
        assertEquals(0, LocalFeedUpdater.parseLeadingNumber(null));
        assertEquals(123456789, LocalFeedUpdater.parseLeadingNumber("1234567890123"));
    }

    @Test
    public void tagTallyPicksTheMostCommonValue() {
        LocalFeedUpdater.TagTally tally = new LocalFeedUpdater.TagTally();
        assertNull(tally.mostCommonAlbum());
        tally.addAlbum("Bonus");
        tally.addAlbum("The Book");
        tally.addAlbum(" The Book ");
        tally.addAlbum("");
        tally.addAlbum(null);
        assertEquals("The Book", tally.mostCommonAlbum());

        tally.addArtist("First");
        tally.addArtist("Second");
        assertEquals("a tie goes to the first value seen", "First", tally.mostCommonArtist());
    }
}
