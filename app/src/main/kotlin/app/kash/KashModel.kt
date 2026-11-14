package app.kash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kash.browser.BrowserEngine
import app.kash.browser.BrowserProfile
import app.kash.browser.BrowserTab
import app.kash.browser.ColorScheme
import app.kash.core.Conversation
import app.kash.core.ConversationKind
import app.kash.core.KashDirectories
import app.kash.core.KashState
import app.kash.core.ProfileId
import app.kash.core.Selection
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.ServiceKind
import app.kash.core.ServiceUnread
import app.kash.core.Settings
import app.kash.core.StateFile
import app.kash.core.UnreadCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(KashModel::class.java)

/** A service whose page is loaded: its profile, its tab, and what watches them. */
class LoadedService(val profile: BrowserProfile, val tab: BrowserTab, val jobs: List<Job>)

/** What the user asked to confirm before it is done. */
sealed interface Confirmation {
    val service: Service

    data class Delete(override val service: Service) : Confirmation
    data class Erase(override val service: Service) : Confirmation
}

/**
 * What the windows show: the services and the settings ([state], saved as they change), the page of each enabled service, and what is unread in
 * each.
 *
 * Used on Swing's thread only: the state is Compose's. What waits for Chromium (closing a profile, which writes its cookies) runs off it.
 */
