package dev.russell.focuspodcast.core.util.download;

/**
 * The row layout of the downloads "Activity" list: an "In progress" section with the running
 * downloads, then a "Problems" section with the download log. Each section starts with a header
 * row, and a section without rows is left out entirely, header included.
 *
 * <p>Pure position arithmetic, kept out of the adapter so that it can be unit tested and so that
 * no caller computes offsets by hand.</p>
 */
public final class DownloadLogSections {

    public static final int ROW_RUNNING_HEADER = 0;
    public static final int ROW_RUNNING = 1;
    public static final int ROW_LOG_HEADER = 2;
    public static final int ROW_LOG = 3;
    /** A position outside the list, e.g. a stale position after the data changed. */
    public static final int ROW_NONE = -1;

    private final int runningCount;
    private final int logCount;

    public DownloadLogSections(int runningCount, int logCount) {
        this.runningCount = Math.max(0, runningCount);
        this.logCount = Math.max(0, logCount);
    }

    public int getRunningCount() {
        return runningCount;
    }

    public int getLogCount() {
        return logCount;
    }

    /** Rows taken by the running section, header included; 0 when there is nothing running. */
    private int runningSectionSize() {
        return runningCount > 0 ? runningCount + 1 : 0;
    }

    public int getItemCount() {
        return runningSectionSize() + (logCount > 0 ? logCount + 1 : 0);
    }

    public int getRowType(int position) {
        if (position < 0 || position >= getItemCount()) {
            return ROW_NONE;
        }
        int running = runningSectionSize();
        if (position < running) {
            return position == 0 ? ROW_RUNNING_HEADER : ROW_RUNNING;
        }
        return position == running ? ROW_LOG_HEADER : ROW_LOG;
    }

    /** The index into the running downloads for a running row, or -1 for any other row. */
    public int toRunningIndex(int position) {
        return getRowType(position) == ROW_RUNNING ? position - 1 : -1;
    }

    /** The index into the download log for a log row, or -1 for any other row. */
    public int toLogIndex(int position) {
        return getRowType(position) == ROW_LOG ? position - runningSectionSize() - 1 : -1;
    }

    /** The adapter position of the first running row, or -1 when nothing is running. */
    public int firstRunningPosition() {
        return runningCount > 0 ? 1 : -1;
    }
}
