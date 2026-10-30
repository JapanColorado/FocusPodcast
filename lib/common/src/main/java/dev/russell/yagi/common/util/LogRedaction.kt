package dev.russell.yagi.common.util

/**
 * Shortens every web address in a log to its scheme and host before the log is shared, because
 * bug reports go to public GitHub issues and private feeds (Patreon, Supercast, ...) carry their
 * access token in the path, the query or the user info of the address.
 */
object LogRedaction {
    private val URL = Regex("""\b(https?)://(?:[^\s/?#@]*@)?([^\s/?#:@"'<>]+)(?::\d+)?[^\s"'<>]*""")

    @JvmStatic
    fun redactUrls(log: String): String = URL.replace(log) { match ->
        "${match.groupValues[1]}://${match.groupValues[2]}/..."
    }
}
