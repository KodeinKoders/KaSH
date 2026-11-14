package app.kash

import org.slf4j.LoggerFactory
import java.awt.EventQueue
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("app.kash.Hangs")

private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

/**
 * Writes what every thread does (its Java stack, the locks it holds and the one it waits for) to a new file of [directory], named by the time
 * and [reason]: what tells where KaSH is stuck. It has no content of the pages or the messages, only code.
 *
 * @return The file, or null when it could not be written.
 */
internal fun dumpThreads(directory: Path, reason: String): Path? = runCatching {
    Files.createDirectories(directory)
    val file = directory.resolve("${LocalDateTime.now().format(FILE_TIME)}-$reason.txt")
    val text = buildString {
        appendLine("KaSH: $reason, ${LocalDateTime.now()}")
        appendLine()
        ManagementFactory.getThreadMXBean().dumpAllThreads(true, true).forEach { appendThread(it) }
    }
    Files.writeString(file, text)
    file
}.onFailure { logger.error("Cannot write the threads to {}", directory, it) }.getOrNull()

// ThreadInfo.toString() stops after 8 frames: the whole stack is what tells where a thread waits.
private fun StringBuilder.appendThread(info: ThreadInfo) {
    append('"').append(info.threadName).append("\" #").append(info.threadId)
    if (info.isDaemon) append(" daemon")
    append(' ').append(info.threadState)
    info.lockName?.let { append(" on ").append(it) }
    info.lockOwnerName?.let { append(" owned by \"").append(it).append("\" #").append(info.lockOwnerId) }
    if (info.isInNative) append(" (in native)")
    appendLine()
    info.stackTrace.forEachIndexed { index, frame ->
        append("    at ").appendLine(frame)
        info.lockedMonitors.filter { it.lockedStackDepth == index }.forEach { append("    - locked ").appendLine(it) }
    }
    if (info.lockedSynchronizers.isNotEmpty()) {
        append("    Locked synchronizers: ").appendLine(info.lockedSynchronizers.joinToString())
    }
    appendLine()
}

/**
 * Watches Swing's thread, which every window of KaSH, the system's Quit (⌘Q, the menu, the Dock) and JCEF's work go through: when it does not
 * answer for [stuckAfter], what the threads do is written to [directory], once per freeze, and logged.
 *
 * KaSH sometimes could not be quit any more, and could not be made to happen again: this is what tells where it was stuck the next time.
 */
internal fun watchEventThread(directory: Path, period: Duration = 2.seconds, stuckAfter: Duration = 10.seconds) {
    val answered = AtomicLong(System.nanoTime())
    thread(isDaemon = true, name = "KaSH-event-thread-watcher") {
        var dumped = false
        while (true) {
            Thread.sleep(period.inWholeMilliseconds)
            EventQueue.invokeLater { answered.set(System.nanoTime()) }
            val silent = (System.nanoTime() - answered.get()) / 1_000_000_000
            if (silent >= stuckAfter.inWholeSeconds) {
                if (!dumped) {
                    dumped = true
                    logger.error("Swing's thread has not answered for {} s: what the threads do is in {}", silent, dumpThreads(directory, "event-thread-stuck"))
                }
            } else if (dumped) {
                dumped = false
                logger.warn("Swing's thread answers again")
            }
        }
    }
}

/**
 * Ends the process when KaSH has not quit [timeout] after it started to: what the threads do is written to [directory] first, and logged.
 * KaSH's state and the window are saved before the quit starts; what Chromium had not written yet may be lost, as it is when the system forces
 * KaSH to quit, which was the only way out before.
 */
internal fun haltIfNotQuitIn(timeout: Duration, directory: Path) {
    thread(isDaemon = true, name = "KaSH-quit-watchdog") {
        Thread.sleep(timeout.inWholeMilliseconds)
        logger.error("KaSH did not quit in {}: it is stopped, what the threads did is in {}", timeout, dumpThreads(directory, "quit-stuck"))
        Runtime.getRuntime().halt(1)
    }
}
