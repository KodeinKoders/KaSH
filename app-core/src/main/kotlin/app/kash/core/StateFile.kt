package app.kash.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger(StateFile::class.java)

/** The file that [KashState] is kept in, as JSON. */
public class StateFile(private val path: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** What the file holds, or an empty state when there is no file or it cannot be read (which is logged). */
    public fun load(): KashState {
        if (!Files.exists(path)) return KashState()
        return try {
            val element = json.parseToJsonElement(Files.readString(path)).jsonObject
            if ("environments" in element) fromGenericBrowser(element) else json.decodeFromJsonElement(KashState.serializer(), element)
        } catch (e: Exception) {
            logger.error("Cannot read {}, starting with no service", path, e)
            KashState()
        }
    }

    /**
     * The state of the first version of KaSH, a browser of generic environments: they are not services, so their profiles are deleted (the user
     * asked for it) and KaSH starts with none.
     */
    private fun fromGenericBrowser(element: JsonObject): KashState {
        val ids = element.getValue("environments").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content } +
            (element["deleted"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
        logger.info("{} is from the generic browser: its {} environments are deleted", path, ids.size)
        return KashState(deletedProfiles = ids.mapNotNull { runCatching { ProfileId(it) }.getOrNull() })
    }

    /** Writes [state] to a temporary file next to the file, then moves it in place: a crash while writing leaves the previous state. */
    public fun save(state: KashState) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(KashState.serializer(), state))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
