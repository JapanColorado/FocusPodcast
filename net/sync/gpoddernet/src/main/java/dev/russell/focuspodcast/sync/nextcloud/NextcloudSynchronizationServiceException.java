package dev.russell.focuspodcast.sync.nextcloud;

import dev.russell.focuspodcast.sync.model.SyncServiceException;

public class NextcloudSynchronizationServiceException extends SyncServiceException {
    public NextcloudSynchronizationServiceException(Throwable e) {
        super(e);
    }
}
