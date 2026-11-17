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
import app.kash.core.PageLanguages
import app.kash.core.InstalledPlugin
import app.kash.core.Plugin
import app.kash.core.PluginIcon
import app.kash.core.PluginManifest
import app.kash.core.PluginPackage
import app.kash.core.PluginPackageException
import app.kash.core.PluginRegistry
import app.kash.core.PluginSource
import app.kash.core.PluginUpdates
import app.kash.core.ProfileId
import app.kash.core.SeenService
import app.kash.core.Selection
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.ServiceOrder
import app.kash.core.ServiceUnread
import app.kash.core.Settings
import app.kash.core.StateFile
import app.kash.core.UnreadCount
import app.kash.core.UnreadTimes
import app.kash.core.UnreadTimesFile
import app.kash.ui.stringsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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

/** An update of the plugin named [name] that failed, and why. */
data class PluginUpdateFailure(val name: String, val message: String)

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
    /** The plugins that KaSH has in its resources, which come first in the catalog. */
    private val builtInPlugins: List<Plugin>,
    /** The plugins that the user installs, updates and uninstalls (Settings › Plugins). */
    private val installer: PluginInstaller,
    /** The installed plugins that could be loaded at launch ([PluginInstaller.plugins]). */
    installedPlugins: List<Plugin>,
    /** The scripts that the pages of a service are given, with its plugin (null for a web application) in a language (`fr`, `en`). */
    private val scriptsFor: (Service, Plugin?, String) -> List<String>,
    /** KaSH's theme when it starts, which the pages see as the system's (see [colorScheme]). */
    initialColorScheme: ColorScheme,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The plugins that services may be of (their names, their addresses and their scripts): the built-in ones, then those that the user installed.
     * They change when a plugin is installed, updated or uninstalled.
     */
    var plugins: PluginRegistry by mutableStateOf(PluginRegistry(builtInPlugins + installedPlugins))
        private set

    /** The plugins that the user installed, where they come from, and whether they are watched. */
    var installedPlugins: List<InstalledPlugin> by mutableStateOf(installer.installed)
        private set

    /** The new versions that the watched plugins' sources have, by plugin: what Settings › Plugins offers to install. */
    val pluginUpdates = mutableStateMapOf<String, PluginManifest>()

    /** The plugins whose new version is being installed from the rail or the title bar ([startPluginUpdate]). */
    var updatingPlugins: Set<String> by mutableStateOf(emptySet())
        private set

    /** Why the last update that [startPluginUpdate] started failed, until the user dismisses it. */
    var pluginUpdateFailure: PluginUpdateFailure? by mutableStateOf(null)

    // The errors of the watched sources that were logged: each one once.
    private val watchErrors = HashSet<String>()

    var state: KashState by mutableStateOf(
        initial.copy(
            // A plugin's service takes its name and its address from its plugin, which may have changed since it was saved.
            services = initial.services.map { plugins.refresh(it, languageOf(initial.settings)) },
            selection = if (initial.settings.openInboxOnLaunch) Selection.Inbox else initial.selection,
        ),
    )
        private set

    /** The language of the interface (`fr`, `en`): the one of the plugins' names, and the one their scripts are given. */
    val language: String get() = languageOf(settings)

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

    /** The unread conversations of each plugin's service, as its script last sent them (`kash.unread.set`). */
    private val conversations = mutableStateMapOf<ServiceId, List<Conversation>>()

    /** The unread total of each plugin's service whose script only tells that (`kash.unread.setCount`). */
    private val totals = mutableStateMapOf<ServiceId, Int>()

    // When KaSH saw the unread conversations whose service does not tell their time (UnreadTimes), kept across launches; the services that no
    // longer exist are forgotten.
    private val unreadTimesFile = UnreadTimesFile(directories.unreadTimesFile)
    private val seen = unreadTimesFile.load().filterKeys { id -> initial.services.any { it.id == id } }.toMutableMap()

    // When each service's page was loaded, and when KaSH had last watched the service before: what UnreadTimes.update needs.
    private val pageLoads = HashMap<ServiceId, Pair<Long, Long?>>()
    private var unreadTimesSave: Job? = null

    /** The services whose cache was just reset, which Settings › Services says until the window is closed. */
    val cacheReset = mutableStateMapOf<ServiceId, Boolean>()

    var addServiceOpen by mutableStateOf(false)
    var editing: ServiceId? by mutableStateOf(null)
    var settingsOpen by mutableStateOf(false)

    /** Whether the About window is open: from the About item of the application menu, or Settings' button. */
    var aboutOpen by mutableStateOf(false)

    /** The page that Settings shows when it opens, or goes to while it is open (a notification of a plugin's update); null once it did. */
    var settingsPage: SettingsPage? by mutableStateOf(null)
    var confirmation: Confirmation? by mutableStateOf(null)

    /** Brings the main window to the front: set by the window. */
    var activateWindow: () -> Unit = {}

    /** Called with each notification that a page shows (`notification-shim.js`): set by the [Notifier]. */
    var onPageNotification: (Service, JsonObject) -> Unit = { _, _ -> }

    /** Called with the new version of a watched plugin, once per version: set by the [Notifier]. */
    var onPluginUpdate: (PluginManifest) -> Unit = {}

    /** Called with the id of a plugin that was installed, updated or uninstalled: set by [ServiceIcons], which forgets its icon. */
    var onPluginChanged: (String) -> Unit = {}

    /** Called with the icon that a plugin's script gives its service (`kash.setIcon`), and with [PluginIcon.None] when it is deleted or erased. */
    var onIcon: (ServiceId, PluginIcon) -> Unit = { _, _ -> }

    init {
        // Before the pages load: a site that follows the browser's language shows itself in KaSH's.
        engine.setPageLanguages(PageLanguages.acceptLanguages(language))
        services.filter { it.enabled }.forEach(::load)
        watchPlugins()
    }

    fun service(id: ServiceId): Service? = services.firstOrNull { it.id == id }

    /** What is unread in each service: a plugin's service's conversations or total, as its script sends them, a web application's total in its title. */
    val unread: Map<ServiceId, ServiceUnread>
        get() = services.associate { service ->
            service.id to if (service.plugin == null) {
                ServiceUnread(total = titles[service.id]?.let(UnreadCount::fromTitle))
            } else {
                ServiceUnread(total = totals[service.id], conversations = conversations[service.id].orEmpty())
            }
        }

    private fun load(service: Service) {
        if (service.id in loaded) return
        val profile = engine.profile(directories.profile(service.profile))
        val tab = profile.openTab(service.url, scriptsFor(service, plugins[service.plugin], language))
        tab.setColorScheme(colorScheme)
        val jobs = listOf(
            scope.launch { tab.title.collect { titles[service.id] = it } },
            scope.launch { tab.messages.collect { message -> received(service.id, message) } },
        )
        loaded[service.id] = LoadedService(profile, tab, jobs)
        pageLoads[service.id] = System.currentTimeMillis() to seen[service.id]?.until
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
            "icon" -> {
                // Only a plugin's script has the API: a web application's page has nothing to say about its tile.
                if (service.plugin == null) return
                val source = json["source"]?.jsonPrimitive?.contentOrNull
                val icon = PluginIcon.parse(source) ?: return logger.warn("The icon that the plugin of {} gave is not one", service.title)
                logger.debug("Icon of {}: {}", service.title, when (icon) { is PluginIcon.Remote -> icon.uri.host; is PluginIcon.Inline -> "inline"; PluginIcon.None -> "none" })
                onIcon(id, icon)
            }
            "unreadCount" -> {
                val count = json["count"]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 } ?: return logger.warn("The unread total of {} cannot be read", service.title)
                conversations.remove(id)
                totals[id] = count
            }
            "unread" -> conversations[id] = try {
                // Only counts in the log: the names are the user's contacts.
                json.getValue("conversations").jsonArray.map { element ->
                    val conversation = element.jsonObject
                    val topic = conversation["topic"]?.jsonPrimitive?.booleanOrNull ?: false
                    Conversation(
                        id = conversation.getValue("id").jsonPrimitive.content,
                        name = conversation["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        kind = when (conversation["kind"]?.jsonPrimitive?.contentOrNull) {
                            "group" -> ConversationKind.GROUP
                            "channel" -> ConversationKind.CHANNEL
                            else -> ConversationKind.DIRECT
                        },
                        // A topic's messages are not counted.
                        count = if (topic) 0 else conversation["count"]?.jsonPrimitive?.intOrNull ?: 1,
                        mention = !topic && conversation["mention"]?.jsonPrimitive?.booleanOrNull ?: false,
                        preview = conversation["preview"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        time = conversation["time"]?.jsonPrimitive?.longOrNull,
                        dayOnly = conversation["timePrecision"]?.jsonPrimitive?.contentOrNull == "day",
                        topic = topic,
                    )
                }.let { read -> withSeenTimes(id, read) }.also { logger.debug("Unread of {}: {} conversations, {} messages, {} topics", service.title, it.count { c -> !c.topic }, it.sumOf { c -> c.count }, it.count { c -> c.topic }); totals.remove(id) }
            } catch (e: Exception) {
                logger.warn("The unread conversations of {} cannot be read", service.title, e)
                return
            }
        }
    }

    // The conversations [read] of the service [id] with the time when KaSH saw those whose service does not tell it (UnreadTimes), which is saved.
    private fun withSeenTimes(id: ServiceId, read: List<Conversation>): List<Conversation> {
        val (loadedAt, watchedUntil) = pageLoads[id] ?: return read
        val updated = UnreadTimes.update(seen[id] ?: SeenService(), read, System.currentTimeMillis(), loadedAt, watchedUntil)
        seen[id] = updated
        saveUnreadTimesLater()
        return UnreadTimes.withTimes(read, updated)
    }

    // Writes the unread conversations' times a few seconds after they last changed, off Swing's thread; save() writes them at once.
    private fun saveUnreadTimesLater() {
        unreadTimesSave?.cancel()
        val snapshot = seen.toMap()
        unreadTimesSave = scope.launch {
            delay(UNREAD_TIMES_SAVE_MS)
            withContext(Dispatchers.IO) { saveUnreadTimes(snapshot) }
        }
    }

    private fun saveUnreadTimes(services: Map<ServiceId, SeenService>) {
        try {
            unreadTimesFile.save(services)
        } catch (e: Exception) {
            logger.error("Cannot save the unread conversations' times", e)
        }
    }

    // KaSH stops watching the service [id] now (its page is closed, KaSH quits): what comes after came while it did not.
    private fun stopWatching(id: ServiceId) {
        seen[id]?.let { seen[id] = it.copy(until = System.currentTimeMillis()) }
        pageLoads.remove(id)
    }

    /** Closes the page of [id], off Swing's thread (its profile writes its cookies first), then runs [then] on Swing's thread. */
    private fun unload(id: ServiceId, then: () -> Unit = {}) {
        if (id in loaded) {
            stopWatching(id)
            saveUnreadTimesLater()
        }
        val loadedService = loaded.remove(id)
        titles.remove(id)
        conversations.remove(id)
        totals.remove(id)
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

    /** Writes the state to its file, and when KaSH saw the unread conversations (until now: KaSH is quitting, or may be). */
    fun save() {
        try {
            file.save(state)
        } catch (e: Exception) {
            logger.error("Cannot save the state", e)
        }
        unreadTimesSave?.cancel()
        val now = System.currentTimeMillis()
        saveUnreadTimes(seen.mapValues { (id, service) -> if (id in loaded) service.copy(until = now) else service })
    }

    fun select(selection: Selection) = update { it.copy(selection = selection) }

    fun updateSettings(change: (Settings) -> Settings) = update { state ->
        val settings = change(state.settings)
        // The names of the plugins' services are in the language of the interface.
        if (languageOf(settings) == languageOf(state.settings)) state.copy(settings = settings)
        // So are the pages, from their next load.
        else state.copy(settings = settings, services = state.services.map { plugins.refresh(it, languageOf(settings)) })
            .also { engine.setPageLanguages(PageLanguages.acceptLanguages(languageOf(settings))) }
    }

    /** Adds [service] (its name and address taken from its plugin), loads it and selects it. */
    fun add(added: Service) {
        val service = plugins.refresh(added, language)
        update { it.copy(services = it.services + service, selection = Selection.OfService(service.id)) }
        if (service.enabled) load(service)
    }

    /**
     * Replaces the service of the same id by [service] (its name and address taken from its plugin): its page is opened again when its address or
     * its options changed (its script is given them), and closed or opened when it was disabled or enabled.
     */
    fun replace(replaced: Service) {
        val service = plugins.refresh(replaced, language)
        val previous = service(service.id) ?: return
        update { state -> state.copy(services = state.services.map { if (it.id == service.id) service else it }) }
        when {
            !service.enabled -> {
                unload(service.id)
                if (state.selection == Selection.OfService(service.id)) select(Selection.Inbox)
            }
            !previous.enabled || previous.url != service.url || previous.options != service.options -> unload(service.id) { service(service.id)?.takeIf { it.enabled }?.let(::load) }
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

    /** Moves [id] past [neighbor] in the services' order (a tile dragged in the rail): see [ServiceOrder.moveNextTo]. */
    fun moveNextTo(id: ServiceId, neighbor: ServiceId) = update { state ->
        val byId = state.services.associateBy { it.id }
        state.copy(services = ServiceOrder.moveNextTo(state.services.map { it.id }, id, neighbor).map(byId::getValue))
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
        onIcon(id, PluginIcon.None)
        unload(id) { service(id)?.takeIf { it.enabled }?.let(::load) }
        // Its conversations are the ones of an account that is not logged in any more.
        seen.remove(id)
        saveUnreadTimesLater()
    }

    /** Deletes the service: its page is closed now, its profile deleted at the next launch. */
    fun delete(id: ServiceId) {
        val service = service(id) ?: return
        unload(id)
        seen.remove(id)
        saveUnreadTimesLater()
        onIcon(id, PluginIcon.None)
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

    /** Opens Settings on [page]. */
    fun openSettings(page: SettingsPage) {
        settingsPage = page
        settingsOpen = true
        activateWindow()
    }

    /** The plugin at [source], read whole off Swing's thread: what the confirmation of its installation shows. */
    suspend fun readPlugin(source: PluginSource): PluginPackage = withContext(Dispatchers.IO) { installer.read(source) }

    /** Installs [plugin] from [source] (or updates it: the same id): the pages of its services are loaded again, with its new script. */
    suspend fun installPlugin(plugin: PluginPackage, source: PluginSource, watch: Boolean) {
        withContext(Dispatchers.IO) { installer.install(plugin, source, watch) }
        pluginUpdates.remove(plugin.id)
        pluginsChanged(plugin.id)
    }

    /** Installs the plugin [id] again from its source: its new version. */
    suspend fun updatePlugin(id: String) {
        val entry = installedPlugins.firstOrNull { it.id == id } ?: return
        val plugin = withContext(Dispatchers.IO) { installer.read(entry.source) }
        if (plugin.id != id) throw PluginPackageException("Its source now has the plugin '${plugin.id}'")
        installPlugin(plugin, entry.source, entry.watch)
    }

    /**
     * Installs the new version of the plugin [id] (see [pluginUpdates]) without waiting for it: what the rail and the title bar do. Its failure is
     * [pluginUpdateFailure], and its new version stays offered.
     */
    fun startPluginUpdate(id: String) {
        if (id in updatingPlugins || id !in pluginUpdates) return
        val name = plugins[id]?.manifest?.name?.of(language) ?: id
        updatingPlugins = updatingPlugins + id
        scope.launch {
            try {
                updatePlugin(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is PluginPackageException) logger.warn("The plugin {} could not be updated", id, e)
                pluginUpdateFailure = PluginUpdateFailure(name, e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            } finally {
                updatingPlugins = updatingPlugins - id
            }
        }
    }

    /** Uninstalls the plugin [id]: its services stay, without its script (a service whose plugin KaSH does not have). */
    suspend fun uninstallPlugin(id: String) {
        withContext(Dispatchers.IO) { installer.uninstall(id) }
        pluginUpdates.remove(id)
        pluginsChanged(id)
    }

    /** Whether KaSH watches the plugin [id]'s source for a new version. */
    fun setPluginWatch(id: String, watch: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { installer.setWatch(id, watch) }
            installedPlugins = installer.installed
            if (!watch) pluginUpdates.remove(id)
        }
    }

    // The plugin [id] changed: the plugins are read again, its services take their names and addresses from it, and their pages are loaded
    // again, with its script (or none).
    private suspend fun pluginsChanged(id: String) {
        val installed = withContext(Dispatchers.IO) { installer.plugins() }
        installedPlugins = installer.installed
        plugins = PluginRegistry(builtInPlugins + installed)
        onPluginChanged(id)
        update { state -> state.copy(services = state.services.map { plugins.refresh(it, language) }) }
        services.filter { it.plugin == id && it.enabled }.forEach { service ->
            unload(service.id) { service(service.id)?.takeIf { it.enabled }?.let(::load) }
        }
    }

    // The watched plugins' sources: a folder every 10 s, an address every hour (and at launch). A new version is offered once.
    private fun watchPlugins() {
        scope.launch {
            while (true) {
                delay(FOLDER_WATCH_MS)
                checkPluginUpdates { it is PluginSource.Folder }
            }
        }
        scope.launch {
            while (true) {
                checkPluginUpdates { it is PluginSource.Url }
                delay(ADDRESS_WATCH_MS)
            }
        }
    }

    private suspend fun checkPluginUpdates(of: (PluginSource) -> Boolean) {
        for (entry in installedPlugins.filter { it.watch && of(it.source) }) {
            val manifest = try {
                withContext(Dispatchers.IO) { installer.readManifest(entry.source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A folder being written, an address that does not answer: the next check may read it.
                if (watchErrors.add("${entry.id}|${e.message}")) logger.warn("The source of the plugin {} cannot be read: {}", entry.id, e.message)
                continue
            }
            // Read again: it may have been uninstalled, updated or no longer watched meanwhile.
            val current = installedPlugins.firstOrNull { it.id == entry.id && it.watch } ?: continue
            if (PluginUpdates.isNewer(current, manifest) && pluginUpdates[entry.id]?.versionCode != manifest.versionCode) {
                pluginUpdates[entry.id] = manifest
                logger.info("Plugin {}: version {} ({}) available", entry.id, manifest.version, manifest.versionCode)
                onPluginUpdate(manifest)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val FOLDER_WATCH_MS = 10_000L
        const val ADDRESS_WATCH_MS = 60 * 60 * 1000L
        const val UNREAD_TIMES_SAVE_MS = 5_000L
    }
}

/** The language of the interface that [settings] give (`fr`, `en`). */
private fun languageOf(settings: Settings): String = stringsOf(settings.language).code
