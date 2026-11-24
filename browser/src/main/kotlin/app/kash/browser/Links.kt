package app.kash.browser

import java.net.URI

/**
 * Where a link of a service's page opens: in KaSH (the page itself, or a window of KaSH in the service's profile), in the system's browser, or
 * nowhere (an address of another application, which KaSH does not load: the page stays where it is).
 */
internal enum class LinkTarget { KASH, SYSTEM, NOWHERE }

// The schemes that a page of KaSH loads: the web's, and those of Chromium's own pages and of what a page makes itself. Any other one is an
// application's (`slack://`, `zoommtg://`), which Chromium cannot load ("ERR_UNKNOWN_URL_SCHEME" in place of the page): Slack sends there after
// signing in, to open its desktop application, and its page offers to stay in the browser when it is not.
private val PAGE_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript", "chrome", "chrome-error", "devtools")

// The schemes that another application of the system handles: the mail, the phone, the messages.
private val SYSTEM_SCHEMES = setOf("mailto", "tel", "sms", "facetime", "facetime-audio")

// The second-level labels under which a country's top-level domain gives names (`co.uk`, `com.br`): without a public suffix list, a site of such a
// country is its last three labels.
private val SECOND_LEVELS = setOf("co", "com", "net", "org", "gov", "edu", "ac", "ne", "or", "go")

// The pages where one signs in with an account of another site, which have to stay in the service's profile (the popup reports to its opener):
// their whole host, or the paths of the host that are the sign-in's.
private val IDENTITY_HOSTS = setOf(
    "accounts.google.com", "accounts.youtube.com",
    "login.microsoftonline.com", "login.microsoft.com", "login.live.com",
    "appleid.apple.com", "idmsa.apple.com",
)
private val IDENTITY_PATHS = mapOf(
    // Facebook's dialogs are also under a version of its API: `/v19.0/dialog/oauth`.
    "facebook.com" to Regex("^(/v\\d+(\\.\\d+)?)?/dialog/.*|^/login(/.*|\\.php.*)?$"),
    "github.com" to Regex("^/login(/.*)?$"),
)

// What Chromium keeps in an address and java.net.URI refuses: written as `%XX` before the address is read again.
private const val REFUSED = "\"<>\\^`{|}"
private const val HEX_DIGITS = "0123456789abcdefABCDEF"

/**
 * The address [url], as `java.net.URI` reads it, or null when it is not one. Chromium's addresses keep characters that `URI` refuses (`|`, `{`,
 * `^`, a `%` that is not an escape, a space, a letter that is not ASCII): such an address is read again with them escaped ([escaped]). Unread, a
 * link to another site stayed in KaSH, in the service's profile.
 */
internal fun parseAddress(url: String): URI? {
    val trimmed = url.trim()
    return runCatching { URI(trimmed) }.getOrNull() ?: runCatching { URI(escaped(trimmed)) }.getOrNull()
}

// [url] with what `URI` refuses written as `%XX` (UTF-8): the characters of REFUSED, the controls and the space, what is not ASCII, a `%` that
// two hexadecimal digits do not follow, and `[` and `]` after the authority (an IPv6 host has them).
private fun escaped(url: String): String {
    val start = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: 0
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), start).takeIf { it >= 0 } ?: url.length
    val out = StringBuilder()
    var i = 0
    while (i < url.length) {
        val point = url.codePointAt(i)
        val size = Character.charCount(point)
        val escape = when {
            point == '%'.code -> !(i + 2 < url.length && url[i + 1] in HEX_DIGITS && url[i + 2] in HEX_DIGITS)
            point <= 0x20 || point >= 0x7F -> true
            point < 0x80 && point.toChar() in REFUSED -> true
            (point == '['.code || point == ']'.code) && i >= authorityEnd -> true
            else -> false
        }
        if (escape) String(Character.toChars(point)).toByteArray(Charsets.UTF_8).forEach { out.append('%').append("%02X".format(it)) }
        else out.append(url, i, i + size)
        i += size
    }
    return out.toString()
}

private fun uri(url: String): URI? = parseAddress(url)

// Whether [url] says it is an address of the web, read or not.
private fun looksWeb(url: String): Boolean = url.trim().let { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }

/**
 * The host of this address: `URI`'s, or the one of its authority when `URI` reads none (a host with `_`, `a_b.example.com`, which `URI` takes for
 * a registry's name), without its user and its port.
 */
private fun URI.hostName(): String? {
    host?.takeIf { it.isNotEmpty() }?.let { return it }
    val authority = rawAuthority?.substringAfterLast('@') ?: return null
    val name = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    return name.takeIf { it.isNotEmpty() }
}

/**
 * Where a link goes through Google's redirector (`https://www.google.com/url?q=<address>`), which Google's applications put around the links of
 * their messages: its address, which is on another site than Google's; the link itself otherwise.
 */
private fun unwrapped(target: URI): URI {
    val host = target.hostName()?.lowercase() ?: return target
    if (!siteOf(host).startsWith("google.") || target.rawPath != "/url") return target
    val parameters = target.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val (key, value) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        key to runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8) }.getOrNull()
    }.toMap()
    return (parameters["q"] ?: parameters["url"])?.let(::uri)?.takeIf { it.isWeb() } ?: target
}

