package dev.russell.yagi.core.util.comparator;

import java.util.Comparator;

import dev.russell.yagi.model.feed.Chapter;

public class ChapterStartTimeComparator implements Comparator<Chapter> {

	@Override
	public int compare(Chapter lhs, Chapter rhs) {
		return Long.compare(lhs.getStart(), rhs.getStart());
	}

}
