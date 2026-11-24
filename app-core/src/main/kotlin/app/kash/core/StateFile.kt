package app.kash.core

import java.nio.file.Path

/** The file that [KashState] is kept in, as JSON. */
public class StateFile(path: Path) {
    // No version in it yet.
    private val file = JsonFile(path, KashState.serializer(), ::KashState, "starting with no service", version = null)

    /**
     * What the file holds, or an empty state when there is no file or it cannot be read. One that cannot be read is set aside ([JsonFile]) before
     * KaSH saves anything over it: the services, the settings and the profiles that they name are still in it.
     */
    public fun load(): KashState = file.load()

    /** Writes [state] durably: a crash while writing leaves the previous state. */
    public fun save(state: KashState) {
        file.save(state)
    }
}
