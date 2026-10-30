package dev.russell.yagi.core.util;

import android.net.Uri;
import android.os.Build;

/** Web addresses of the project on GitHub: the repository, its issues, releases and privacy policy. */
public final class ProjectLinks {
    public static final String REPOSITORY = "https://github.com/JapanColorado/Yagi";
    public static final String ISSUES = REPOSITORY + "/issues";
    public static final String RELEASES = REPOSITORY + "/releases";
    public static final String PRIVACY_POLICY = REPOSITORY + "/blob/main/PRIVACY.md";

    private ProjectLinks() {
    }

    /**
     * The bug report form of {@code .github/ISSUE_TEMPLATE/bug_report.yml}, with the app version,
     * Android version and device filled in (GitHub fills form fields from query parameters named
     * after their ids).
     */
    public static String newBugReport(String versionName) {
        return Uri.parse(ISSUES + "/new").buildUpon()
                .appendQueryParameter("template", "bug_report.yml")
                .appendQueryParameter("version", versionName)
                .appendQueryParameter("android_version", Build.VERSION.RELEASE)
                .appendQueryParameter("device", Build.MANUFACTURER + " " + Build.MODEL)
                .build()
                .toString();
    }
}
