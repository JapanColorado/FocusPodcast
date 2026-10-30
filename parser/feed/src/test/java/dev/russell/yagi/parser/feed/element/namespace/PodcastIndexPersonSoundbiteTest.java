package dev.russell.yagi.parser.feed.element.namespace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import dev.russell.yagi.model.feed.Feed;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.Person;
import dev.russell.yagi.model.feed.Soundbite;

/** {@code <podcast:person>} on the channel and on items, and {@code <podcast:soundbite>} on items. */
@RunWith(RobolectricTestRunner.class)
public class PodcastIndexPersonSoundbiteTest {

    private Feed feed;

    @Before
    public void setUp() throws Exception {
        File feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testPersonsSoundbites.xml");
        feed = FeedParserTestHelper.runFeedParser(feedFile);
    }

    @Test
    public void channelPersonsKeepAttributesAndDefaults() {
        List<Person> persons = feed.getPersons();
        // the nameless producer and the liveItem's guest are dropped
        assertEquals(2, persons.size());
        Person alice = persons.get(0);
        assertEquals("Alice Host", alice.getName());
        assertEquals(Person.DEFAULT_ROLE, alice.getRole());
        assertEquals(Person.DEFAULT_GROUP, alice.getGroup());
        assertEquals("https://example.com/alice.jpg", alice.getImageUrl());
        assertEquals("https://example.com/alice", alice.getHref());
        Person bob = persons.get(1);
        assertEquals("Bob Cohost", bob.getName());
        assertEquals("co-host", bob.getRole());
        assertEquals("cast", bob.getGroup());
        assertNull(bob.getImageUrl());
        assertNull(bob.getHref());
    }

    @Test
    public void itemPersonsReplaceTheChannelOnes() {
        FeedItem item = item("With guest");
        item.setFeed(feed);
        List<Person> persons = item.getEffectivePersons();
        assertEquals(2, persons.size());
        assertEquals("Alice Host", persons.get(0).getName());
        assertEquals("Carol Guest", persons.get(1).getName());
        assertEquals("guest", persons.get(1).getRole());
        assertNull(persons.get(1).getImageUrl());
    }

    @Test
    public void itemWithoutPersonsInheritsTheChannel() {
        FeedItem item = item("Inherits");
        assertNull(item.getPersons());
        item.setFeed(feed);
        assertSame(feed.getPersons(), item.getEffectivePersons());
    }

    @Test
    public void soundbitesAreParsedAndMalformedOnesSkipped() {
        List<Soundbite> soundbites = item("With guest").getSoundbites();
        assertEquals(2, soundbites.size());
        assertEquals(new Soundbite(73_000, 60_500, "Why the ocean is salty"), soundbites.get(0));
        assertEquals(new Soundbite(1_234_500, 42_000, null), soundbites.get(1));
        assertNull(item("Inherits").getSoundbites());
    }

    private FeedItem item(String title) {
        for (FeedItem item : feed.getItems()) {
            if (title.equals(item.getTitle())) {
                return item;
            }
        }
        throw new AssertionError("no item " + title);
    }
}
