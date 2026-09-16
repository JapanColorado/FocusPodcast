package allen.town.podcast.core.service.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import allen.town.podcast.model.download.DownloadError;
import allen.town.podcast.model.feed.Feed;

/**
 * Tests the pure decision logic that {@link DownloadService} applies before it ever touches a
 * {@link android.content.Intent}: which requests are worth sending, how big a batch may get and
 * which failures deserve an automatic retry.
 *
 * <p>Robolectric is needed only because {@link DownloadRequest} carries a {@link Bundle}.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class DownloadServiceLogicTest {

    private static final DownloadService.InFlightCheck NOTHING_IN_FLIGHT = url -> false;

    // ------------------------------------------------------- de-duplication

    @Test
    public void nothingInFlightKeepsEveryDistinctRequest() {
        DownloadRequest[] requests = {request("https://a.example/1.mp3"), request("https://a.example/2.mp3")};
        List<DownloadRequest> kept = DownloadService.filterInFlight(requests, NOTHING_IN_FLIGHT);

        assertEquals(2, kept.size());
        assertSame(requests[0], kept.get(0));
        assertSame(requests[1], kept.get(1));
    }

    @Test
    public void requestsAlreadyDownloadingAreDropped() {
        DownloadRequest[] requests = {
                request("https://a.example/1.mp3"),
                request("https://a.example/2.mp3"),
                request("https://a.example/3.mp3")};
        Set<String> inFlight = new HashSet<>(Arrays.asList("https://a.example/2.mp3"));

        List<DownloadRequest> kept = DownloadService.filterInFlight(requests, inFlight::contains);

        assertEquals(2, kept.size());
        assertEquals("https://a.example/1.mp3", kept.get(0).getSource());
        assertEquals("https://a.example/3.mp3", kept.get(1).getSource());
    }

    @Test
    public void duplicateUrlsWithinOneBatchAreCollapsedToTheFirst() {
        DownloadRequest first = request("https://a.example/same.mp3");
        DownloadRequest second = request("https://a.example/same.mp3");
        DownloadRequest other = request("https://a.example/other.mp3");

        List<DownloadRequest> kept = DownloadService.filterInFlight(
                new DownloadRequest[]{first, second, other}, NOTHING_IN_FLIGHT);

        assertEquals(2, kept.size());
        assertSame(first, kept.get(0));
        assertSame(other, kept.get(1));
    }

    @Test
    public void everythingInFlightLeavesNothingToSend() {
        DownloadRequest[] requests = {request("https://a.example/1.mp3"), request("https://a.example/2.mp3")};
        assertTrue(DownloadService.filterInFlight(requests, url -> true).isEmpty());
    }

    @Test
    public void anEmptyBatchStaysEmpty() {
        assertTrue(DownloadService.filterInFlight(new DownloadRequest[0], NOTHING_IN_FLIGHT).isEmpty());
    }

    // --------------------------------------------------------------- the cap

    @Test
    public void theBatchCapIsOneHundred() {
        assertEquals(100, DownloadService.MAX_REQUESTS_PER_INTENT);
    }

    @Test
    public void batchesAtOrBelowTheCapAreUntouched() {
        List<DownloadRequest> exactlyAtTheCap = manyRequests(DownloadService.MAX_REQUESTS_PER_INTENT);
        assertEquals(DownloadService.MAX_REQUESTS_PER_INTENT,
                DownloadService.capToIntentLimit(exactlyAtTheCap).size());
        assertEquals(1, DownloadService.capToIntentLimit(manyRequests(1)).size());
        assertEquals(0, DownloadService.capToIntentLimit(Collections.emptyList()).size());
    }

    @Test
    public void oversizedBatchesAreTrimmedToTheFirstHundred() {
        List<DownloadRequest> tooMany = manyRequests(250);
        List<DownloadRequest> capped = DownloadService.capToIntentLimit(tooMany);

        assertEquals(DownloadService.MAX_REQUESTS_PER_INTENT, capped.size());
        assertSame(tooMany.get(0), capped.get(0));
        assertSame(tooMany.get(99), capped.get(99));
    }

    @Test
    public void cappingReturnsACopyAndNeverAView() {
        List<DownloadRequest> tooMany = new ArrayList<>(manyRequests(120));
        List<DownloadRequest> capped = DownloadService.capToIntentLimit(tooMany);
        tooMany.clear();
        assertEquals(DownloadService.MAX_REQUESTS_PER_INTENT, capped.size());
    }

    @Test
    public void deduplicationRunsBeforeTheCapSoAFullBatchOfDuplicatesShrinks() {
        DownloadRequest[] allTheSame = new DownloadRequest[150];
        Arrays.fill(allTheSame, request("https://a.example/one.mp3"));

        List<DownloadRequest> kept = DownloadService.filterInFlight(allTheSame, NOTHING_IN_FLIGHT);
        assertEquals(1, kept.size());
        assertEquals(1, DownloadService.capToIntentLimit(kept).size());
    }

    // --------------------------------------------- transient-error classification

    @Test
    public void connectionProblemsAreTransient() {
        assertTrue(DownloadService.isTransientError(DownloadError.ERROR_CONNECTION_ERROR));
        assertTrue(DownloadService.isTransientError(DownloadError.ERROR_IO_ERROR));
        assertTrue(DownloadService.isTransientError(DownloadError.ERROR_UNKNOWN_HOST));
    }

    @Test
    public void permanentProblemsAreNotTransient() {
        assertFalse(DownloadService.isTransientError(DownloadError.ERROR_UNAUTHORIZED));
        assertFalse(DownloadService.isTransientError(DownloadError.ERROR_FILE_TYPE));
        assertFalse(DownloadService.isTransientError(DownloadError.ERROR_PARSER_EXCEPTION));
        assertFalse(DownloadService.isTransientError(DownloadError.ERROR_NOT_ENOUGH_SPACE));
        assertFalse(DownloadService.isTransientError(DownloadError.ERROR_HTTP_DATA_ERROR));
    }

    @Test
    public void anAbsentReasonIsNotTransient() {
        assertFalse(DownloadService.isTransientError(null));
    }

    // ------------------------------------------------------- DownloadRequest

    @Test
    public void requestsWithTheSameSourceAndDestinationAreEqual() {
        assertEquals(request("https://a.example/1.mp3"), request("https://a.example/1.mp3"));
        DownloadRequest request = request("https://a.example/1.mp3");
        assertEquals(request.hashCode(), request.hashCode());
    }

    @Test
    public void requestsWithADifferentSourceAreNotEqual() {
        assertNotEquals(request("https://a.example/1.mp3"), request("https://a.example/2.mp3"));
    }

    @Test
    public void retryCountStartsAtZeroAndRoundTrips() {
        DownloadRequest request = request("https://a.example/1.mp3");
        assertEquals(0, request.getRetryCount());
        request.setRetryCount(1);
        assertEquals(1, request.getRetryCount());
    }

    @Test
    public void theBuilderCarriesAuthenticationAndTheDeleteOnFailureFlag() {
        Feed feed = new Feed("https://a.example/feed.xml", null, "A feed");
        DownloadRequest request = new DownloadRequest.Builder("/tmp/dest", feed)
                .withAuthentication("user", "pass")
                .deleteOnFailure(true)
                .lastModified("Wed, 21 Oct 2015 07:28:00 GMT")
                .build();

        assertEquals("user", request.getUsername());
        assertEquals("pass", request.getPassword());
        assertTrue(request.isDeleteOnFailure());
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", request.getLastModified());
        assertEquals("/tmp/dest", request.getDestination());
        assertEquals(Feed.FEEDFILETYPE_FEED, request.getFeedfileType());
    }

    @Test
    public void setForceClearsTheLastModifiedHeader() {
        Feed feed = new Feed("https://a.example/feed.xml", null, "A feed");
        DownloadRequest.Builder builder = new DownloadRequest.Builder("/tmp/dest", feed)
                .lastModified("Wed, 21 Oct 2015 07:28:00 GMT");
        builder.setForce(true);

        assertNull(builder.build().getLastModified());
    }

    // --------------------------------------------------------------- helpers

    private static List<DownloadRequest> manyRequests(int count) {
        List<DownloadRequest> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            requests.add(request("https://a.example/" + i + ".mp3"));
        }
        return requests;
    }

    private static DownloadRequest request(String source) {
        return new DownloadRequest("/tmp/downloads/" + source.hashCode(), source, "title",
                1, Feed.FEEDFILETYPE_FEED, null, null, false, new Bundle(), true);
    }
}
