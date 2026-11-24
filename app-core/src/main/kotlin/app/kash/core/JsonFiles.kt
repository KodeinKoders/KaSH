package app.kash.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val ASIDE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * Moves the file at [path], which cannot be read, next to itself as `<name>.unreadable-<time>`, and returns where it went: what is saved next does
 * not replace it, and the user's data is still there to be read again (a version of KaSH that knows a value that this one does not, a file fixed
 * by hand).
 */
internal fun setAside(path: Path, now: LocalDateTime = LocalDateTime.now()): Path {
    val aside = path.resolveSibling("${path.fileName}.unreadable-${ASIDE_TIME.format(now)}")
    Files.move(path, aside, StandardCopyOption.REPLACE_EXISTING)
    return aside
}

/**
 * Writes [text] to a temporary file next to [path], forced to the disk, then moves it in place: a crash or a power loss while writing leaves the
 * file before, never an empty or truncated one.
 */
internal fun writeDurably(path: Path, text: String) {
    val absolute = path.toAbsolutePath()
    Files.createDirectories(absolute.parent)
    val temporary = absolute.resolveSibling("${absolute.fileName}.tmp")
    FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
        val bytes = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
    }
    Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/**
 * A file of KaSH's that holds a [T] as JSON: one way for each of them (`state.json`, `plugins.json`, `window.json`, `unread-times.json`,
 * `contacts.json`, `notification-reads.json`) to be read, set aside and written, where six copies of the same code had drifted apart.
 *
 * - None: [empty].
 * - One that cannot be read: set aside ([setAside], unless [keepUnreadable] is false: what it holds is nothing worth keeping) and logged, saying
 *   what follows ([unreadable]), then [empty].
 * - One that a later version of KaSH wrote (its `version` greater than [version]): logged, and read as this one can (an unknown value that has a
 *   default is the default, an unknown key is left out): it was misread without a word.
 * - Written durably ([writeDurably]), one write at a time.
 */
internal class JsonFile<T>(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val empty: () -> T,
    private val unreadable: String,
    private val version: Int? = 1,
    private val keepUnreadable: Boolean = true,
) {
    @Synchronized
    fun load(): T {
        if (!Files.exists(path)) return empty()
        return try {
            val element = JSON.parseToJsonElement(Files.readString(path))
            val written = ((element as? JsonObject)?.get("version") as? JsonPrimitive)?.intOrNull
            if (version != null && written != null && written > version) {
                logger.warn("{} was written by a later version of KaSH (its version {}, this one's {}): read as this one can", path.fileName, written, version)
            }
            JSON.decodeFromJsonElement(serializer, element)
        } catch (e: Exception) {
            if (keepUnreadable) {
                val aside = setAside(path)
                logger.error("Cannot read {}: {}; it is kept as {}", path, unreadable, aside.fileName, e)
            } else {
                logger.warn("Cannot read {}: {}", path, unreadable, e)
            }
            empty()
        }
    }

    @Synchronized
    fun save(value: T) {
        writeDurably(path, JSON.encodeToString(serializer, value))
    }

    private companion object {
        val logger = LoggerFactory.getLogger(JsonFile::class.java)

        val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            // A value that this version does not know (an enum's, from another branch or a later version) is its default, not a file that
            // cannot be read.
            coerceInputValues = true
        }
    }
}

/** A short hash of [text] (the first 16 bytes of its SHA-256, in hexadecimal): what is kept of an id or a name that says who someone is. */
internal fun shortHash(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).take(16).joinToString("") { "%02x".format(it) }
