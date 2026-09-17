package allen.town.podcast.core.service.playback;

import android.util.Log;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All ExoPlayer methods must be executed on the same thread.
 * We use the main application thread. This class allows to
 * "fake" an executor that just calls the methods on the
 * calling thread instead of submitting to an executor.
 * Other players are still executed in a background thread.
 * The flag deciding between the two modes is owned by {@link LocalPSMP}, which flips it whenever
 * it (re-)creates a player, and shared with this class and {@link PlayerLock}.
 */
class PlayerExecutor {
    private static final String TAG = "LocalMediaPlayer";

    private final AtomicBoolean useCallerThread;
    private final ThreadPoolExecutor threadPool;

    PlayerExecutor(AtomicBoolean useCallerThread) {
        this.useCallerThread = useCallerThread;
        this.threadPool = new ThreadPoolExecutor(1, 1, 5, TimeUnit.MINUTES, new LinkedBlockingDeque<>(),
                (r, executor) -> Log.d(TAG, "Rejected execution of runnable"));
    }

    Future<?> submit(Runnable r) {
        if (useCallerThread.get()) {
            r.run();
            return new FutureTask<Void>(() -> { }, null);
        } else {
            return threadPool.submit(r);
        }
    }

    void shutdown() {
        threadPool.shutdown();
    }
}
