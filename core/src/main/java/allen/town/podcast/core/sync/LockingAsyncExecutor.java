package allen.town.podcast.core.sync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import allen.town.podcast.common.util.Timber;

public class LockingAsyncExecutor {

    static final ReentrantLock lock = new ReentrantLock();

    /**
     * Every task queued here contends for the same {@link #lock}, so running them on one thread
     * costs nothing and keeps the queue observable (unlike a fire-and-forget Rx subscription,
     * whose Disposable nobody could hold on to).
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LockingAsyncExecutor");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private LockingAsyncExecutor() {
    }

    /**
     * Take the lock and execute runnable (to prevent changes to preferences being lost when enqueueing while sync is
     * in progress). If the lock is free, the runnable is directly executed in the calling thread to prevent overhead.
     */
    public static void executeLockedAsync(Runnable runnable) {
        if (lock.tryLock()) {
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        } else {
            EXECUTOR.execute(() -> {
                lock.lock();
                try {
                    runnable.run();
                } catch (RuntimeException e) {
                    // Must not escape: an exception here would kill the executor's only thread and
                    // silently stop every later queued sync task.
                    Timber.e(e, "Locked sync task failed");
                } finally {
                    lock.unlock();
                }
            });
        }
    }
}