class KashModel(
    initial: KashState,
    private val file: StateFile,
    private val directories: KashDirectories,
    private val engine: BrowserEngine,
    /** The scripts that the pages of a kind of service are given: the notifications' for all, and what reads each known service. */
    private val scriptsFor: (ServiceKind) -> List<String>,
    /** KaSH's theme when it starts, which the pages see as the system's (see [colorScheme]). */
    initialColorScheme: ColorScheme,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var state: KashState by mutableStateOf(
        initial.copy(selection = if (initial.settings.openInboxOnLaunch) Selection.Inbox else initial.selection),
    )
        private set

    val services: List<Service> get() = state.services
    val settings: Settings get() = state.settings

    /**
     * KaSH's theme, which every page sees as the system's color scheme (`prefers-color-scheme`): a service whose own theme follows the system
     * (WhatsApp by default) has KaSH's.
     */
    var colorScheme: ColorScheme = initialColorScheme
        set(value) {
            if (field == value) return
            field = value
            loaded.values.forEach { it.tab.setColorScheme(value) }
        }

    /** The page of each enabled service: all are loaded at launch, so that they notify and count before they are shown. */
    val loaded = mutableStateMapOf<ServiceId, LoadedService>()

    /** The title of each service's page: what a custom application's count is read from. */
    private val titles = mutableStateMapOf<ServiceId, String>()

    /** The unread conversations of each instrumented service, as its page last sent them. */
    private val conversations = mutableStateMapOf<ServiceId, List<Conversation>>()

    /** The services whose cache was just reset, which Settings › Services says until the window is closed. */
    val cacheReset = mutableStateMapOf<ServiceId, Boolean>()

    var addServiceOpen by mutableStateOf(false)
    var editing: ServiceId? by mutableStateOf(null)
    var settingsOpen by mutableStateOf(false)
    var confirmation: Confirmation? by mutableStateOf(null)

    /** Brings the main window to the front: set by the window. */
    var activateWindow: () -> Unit = {}

    /** Called with each notification that a page shows (`notification-shim.js`): set by the [Notifier]. */
    var onPageNotification: (Service, JsonObject) -> Unit = { _, _ -> }

    init {
        services.filter { it.enabled }.forEach(::load)
    }

    fun service(id: ServiceId): Service? = services.firstOrNull { it.id == id }

    /** What is unread in each service: a known service's conversations as its page sends them (`services/`), a custom application's total in its title. */
    val unread: Map<ServiceId, ServiceUnread>
        get() = services.associate { service ->
            service.id to when (service.kind) {
                ServiceKind.CUSTOM -> ServiceUnread(total = titles[service.id]?.let(UnreadCount::fromTitle))
                ServiceKind.WHATSAPP, ServiceKind.GOOGLE_CHAT -> ServiceUnread(conversations = conversations[service.id].orEmpty())
            }
        }

    private fun load(service: Service) {
        if (service.id in loaded) return
        val profile = engine.profile(directories.profile(service.profile))
        val tab = profile.openTab(service.url, scriptsFor(service.kind))
        tab.setColorScheme(colorScheme)
        val jobs = listOf(
            scope.launch { tab.title.collect { titles[service.id] = it } },
            scope.launch { tab.messages.collect { message -> received(service.id, message) } },
        )
        loaded[service.id] = LoadedService(profile, tab, jobs)
    }

    /** A message of a page: a notification, or its unread conversations. */
    private fun received(id: ServiceId, message: String) {
        val service = service(id) ?: return
        val json = try {
            Json.parseToJsonElement(message).jsonObject
        } catch (e: Exception) {
            logger.warn("A message of {} is not JSON", service.title)
            return
        }
        when (json["type"]?.jsonPrimitive?.contentOrNull) {
            "notification" -> onPageNotification(service, json)
            "unread" -> conversations[id] = try {
                // Only counts in the log: the names are the user's contacts.
                json.getValue("conversations").jsonArray.map { element ->
                    val conversation = element.jsonObject
                    Conversation(
                        id = conversation.getValue("id").jsonPrimitive.content,
                        name = conversation["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        kind = when (conversation["kind"]?.jsonPrimitive?.contentOrNull) {
                            "group" -> ConversationKind.GROUP
                            "channel" -> ConversationKind.CHANNEL
                            else -> ConversationKind.DIRECT
                        },
                        count = conversation["count"]?.jsonPrimitive?.intOrNull ?: 1,
                        mention = conversation["mention"]?.jsonPrimitive?.booleanOrNull ?: false,
                        preview = conversation["preview"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        time = conversation["time"]?.jsonPrimitive?.longOrNull,
                    )
                }.also { logger.debug("Unread of {}: {} conversations, {} messages", service.title, it.size, it.sumOf { c -> c.count }) }
            } catch (e: Exception) {
                logger.warn("The unread conversations of {} cannot be read", service.title, e)
                return
            }
        }
    }

    /** Closes the page of [id], off Swing's thread (its profile writes its cookies first), then runs [then] on Swing's thread. */
    private fun unload(id: ServiceId, then: () -> Unit = {}) {
        val loadedService = loaded.remove(id)
        titles.remove(id)
        conversations.remove(id)
        if (loadedService == null) {
            then()
            return
        }
        loadedService.jobs.forEach { it.cancel() }
        scope.launch {
            withContext(Dispatchers.IO) { loadedService.profile.close() }
            then()
        }
    }

    private fun update(change: (KashState) -> KashState) {
        state = change(state)
        save()
    }

    /** Writes the state to its file. */
    fun save() {
        try {
            file.save(state)
        } catch (e: Exception) {
            logger.error("Cannot save the state", e)
        }
    }

    fun select(selection: Selection) = update { it.copy(selection = selection) }

    fun updateSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    /** Adds [service], loads it and selects it. */
    fun add(service: Service) {
        update { it.copy(services = it.services + service, selection = Selection.OfService(service.id)) }
        if (service.enabled) load(service)
    }

    /**
     * Replaces the service of the same id by [service]: its page is opened again when its address changed, and closed or opened when it was disabled
     * or enabled.
     */
    fun replace(service: Service) {
        val previous = service(service.id) ?: return
        update { state -> state.copy(services = state.services.map { if (it.id == service.id) service else it }) }
        when {
            !service.enabled -> {
                unload(service.id)
                if (state.selection == Selection.OfService(service.id)) select(Selection.Inbox)
            }
            !previous.enabled || previous.url != service.url -> unload(service.id) { service(service.id)?.takeIf { it.enabled }?.let(::load) }
        }
    }

    /** The services in [order], with what Settings › Services changed in them (enabled or not). */
    fun applyServices(order: List<Service>) {
        val byId = order.associateBy { it.id }
        val changed = services.filter { byId[it.id] != null && byId[it.id] != it }
        update { state -> state.copy(services = order.filter { service -> state.services.any { it.id == service.id } }) }
        changed.forEach { previous -> byId[previous.id]?.let { replaceLoaded(previous, it) } }
    }

    private fun replaceLoaded(previous: Service, service: Service) {
        if (previous.enabled && !service.enabled) {
            unload(service.id)
            if (state.selection == Selection.OfService(service.id)) select(Selection.Inbox)
        } else if (!previous.enabled && service.enabled) {
            load(service)
        }
    }

    fun toggleMute(id: ServiceId) {
        val service = service(id) ?: return
        update { state -> state.copy(services = state.services.map { if (it.id == id) service.copy(muted = !service.muted) else it }) }
    }

    fun toggleDoNotDisturb() = updateSettings { it.copy(doNotDisturb = !it.doNotDisturb) }

    /** Empties the HTTP cache of [id]: it stays logged in. */
    fun resetCache(id: ServiceId) {
        val loadedService = loaded[id]
        if (loadedService == null) {
            cacheReset[id] = true
            return
        }
        loadedService.profile.clearHttpCache { scope.launch { cacheReset[id] = true } }
    }

    /**
     * Erases every data of the service: its page is closed, it is given a new, empty profile, and the old one is deleted at the next launch (Chromium
     * may still hold its files until then).
     */
    fun eraseData(id: ServiceId) {
        val service = service(id) ?: return
        val renewed = service.copy(profile = ProfileId.random())
        update { state ->
            state.copy(
                services = state.services.map { if (it.id == id) renewed else it },
                deletedProfiles = state.deletedProfiles + service.profile,
            )
        }
        unload(id) { service(id)?.takeIf { it.enabled }?.let(::load) }
    }

    /** Deletes the service: its page is closed now, its profile deleted at the next launch. */
    fun delete(id: ServiceId) {
        val service = service(id) ?: return
        unload(id)
        update { state ->
            state.copy(
                services = state.services.filter { it.id != id },
                selection = if (state.selection == Selection.OfService(id)) Selection.Inbox else state.selection,
                deletedProfiles = state.deletedProfiles + service.profile,
            )
        }
    }

    /** Selects [service] and brings the window to the front: what clicking a notification or a row of the Unread tab does. */
    fun show(service: Service) {
        select(Selection.OfService(service.id))
        activateWindow()
    }

    /** Shows [service], and opens [conversation] in its page when there is one (a row of the Unread tab). */
    fun openConversation(service: Service, conversation: String?) {
        show(service)
        if (conversation == null) return
        val quoted = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(conversation))
        loaded[service.id]?.tab?.execute("window.__kashOpenConversation && window.__kashOpenConversation($quoted)")
    }

    fun close() {
        scope.cancel()
    }
}
