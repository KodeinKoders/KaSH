package app.kash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.core.KashDirectories
import app.kash.core.PluginIcon
import app.kash.core.PluginRegistry
import app.kash.core.Service
import app.kash.core.ServiceIcon
import app.kash.core.ServiceId
import app.kash.core.SquareColors
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.jetbrains.skia.Image as SkiaImage

private val logger = LoggerFactory.getLogger(ServiceIcons::class.java)

/**
 * The images of the services' icons: the images that the plugins' scripts give their services ([PluginIcon], `kash.setIcon`) and the sites' own
 * icons ([ServiceIcon.Favicon]), both kept on disk, and the images that the user chose ([ServiceIcon.Image]). They are read off Swing's thread;
 * until they are there, a tile shows the service's square.
 */
class ServiceIcons(private val directories: KashDirectories, val plugins: PluginRegistry) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val favicons = mutableStateMapOf<String, ImageBitmap>()
    private val images = mutableStateMapOf<String, ImageBitmap>()
    private val reported = mutableStateMapOf<ServiceId, ImageBitmap>()
    private val pluginIcons = HashMap<String, ImageBitmap?>()
    private val requested = HashSet<String>()

    /** The source that each service's script gave last, in this launch: the same one is not downloaded again. */
    private val sources = HashMap<ServiceId, PluginIcon>()

    // A browser's User-Agent: some sites answer another one with a page that has no icon ("your browser is not supported").
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()

    /** The icon of the site at [url], or null while it is fetched, or when it has none. */
    fun favicon(url: String): ImageBitmap? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return null
        if (requested.add("favicon:$host")) {
            scope.launch {
                withContext(Dispatchers.IO) { loadFavicon(host, url) }?.let { favicons[host] = it }
            }
        }
        return favicons[host]
    }

    /** The icon of the plugin [id] (its manifest's `icon`), decoded once; null for a web application, a plugin without one, or one that cannot be. */
    fun pluginIcon(id: String?): ImageBitmap? {
        val plugin = plugins[id] ?: return null
        return pluginIcons.getOrPut(plugin.id) {
            plugin.icon?.let(::decode).also { if (it == null && plugin.icon != null) logger.warn("The icon of the plugin {} cannot be drawn", plugin.id) }
        }
    }

    /** The image that the script of the service [id] gave, kept from a launch to the next; null while it is read, or when it gave none. */
    fun reported(id: ServiceId): ImageBitmap? {
        if (requested.add("reported:${id.value}")) {
            scope.launch {
                withContext(Dispatchers.IO) { directories.serviceIcon(id)?.let(::decode) }?.let { if (id !in sources) reported[id] = it }
            }
        }
        return reported[id]
    }

    /**
     * The script of the service [id] gave [icon] (`kash.setIcon`): [PluginIcon.None] forgets its image, another one is downloaded or decoded, kept on
     * disk and shown. An image that cannot be had keeps the one before. Called on Swing's thread.
     */
    fun report(id: ServiceId, icon: PluginIcon) {
        // An inline image is never equal to another one: the API sends the same one only once.
        if (sources[id] == icon) return
        sources[id] = icon
        requested += "reported:${id.value}"
        val file = directories.serviceIcon(id) ?: return
        if (icon == PluginIcon.None) {
            reported.remove(id)
            scope.launch { withContext(Dispatchers.IO) { runCatching { Files.deleteIfExists(file) } } }
            return
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val bytes = when (icon) {
                    is PluginIcon.Remote -> download(icon.uri)
                    is PluginIcon.Inline -> icon.bytes
                    PluginIcon.None -> null
                }
                val bitmap = bytes?.let(::decode)
                if (bytes != null && bitmap != null) {
                    runCatching {
                        Files.createDirectories(directories.serviceIcons)
                        Files.write(file, bytes)
                    }.onFailure { logger.warn("Cannot keep the icon of a service", it) }
                }
                bitmap
            }
            // Only the host in the log: the address can name the user's account.
            if (bitmap == null) logger.warn("The icon that a plugin gave cannot be shown ({})", (icon as? PluginIcon.Remote)?.uri?.host ?: "inline")
            // A source given since then wins.
            else if (sources[id] == icon) reported[id] = bitmap
        }
    }

    /** The image [file] of [KashDirectories.icons], or null while it is read, or when it cannot be. */
    fun image(file: String): ImageBitmap? {
        if (requested.add("image:$file")) {
            scope.launch {
                withContext(Dispatchers.IO) { decode(directories.icons.resolve(file)) }?.let { images[file] = it }
            }
        }
        return images[file]
    }

    /** Copies the image [source] in [KashDirectories.icons], and returns its name there; null when it is not an image that can be shown. */
    fun import(source: Path): String? {
        val bytes = runCatching { Files.readAllBytes(source) }.getOrNull() ?: return null
        val bitmap = decode(bytes) ?: return null
        val extension = source.fileName.toString().substringAfterLast('.', "img").lowercase()
        val name = "icon-${System.currentTimeMillis()}.$extension"
        Files.createDirectories(directories.icons)
        Files.write(directories.icons.resolve(name), bytes)
        images[name] = bitmap
        requested += "image:$name"
        return name
    }

    private fun loadFavicon(host: String, url: String): ImageBitmap? {
        val cached = directories.favicons.resolve("$host.icon")
        decode(cached)?.let { return it }
        for (candidate in candidates(url)) {
            val bytes = get(candidate)?.takeIf { it.isNotEmpty() } ?: continue
            val bitmap = decode(bytes) ?: continue
            Files.createDirectories(directories.favicons)
            Files.write(cached, bytes)
            return bitmap
        }
        logger.info("No icon for {}", host)
        return null
    }

    /** Where the site's icon may be: the large icons its page names first (`apple-touch-icon`), then the others, then `/favicon.ico`. */
    private fun candidates(url: String): List<String> {
        val base = URI(url)
        val html = get(url)?.toString(Charsets.UTF_8).orEmpty()
        val links = LINK.findAll(html).map { it.value }.mapNotNull { tag ->
            val rel = REL.find(tag)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            val href = HREF.find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            if ("icon" !in rel) null else rel to runCatching { base.resolve(href.replace("&amp;", "&")).toString() }.getOrNull()
        }.filter { it.second != null }.toList()
        val touch = links.filter { "apple-touch-icon" in it.first }.map { it.second!! }
        val others = links.filter { "apple-touch-icon" !in it.first }.map { it.second!! }
        return (touch + others + base.resolve("/favicon.ico").toString()).distinct()
    }

    /** The image at [uri], without the service's cookies, of at most [PluginIcon.MAX_BYTES]; null when it cannot be had. */
    private fun download(uri: URI): ByteArray? = try {
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("User-Agent", USER_AGENT).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() !in 200..299) null
            else body.readNBytes(PluginIcon.MAX_BYTES + 1).takeIf { it.isNotEmpty() && it.size <= PluginIcon.MAX_BYTES }
        }
    } catch (e: Exception) {
        null
    }

    private fun get(url: String): ByteArray? = try {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", USER_AGENT).GET().build()
        client.send(request, HttpResponse.BodyHandlers.ofByteArray()).takeIf { it.statusCode() in 200..299 }?.body()
    } catch (e: Exception) {
        null
    }

    private fun decode(path: Path): ImageBitmap? = if (Files.isRegularFile(path)) runCatching { decode(Files.readAllBytes(path)) }.getOrNull() else null

    /** Decoded by Skia (PNG, ICO, JPEG, WebP, GIF…); SVG is not, and an SVG icon is skipped for the next candidate. */
    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    private companion object {
        val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val REL = Regex("rel\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val HREF = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    }
}

/** What a tile draws, decided from a service or from the Add window's draft. */
sealed interface TileContent {
    data class Picture(val bitmap: ImageBitmap) : TileContent
    data class Square(val color: String, val text: String) : TileContent
    data object Globe : TileContent
    data object Image : TileContent
}

/** What the tile of [service] draws: its icon when it is there, else its square (its plugin's, or a globe for a web application). */
@Composable
fun tileContent(service: Service, icons: ServiceIcons): TileContent = tileContent(service.plugin, service.url, service.icon, icons, service.id)

/**
 * What the tile of a service of [plugin] (null for a web application) at [url] with [icon] draws. The plugin's icon and the automatic one are,
 * first, the image that the plugin's script of the service [id] gave (`kash.setIcon`: Slack's workspace's; null: a service that is not added yet),
 * then the plugin's icon or the site's, then the plugin's square, then a globe.
 */
@Composable
fun tileContent(plugin: String?, url: String, icon: ServiceIcon, icons: ServiceIcons, id: ServiceId? = null): TileContent = when (icon) {
    is ServiceIcon.Square -> TileContent.Square(icon.color, icon.text)
    ServiceIcon.Plugin -> id?.takeIf { plugin != null }?.let { icons.reported(it) }?.let { TileContent.Picture(it) }
        ?: icons.pluginIcon(plugin)?.let { TileContent.Picture(it) }
        ?: icons.plugins[plugin]?.manifest?.square?.let { TileContent.Square(it.color, it.text) }
        ?: TileContent.Globe
    is ServiceIcon.Image -> icons.image(icon.file)?.let { TileContent.Picture(it) } ?: TileContent.Image
    ServiceIcon.Favicon -> id?.takeIf { plugin != null }?.let { icons.reported(it) }?.let { TileContent.Picture(it) }
        ?: icons.favicon(url)?.let { TileContent.Picture(it) }
        ?: icons.plugins[plugin]?.manifest?.square?.let { TileContent.Square(it.color, it.text) }
        ?: TileContent.Globe
}

/** The tile of [service]: its icon on a rounded square of [size]. */
@Composable
fun ServiceTile(service: Service, icons: ServiceIcons, size: Dp, modifier: Modifier = Modifier) {
    Tile(tileContent(service, icons), size, modifier)
}

/** A rounded square of [size] that draws [content]: the radius and the text grow with it as in the design (40 → 10 dp, 14 sp). */
@Composable
fun Tile(content: TileContent, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 4)
    val base = modifier.size(size).clip(shape)
    when (content) {
        is TileContent.Picture -> Image(content.bitmap, contentDescription = null, modifier = base, contentScale = ContentScale.Crop)
        is TileContent.Square -> Box(base.background(Color(0xFF000000 or SquareColors.rgb(content.color).toLong())), contentAlignment = Alignment.Center) {
            Text(
                content.text,
                color = if (SquareColors.whiteTextOn(content.color)) Color.White else Color(0xFF1E1F22),
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        TileContent.Globe -> Box(base.background(Kash.colors.neutral), contentAlignment = Alignment.Center) {
            KashIcon(KashIcons.Globe, Kash.colors.text, size = size * 0.55f)
        }
        TileContent.Image -> Box(base.background(Kash.colors.neutral), contentAlignment = Alignment.Center) {
            KashIcon(KashIcons.Picture, Kash.colors.text, size = size * 0.55f)
        }
    }
}
