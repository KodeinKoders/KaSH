package app.kash.core

import java.net.URI
import java.util.Base64

/**
 * The image that a plugin's script gives as its service's icon (`kash.setIcon`), which the service's tile shows when its icon is an automatic
 * one ([ServiceIcon.Plugin] or [ServiceIcon.Favicon]), in place of its plugin's icon or its site's.
 */
public sealed interface PluginIcon {
    /** The service has none: its tile shows its plugin's icon or its site's, as its icon says. */
    public data object None : PluginIcon

    /** An image that KaSH downloads, without the service's cookies. */
    public data class Remote(val uri: URI) : PluginIcon

    /** An image that the script gave whole (a `data:` URL). */
    public class Inline(public val bytes: ByteArray) : PluginIcon

    public companion object {
        /** The largest image that KaSH takes: an icon is a few kilobytes. */
        public const val MAX_BYTES: Int = 1024 * 1024

        private val DATA = Regex("^data:image/(png|jpeg|gif|webp|x-icon|vnd\\.microsoft\\.icon);base64,(.*)$", RegexOption.IGNORE_CASE)

        /**
         * What [source] is: null is [None], an `https:` URL with a host is [Remote], a base64 `data:` URL of an image of at most [MAX_BYTES] is
         * [Inline]. Anything else (another scheme, `http:`, an SVG, too big) is not an icon: null.
         */
        public fun parse(source: String?): PluginIcon? {
            if (source == null) return None
            DATA.matchEntire(source)?.let { match ->
                // The base64 is about 4/3 of the image: a longer one is refused before it is decoded.
                if (match.groupValues[2].length > MAX_BYTES / 3 * 4 + 4) return null
                val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2].trim()) }.getOrNull() ?: return null
                return if (bytes.isEmpty() || bytes.size > MAX_BYTES) null else Inline(bytes)
            }
            val uri = runCatching { URI(source) }.getOrNull() ?: return null
            return if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()) Remote(uri) else null
        }
    }
}
