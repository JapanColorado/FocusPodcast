package allen.town.podcast.core.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import allen.town.podcast.core.pref.Prefs;

/**
 * Tests the pure predicates of {@link NetworkUtils}: the "is this download blocked" heuristic,
 * which needs nothing at all, and the allowed-on-mobile rules, which are driven by the active
 * network type plus the user's per-feature mobile-data preferences.
 */
@RunWith(RobolectricTestRunner.class)
public class NetworkUtilsTest {

    private Application context;
    private ShadowConnectivityManager shadowConnectivity;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Prefs.resetForTests();
        Prefs.init(context);
        NetworkUtils.init(context);
        shadowConnectivity = shadowOf(
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
    }

    @After
    public void tearDown() {
        Prefs.resetForTests();
    }

    // ------------------------------------------------ wasDownloadBlocked

    @Test
    public void aLoopbackAddressInTheMessageMeansBlocked() {
        assertTrue(NetworkUtils.wasDownloadBlocked(
                new IOException("Failed to connect to /127.0.0.1:8080")));
    }

    @Test
    public void aZeroAddressInTheMessageMeansBlocked() {
        assertTrue(NetworkUtils.wasDownloadBlocked(
                new IOException("Failed to connect to /0.0.0.0:80")));
    }

    @Test
    public void aRealAddressInTheMessageDoesNotMeanBlocked() {
        assertFalse(NetworkUtils.wasDownloadBlocked(
                new IOException("Failed to connect to /93.184.216.34:443")));
    }

    @Test
    public void aMessageWithoutAnAddressDoesNotMeanBlocked() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new SocketTimeoutException("timeout")));
    }

    @Test
    public void aMessagelessThrowableDoesNotMeanBlocked() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new UnknownHostException()));
    }

    @Test
    public void theCauseChainIsSearchedForTheAddress() {
        Throwable cause = new IOException("Failed to connect to /127.0.0.2:8080");
        Throwable wrapper = new RuntimeException("download failed", new IllegalStateException(cause));
        assertTrue(NetworkUtils.wasDownloadBlocked(wrapper));
    }

    @Test
    public void aCauseChainWithoutAnAddressDoesNotMeanBlocked() {
        Throwable wrapper = new RuntimeException("outer", new IllegalStateException("inner"));
        assertFalse(NetworkUtils.wasDownloadBlocked(wrapper));
    }

    // --------------------------------------------------- network availability

    @Test
    public void noActiveNetworkMeansNothingIsAvailableOrAutoDownloadable() {
        shadowConnectivity.setActiveNetworkInfo(null);

        assertFalse(NetworkUtils.networkAvailable());
        assertFalse(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void aConnectedNetworkIsAvailable() {
        setActiveNetwork(ConnectivityManager.TYPE_WIFI, true);
        assertTrue(NetworkUtils.networkAvailable());
    }

    @Test
    public void aDisconnectedNetworkIsNotAvailable() {
        shadowConnectivity.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.DISCONNECTED, ConnectivityManager.TYPE_WIFI, 0,
                true, NetworkInfo.State.DISCONNECTED));
        assertFalse(NetworkUtils.networkAvailable());
    }

    @Test
    public void ethernetIsAlwaysGoodEnoughForAutoDownload() {
        setActiveNetwork(ConnectivityManager.TYPE_ETHERNET, true);
        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    // ------------------------------------------- per-feature mobile settings

    @Test
    public void imagesAreTheOnlyFeatureAllowedOnMobileByDefault() {
        assertTrue(Prefs.isAllowMobileImages());
        assertFalse(Prefs.isAllowMobileEpisodeDownload());
        assertFalse(Prefs.isAllowMobileStreaming());
        assertFalse(Prefs.isAllowMobileAutoDownload());
        assertFalse(Prefs.isAllowMobileFeedRefresh());
    }

    @Test
    public void allowingAFeatureOnMobileMakesItAllowedWhateverTheNetworkIs() {
        setActiveNetwork(ConnectivityManager.TYPE_MOBILE, true);

        Prefs.setAllowMobileEpisodeDownload(true);
        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());

        Prefs.setAllowMobileStreaming(true);
        assertTrue(NetworkUtils.isStreamingAllowed());

        Prefs.setAllowMobileImages(true);
        assertTrue(NetworkUtils.isImageAllowed());
        // Head requests are treated like images because they are similarly tiny.
        assertTrue(NetworkUtils.isEpisodeHeadDownloadAllowed());
    }

    @Test
    public void disallowingImagesOnMobileBlocksHeadRequestsToo() {
        setActiveNetwork(ConnectivityManager.TYPE_MOBILE, true);
        Prefs.setAllowMobileImages(false);

        assertFalse(Prefs.isAllowMobileImages());
        assertFalse(NetworkUtils.isImageAllowed());
        assertFalse(NetworkUtils.isEpisodeHeadDownloadAllowed());
    }

    @Test
    public void mobileSettingsRoundTrip() {
        Prefs.setAllowMobileEpisodeDownload(true);
        assertTrue(Prefs.isAllowMobileEpisodeDownload());
        Prefs.setAllowMobileEpisodeDownload(false);
        assertFalse(Prefs.isAllowMobileEpisodeDownload());

        Prefs.setAllowMobileStreaming(true);
        assertTrue(Prefs.isAllowMobileStreaming());
        // Toggling one feature must not toggle its neighbours.
        assertFalse(Prefs.isAllowMobileEpisodeDownload());
        assertFalse(Prefs.isAllowMobileAutoDownload());
    }

    // --------------------------------------------------------------- helpers

    private void setActiveNetwork(int type, boolean connected) {
        shadowConnectivity.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                connected ? NetworkInfo.DetailedState.CONNECTED : NetworkInfo.DetailedState.DISCONNECTED,
                type, 0, true,
                connected ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED));
    }
}
