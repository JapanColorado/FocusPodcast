package dev.russell.focuspodcast.core.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationPermissionGateTest {

    private static final int T = NotificationPermissionGate.FIRST_RUNTIME_PERMISSION_SDK;

    @Test
    public void explainsOnceWhenAnEpisodeDownloadStartsOnAndroid13() {
        assertTrue(NotificationPermissionGate.shouldExplain(T, false, false, true));
        assertTrue(NotificationPermissionGate.shouldExplain(T + 3, false, false, true));
    }

    @Test
    public void neverExplainsBeforeAndroid13() {
        assertFalse(NotificationPermissionGate.shouldExplain(T - 1, false, false, true));
    }

    @Test
    public void neverExplainsWhenNotificationsAreAlreadyAllowed() {
        assertFalse(NotificationPermissionGate.shouldExplain(T, true, false, true));
    }

    @Test
    public void neverExplainsTwice() {
        assertFalse(NotificationPermissionGate.shouldExplain(T, false, true, true));
    }

    @Test
    public void feedRefreshesAloneDoNotTriggerIt() {
        assertFalse(NotificationPermissionGate.shouldExplain(T, false, false, false));
    }
}
