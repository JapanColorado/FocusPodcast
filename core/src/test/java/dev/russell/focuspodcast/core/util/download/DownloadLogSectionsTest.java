package dev.russell.focuspodcast.core.util.download;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadLogSectionsTest {

    @Test
    public void emptyListHasNoRowsAndNoHeaders() {
        DownloadLogSections sections = new DownloadLogSections(0, 0);
        assertEquals(0, sections.getItemCount());
        assertEquals(DownloadLogSections.ROW_NONE, sections.getRowType(0));
        assertEquals(-1, sections.firstRunningPosition());
    }

    @Test
    public void bothSectionsGetAHeader() {
        DownloadLogSections sections = new DownloadLogSections(2, 3);
        assertEquals(2 + 1 + 3 + 1, sections.getItemCount());

        assertEquals(DownloadLogSections.ROW_RUNNING_HEADER, sections.getRowType(0));
        assertEquals(DownloadLogSections.ROW_RUNNING, sections.getRowType(1));
        assertEquals(DownloadLogSections.ROW_RUNNING, sections.getRowType(2));
        assertEquals(DownloadLogSections.ROW_LOG_HEADER, sections.getRowType(3));
        assertEquals(DownloadLogSections.ROW_LOG, sections.getRowType(4));
        assertEquals(DownloadLogSections.ROW_LOG, sections.getRowType(6));
        assertEquals(DownloadLogSections.ROW_NONE, sections.getRowType(7));

        assertEquals(0, sections.toRunningIndex(1));
        assertEquals(1, sections.toRunningIndex(2));
        assertEquals(-1, sections.toRunningIndex(3));
        assertEquals(0, sections.toLogIndex(4));
        assertEquals(2, sections.toLogIndex(6));
        assertEquals(-1, sections.toLogIndex(3));
        assertEquals(-1, sections.toLogIndex(1));
        assertEquals(1, sections.firstRunningPosition());
    }

    @Test
    public void onlyProblemsHidesTheInProgressHeader() {
        DownloadLogSections sections = new DownloadLogSections(0, 2);
        assertEquals(3, sections.getItemCount());
        assertEquals(DownloadLogSections.ROW_LOG_HEADER, sections.getRowType(0));
        assertEquals(0, sections.toLogIndex(1));
        assertEquals(1, sections.toLogIndex(2));
        assertEquals(-1, sections.firstRunningPosition());
    }

    @Test
    public void onlyRunningHidesTheProblemsHeader() {
        DownloadLogSections sections = new DownloadLogSections(1, 0);
        assertEquals(2, sections.getItemCount());
        assertEquals(DownloadLogSections.ROW_RUNNING_HEADER, sections.getRowType(0));
        assertEquals(0, sections.toRunningIndex(1));
        assertEquals(DownloadLogSections.ROW_NONE, sections.getRowType(2));
    }

    @Test
    public void negativePositionsAreOutsideTheList() {
        DownloadLogSections sections = new DownloadLogSections(1, 1);
        assertEquals(DownloadLogSections.ROW_NONE, sections.getRowType(-1));
        assertEquals(-1, sections.toRunningIndex(-1));
        assertEquals(-1, sections.toLogIndex(-1));
    }
}
