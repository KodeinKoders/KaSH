package app.kash.macos

import org.slf4j.LoggerFactory
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger("app.kash.macos.MacosLibrary")

/**
 * libkash_macos.dylib (`native/kash_macos.m`), extracted from the jar to a temporary file and loaded once. Null when it cannot be (not on macOS):
 * what it does is then unavailable.
 */
internal object MacosLibrary {
    private val linker = Linker.nativeLinker()

    private val lookup: SymbolLookup? by lazy {
        if (!System.getProperty("os.name").startsWith("Mac")) return@lazy null
        try {
            val resource = MacosLibrary::class.java.getResourceAsStream("libkash_macos.dylib") ?: error("libkash_macos.dylib is not in the jar")
            val file = Files.createTempFile("kash_macos", ".dylib").also { it.toFile().deleteOnExit() }
            resource.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
            SymbolLookup.libraryLookup(file, Arena.global())
        } catch (e: Throwable) {
            logger.error("Cannot load libkash_macos.dylib: notifications and the login item are unavailable", e)
            null
        }
    }

    fun function(name: String, descriptor: FunctionDescriptor): MethodHandle? =
        lookup?.find(name)?.orElse(null)?.let { linker.downcallHandle(it, descriptor) }

    /** A C function pointer that calls [onCall] with the C string it is given: kept alive for the whole process. */
    fun stringCallback(onCall: (String) -> Unit): MemorySegment {
        val target = MethodHandles.lookup()
            .findVirtual(StringCallback::class.java, "invoke", MethodType.methodType(Void.TYPE, MemorySegment::class.java))
            .bindTo(StringCallback(onCall))
        return linker.upcallStub(target, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), Arena.global())
    }

    /** [value] as a C string, in [arena]. */
    fun cString(arena: Arena, value: String): MemorySegment = arena.allocateFrom(value)

    class StringCallback(private val onCall: (String) -> Unit) {
        fun invoke(string: MemorySegment) {
            // An exception that escapes an upcall takes the JVM down.
            try {
                onCall(string.reinterpret(Long.MAX_VALUE).getString(0))
            } catch (e: Throwable) {
                logger.error("A callback of libkash_macos failed", e)
            }
        }
    }
}