private fun URI.isWeb(): Boolean = scheme?.lowercase() in setOf("http", "https") && !hostName().isNullOrEmpty()

/**
 * The site of [host]: its last two labels (`web.whatsapp.com` → `whatsapp.com`), or three under a country's second level (`www.bbc.co.uk` →
 * `bbc.co.uk`). An address, or a name of one label, is its own site.
 */
internal fun siteOf(host: String): String {
    val name = host.lowercase().trimEnd('.')
    if (name.startsWith("[") || name.all { it.isDigit() || it == '.' }) return name
    val labels = name.split('.')
    if (labels.size <= 2) return name
    val count = if (labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVELS) 3 else 2
    return labels.takeLast(count).joinToString(".")
}

/**
 * The pattern of an address where `*` matches any run of characters and the rest is literal, as a plugin's manifest writes them (`matches`,
 * `openInKash`).
 */
internal fun addressPattern(address: String): Regex =
    Regex(address.split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.IGNORE_CASE)

private fun List<Regex>.matchAny(target: URI): Boolean = isNotEmpty() && target.toString().let { url -> any { it.matches(url) } }

private fun sameSite(a: URI, b: URI?): Boolean = b != null && b.isWeb() && siteOf(a.hostName()!!) == siteOf(b.hostName()!!)

/**
 * Whether a page at [frameUrl] is the service's own, on the site of its address [serviceUrl]: the only page whose messages KaSH takes (its unread
 * conversations, its notifications). Not a frame of another site in it (an advertisement, an embed), nor another site that the tab went to (a
 * sign-in page, a page that a redirection led to).
 */
internal fun isServicePage(frameUrl: String?, serviceUrl: String): Boolean {
    val frame = frameUrl?.let(::uri)?.takeIf { it.isWeb() } ?: return false
    return sameSite(frame, uri(serviceUrl))
}

/** Whether [target] is a page where one signs in with an account of another site (Google, Microsoft, Apple, Facebook, GitHub). */
internal fun isSignIn(target: URI): Boolean {
    val host = target.hostName()?.lowercase() ?: return false
    if (host in IDENTITY_HOSTS) return true
    val paths = IDENTITY_PATHS[siteOf(host)] ?: return false
    return paths.matches(target.rawPath.orEmpty())
}

/**
 * Where a window that a page of the service at [serviceUrl] opens goes (a link to a new window, `window.open`): in KaSH when it is on the
 * service's site, a sign-in page or one of [openInKash] (its plugin's, [addressPattern]), in the system's browser when it is another site, or the
 * mail, the phone. What is not an address of the web (`about:blank`, which a page fills itself, `blob:`, `data:`) stays in KaSH.
 */
internal fun popupTarget(url: String, serviceUrl: String, openInKash: List<Regex> = emptyList()): LinkTarget {
    // An address of the web that cannot be read even escaped is not known to be the service's: the system's browser, not the service's profile.
    val target = uri(url)?.let(::unwrapped) ?: return if (looksWeb(url)) LinkTarget.SYSTEM else LinkTarget.KASH
    val scheme = target.scheme?.lowercase()
    if (scheme in SYSTEM_SCHEMES) return LinkTarget.SYSTEM
    if (scheme != null && scheme !in PAGE_SCHEMES) return LinkTarget.NOWHERE
    if (!target.isWeb()) return LinkTarget.KASH
    return if (sameSite(target, uri(serviceUrl)) || isSignIn(target) || openInKash.matchAny(target)) LinkTarget.KASH else LinkTarget.SYSTEM
}

/**
 * Where a navigation of a service's page goes: nowhere when it is an application's address (see PAGE_SCHEMES); in the system's browser when the
 * user clicked a link (a gesture, a `GET`, not a redirect) to another
 * site than the service's at [serviceUrl] and than the page's at [pageUrl], which is not a sign-in page nor one of [openInKash]; in KaSH otherwise.
 * What the site does itself (its redirects, a form that it posts, as when signing in) stays in KaSH.
 */
internal fun navigationTarget(
    url: String, serviceUrl: String, pageUrl: String?, userGesture: Boolean, isRedirect: Boolean, method: String?, openInKash: List<Regex> = emptyList(),
): LinkTarget {
    val clicked = userGesture && !isRedirect
    // An address of the web that cannot be read even escaped, clicked: the system's browser, not the service's profile.
    val target = uri(url)?.let(::unwrapped)
        ?: return if (looksWeb(url) && clicked && method.equals("GET", ignoreCase = true)) LinkTarget.SYSTEM else LinkTarget.KASH
    val scheme = target.scheme?.lowercase()
    // The mail and the phone when the user clicked; an application's address never, even when the site sends there itself.
    if (scheme in SYSTEM_SCHEMES) return if (clicked) LinkTarget.SYSTEM else LinkTarget.NOWHERE
    if (scheme != null && scheme !in PAGE_SCHEMES) return LinkTarget.NOWHERE
    if (!clicked || !method.equals("GET", ignoreCase = true)) return LinkTarget.KASH
    if (!target.isWeb()) return LinkTarget.KASH
    val page = pageUrl?.let(::uri)
    return if (sameSite(target, uri(serviceUrl)) || sameSite(target, page) || isSignIn(target) || openInKash.matchAny(target)) LinkTarget.KASH else LinkTarget.SYSTEM
}
