package dev.russell.yagi.core.util.comparator;

import androidx.annotation.NonNull;

import java.util.Comparator;
import java.util.Locale;

/**
 * Compares strings the way a person reads file names: runs of digits compare by their numeric
 * value ("Chapter 2" before "Chapter 10") and letters compare case-insensitively. Strings that
 * are equal under those rules fall back to a plain comparison, so the order is total and
 * consistent with {@link String#equals}.
 */
public final class NaturalOrderComparator implements Comparator<String> {

    public static final NaturalOrderComparator INSTANCE = new NaturalOrderComparator();

    private NaturalOrderComparator() {
    }

    @Override
    public int compare(@NonNull String a, @NonNull String b) {
        int natural = compareNatural(a, b);
        if (natural != 0) {
            return natural;
        }
        return a.compareTo(b);
    }

    private static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        final int lengthA = a.length();
        final int lengthB = b.length();
        while (i < lengthA && j < lengthB) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int endA = digitRunEnd(a, i);
                int endB = digitRunEnd(b, j);
                int result = compareDigitRuns(a, i, endA, b, j, endB);
                if (result != 0) {
                    return result;
                }
                i = endA;
                j = endB;
            } else {
                if (ca != cb) {
                    String la = String.valueOf(ca).toLowerCase(Locale.ROOT);
                    String lb = String.valueOf(cb).toLowerCase(Locale.ROOT);
                    int result = la.compareTo(lb);
                    if (result != 0) {
                        return result;
                    }
                }
                i++;
                j++;
            }
        }
        return Integer.compare(lengthA - i, lengthB - j);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitRunEnd(String s, int start) {
        int end = start;
        while (end < s.length() && isDigit(s.charAt(end))) {
            end++;
        }
        return end;
    }

    /** Compares two digit runs by value without parsing them, so any length works. */
    private static int compareDigitRuns(String a, int startA, int endA, String b, int startB, int endB) {
        int sa = startA;
        while (sa < endA - 1 && a.charAt(sa) == '0') {
            sa++;
        }
        int sb = startB;
        while (sb < endB - 1 && b.charAt(sb) == '0') {
            sb++;
        }
        int lengthDiff = (endA - sa) - (endB - sb);
        if (lengthDiff != 0) {
            return lengthDiff < 0 ? -1 : 1;
        }
        for (int k = 0; k < endA - sa; k++) {
            int diff = a.charAt(sa + k) - b.charAt(sb + k);
            if (diff != 0) {
                return diff < 0 ? -1 : 1;
            }
        }
        // same value: fewer leading zeros first ("1" before "01")
        return Integer.compare(endA - startA, endB - startB);
    }
}
