package app.kash.core

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
