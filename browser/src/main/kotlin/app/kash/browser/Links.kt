package app.kash.browser

import java.net.URI

/** Where a link of a service's page opens: in KaSH (the page itself, or a window of KaSH in the service's profile), or in the system's browser. */
internal enum class LinkTarget { KASH, SYSTEM }

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

private fun uri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()

/**
 * Where a link goes through Google's redirector (`https://www.google.com/url?q=<address>`), which Google's applications put around the links of
 * their messages: its address, which is on another site than Google's; the link itself otherwise.
 */
private fun unwrapped(target: URI): URI {
    val host = target.host?.lowercase() ?: return target
    if (!siteOf(host).startsWith("google.") || target.rawPath != "/url") return target
    val parameters = target.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val (key, value) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        key to runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8) }.getOrNull()
    }.toMap()
    return (parameters["q"] ?: parameters["url"])?.let(::uri)?.takeIf { it.isWeb() } ?: target
}

private fun URI.isWeb(): Boolean = scheme?.lowercase() in setOf("http", "https") && !host.isNullOrEmpty()

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

private fun sameSite(a: URI, b: URI?): Boolean = b != null && b.isWeb() && siteOf(a.host) == siteOf(b.host)

/** Whether [target] is a page where one signs in with an account of another site (Google, Microsoft, Apple, Facebook, GitHub). */
internal fun isSignIn(target: URI): Boolean {
    val host = target.host?.lowercase() ?: return false
    if (host in IDENTITY_HOSTS) return true
    val paths = IDENTITY_PATHS[siteOf(host)] ?: return false
    return paths.matches(target.rawPath.orEmpty())
}

/**
 * Where a window that a page of the service at [serviceUrl] opens goes (a link to a new window, `window.open`): in KaSH when it is on the
 * service's site or a sign-in page, in the system's browser when it is another site, or the mail, the phone. What is not an address of the web
 * (`about:blank`, which a page fills itself, `blob:`, `data:`) stays in KaSH.
 */
internal fun popupTarget(url: String, serviceUrl: String): LinkTarget {
    val target = uri(url)?.let(::unwrapped) ?: return LinkTarget.KASH
    if (target.scheme?.lowercase() in SYSTEM_SCHEMES) return LinkTarget.SYSTEM
    if (!target.isWeb()) return LinkTarget.KASH
    return if (sameSite(target, uri(serviceUrl)) || isSignIn(target)) LinkTarget.KASH else LinkTarget.SYSTEM
}

/**
 * Where a navigation of a service's page goes: in the system's browser when the user clicked a link (a gesture, a `GET`, not a redirect) to another
 * site than the service's at [serviceUrl] and than the page's at [pageUrl], which is not a sign-in page; in KaSH otherwise. What the site does
 * itself (its redirects, a form that it posts, as when signing in) stays in KaSH.
 */
internal fun navigationTarget(url: String, serviceUrl: String, pageUrl: String?, userGesture: Boolean, isRedirect: Boolean, method: String?): LinkTarget {
    if (!userGesture || isRedirect || !method.equals("GET", ignoreCase = true)) return LinkTarget.KASH
    val target = uri(url)?.let(::unwrapped) ?: return LinkTarget.KASH
    if (target.scheme?.lowercase() in SYSTEM_SCHEMES) return LinkTarget.SYSTEM
    if (!target.isWeb()) return LinkTarget.KASH
    val page = pageUrl?.let(::uri)
    return if (sameSite(target, uri(serviceUrl)) || sameSite(target, page) || isSignIn(target)) LinkTarget.KASH else LinkTarget.SYSTEM
}
