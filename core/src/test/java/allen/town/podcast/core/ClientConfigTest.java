package allen.town.podcast.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import allen.town.podcast.core.service.download.DownloadRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The core module refuses to be used before the app module has wired it up. This test walks the
 * whole contract in one method on purpose: {@code ClientConfig.initialized} is process-wide state
 * that cannot be undone, and Robolectric shares one class loader between test methods, so the
 * "not initialised yet" branch is only reachable once per class.
 *
 * <p>No other test in this module may call {@link ClientConfig#initialize}, or this one would see
 * an already-initialised core.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class ClientConfigTest {

    private static final DownloadServiceCallbacks CALLBACKS = new StubDownloadServiceCallbacks();

    @Test
    public void ensureInitializedThrowsBeforeInitializeAndIsANoOpAfterwards() {
        Application context = ApplicationProvider.getApplicationContext();

        // 1. Before initialize(): both the guard and the accessor fail loudly.
        try {
            ClientConfig.ensureInitialized(context);
            fail("expected an IllegalStateException before ClientConfig.initialize()");
        } catch (IllegalStateException expected) {
            assertMentionsInitialize(expected);
        }
        try {
            ClientConfig.getDownloadServiceCallbacks();
            fail("expected an IllegalStateException before ClientConfig.initialize()");
        } catch (IllegalStateException expected) {
            assertMentionsInitialize(expected);
        }

        // 2. initialize() validates its arguments.
        try {
            ClientConfig.initialize(context, null);
            fail("expected an IllegalArgumentException for null callbacks");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }

        // 3. After initialize(): the guard is a no-op and the callbacks are the ones supplied.
        ClientConfig.initialize(context, CALLBACKS);
        ClientConfig.ensureInitialized(context);
        ClientConfig.ensureInitialized(context);
        assertSame(CALLBACKS, ClientConfig.getDownloadServiceCallbacks());

        // 4. A repeat initialize() is ignored rather than re-wiring core.
        ClientConfig.initialize(context, new StubDownloadServiceCallbacks());
        assertSame(CALLBACKS, ClientConfig.getDownloadServiceCallbacks());
    }

    @Test
    public void theUserAgentNamesTheApp() {
        assertNotNull(ClientConfig.USER_AGENT);
        assertTrue(ClientConfig.USER_AGENT, ClientConfig.USER_AGENT.startsWith("FocusPodcast_"));
    }

    private static void assertMentionsInitialize(IllegalStateException e) {
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage(), e.getMessage().contains("ClientConfig.initialize"));
        assertEquals(IllegalStateException.class, e.getClass());
    }

    private static class StubDownloadServiceCallbacks implements DownloadServiceCallbacks {
        @Override
        public PendingIntent getNotificationContentIntent(Context context) {
            return null;
        }

        @Override
        public PendingIntent getAuthentificationNotificationContentIntent(Context context,
                                                                          DownloadRequest request) {
            return null;
        }

        @Override
        public PendingIntent getReportNotificationContentIntent(Context context) {
            return null;
        }

        @Override
        public PendingIntent getAutoDownloadReportNotificationContentIntent(Context context) {
            return null;
        }
    }
}
