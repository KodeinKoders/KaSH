package app.kash.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Settings' draft (the settings that Settings shows, changed there until Apply) follows what changes elsewhere while Settings is open (the rail's
 * Do Not Disturb, the title bar's theme, the Unread tab's sort): Apply writes what was changed in Settings, and nothing else.
 */
public object SettingsDraft {
    private val json = Json { encodeDefaults = true }

    /**
     * The [draft] made from [base], on [current], the settings as they are now: a setting that the draft did not change is [current]'s, one that it
     * changed is the draft's (also when it changed elsewhere too: the user's last word is Settings'). Setting by setting, as the JSON of
     * [Settings] has them, so that a new setting is followed without a line here.
     */
    public fun rebase(draft: Settings, base: Settings, current: Settings): Settings {
        if (draft == base) return current
        val drafted = json.encodeToJsonElement(Settings.serializer(), draft).jsonObject
        val based = json.encodeToJsonElement(Settings.serializer(), base).jsonObject
        val merged = json.encodeToJsonElement(Settings.serializer(), current).jsonObject.toMutableMap()
        for ((name, value) in drafted) if (value != based[name]) merged[name] = value
        return json.decodeFromJsonElement(Settings.serializer(), JsonObject(merged))
    }
}
