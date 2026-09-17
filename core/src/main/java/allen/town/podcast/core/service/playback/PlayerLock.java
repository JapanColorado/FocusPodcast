package allen.town.podcast.core.service.playback;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * All ExoPlayer methods must be executed on the same thread.
 * We use the main application thread. This class allows to
 * "fake" a lock that does nothing. A lock is not needed if
 * everything is called on the same thread.
 * Other players are still executed in a background thread and
 * therefore use a real lock.
 * The flag deciding between the two modes is owned by {@link LocalPSMP} and shared with this class
 * and {@link PlayerExecutor}.
 */
class PlayerLock {
    private final AtomicBoolean useCallerThread;
    private final ReentrantLock lock = new ReentrantLock();

    PlayerLock(AtomicBoolean useCallerThread) {
        this.useCallerThread = useCallerThread;
    }

    void lock() {
        if (!useCallerThread.get()) {
            lock.lock();
        }
    }

    boolean tryLock(int i, TimeUnit milliseconds) throws InterruptedException {
        if (!useCallerThread.get()) {
            return lock.tryLock(i, milliseconds);
        }
        return true;
    }

    boolean tryLock() {
        if (!useCallerThread.get()) {
            return lock.tryLock();
        }
        return true;
    }

    void unlock() {
        if (!useCallerThread.get()) {
            lock.unlock();
        }
    }

    boolean isHeldByCurrentThread() {
        if (!useCallerThread.get()) {
            return lock.isHeldByCurrentThread();
        }
        return true;
    }
}
