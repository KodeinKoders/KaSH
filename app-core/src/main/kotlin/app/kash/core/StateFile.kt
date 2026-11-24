package app.kash.core

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(StateFile::class.java)

/** The file that [KashState] is kept in, as JSON. */
public class StateFile(private val path: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        // A value that this version does not know (an enum's, from another branch or a later version) is the setting's default, not a file
        // that cannot be read.
        coerceInputValues = true
    }

    /**
     * What the file holds, or an empty state when there is no file or it cannot be read. One that cannot be read is set aside ([setAside], which
     * is logged) before KaSH saves anything over it: the services, the settings and the profiles that they name are still in it.
     */
    @Synchronized
    public fun load(): KashState {
        if (!Files.exists(path)) return KashState()
        return try {
            json.decodeFromString(KashState.serializer(), Files.readString(path))
        } catch (e: Exception) {
            val aside = setAside(path)
            logger.error("Cannot read {}, starting with no service: it is kept as {}", path, aside.fileName, e)
            KashState()
        }
    }

    /** Writes [state] durably ([writeDurably]): a crash while writing leaves the previous state. */
    @Synchronized
    public fun save(state: KashState) {
        writeDurably(path, json.encodeToString(KashState.serializer(), state))
    }
}
