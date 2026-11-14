package app.kash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.kash.core.Language
import app.kash.core.TimeLabel
import java.util.Locale

/** Every text of the interface, in one language. */
@Suppress("PropertyName")
interface Strings {
    // Main window
    val alreadyRunning: String

    /** How the Unread tab says when a conversation's last message came. */
    val timeLabel: TimeLabel
    val inbox: String
    fun inboxSummary(messages: Int, conversations: Int): String
    val refresh: String
    val filterPlaceholder: String
    val filterLabel: String
    val byDate: String
    val byCount: String
    val byService: String
    fun open(name: String): String
    fun noResults(query: String): String
    val emptyTitle: String
    val emptyHint: String
    fun unread(count: Int): String
    fun inboxTileLabel(count: Int): String
    fun serviceTileLabel(title: String, count: Int): String
    val unreadMessages: String
    val directConversation: String
    val groupConversation: String
    val channelConversation: String
    val unnamedGroup: String
    val unnamedConversation: String
    val doNotDisturb: String
    val theme: String
    val light: String
    val dark: String
    val system: String
    fun themeButton(mode: String): String
    val addService: String
    val settings: String
    val back: String
    val forward: String
    val reload: String
    val muteService: String
    val unmuteService: String
    val moreOptions: String
    val devTools: String
    val edit: String
    val resetCache: String
    val eraseData: String
    val delete: String
    val cacheReset: String
    val services: String

    // Confirmations
    fun deleteTitle(title: String): String
    val deleteBody: String
    fun eraseTitle(title: String): String
    val eraseBody: String
    val erase: String
    val cancel: String

    // Add a service
    val editService: String
    val service: String
    val custom: String
    val customApplication: String
    val name: String
    val namePlaceholder: String
    val url: String
    val urlPlaceholder: String
    val urlInvalid: String
    val nameRequired: String
    val labelOptional: String
    val labelPlaceholder: String
    val labelHint: String
    val showBadge: String
    val customBadgeHint: String
    fun configOf(name: String): String
    val includeInInbox: String
    val desktopNotifications: String
    val tabIcon: String
    val iconFavicon: String
    val iconImage: String
    val chooseFile: String
    val iconSquare: String
    val color: String
    val hexColor: String
    val hexError: String
    val squareText: String
    val preview: String
    val add: String
    val save: String

    // Settings
    val search: String
    val searchSetting: String
    val general: String
    val appearance: String
    val notifications: String
    val inboxTab: String
    val startup: String
    val launchAtLogin: String
    val launchAtLoginUnavailable: String
    val launchAtLoginApproval: String
    val language: String
    val interfaceLanguage: String
    val systemLanguage: String
    val sidebar: String
    val iconSize: String
    val small: String
    val medium: String
    val large: String
    val position: String
    val left: String
    val right: String
    val top: String
    val bottom: String
    val showLabels: String
    val showBadges: String
    val alerts: String
    val playSound: String
    val dockBadge: String
    val dockTotal: String
    val dockDot: String
    val dockNone: String
    val display: String
    val defaultSort: String
    val openInboxOnLaunch: String
    val content: String
    val skipMuted: String
    val directOnly: String
    val directOnlyHint: String
    fun servicesCount(services: Int, enabled: Int): String
    val enabled: String
    fun actionsFor(title: String): String
    val dragToReorder: String
    val apply: String
    val ok: String
    val noService: String
}

object French : Strings {
    override val timeLabel = TimeLabel(Locale.FRENCH, "HH:mm", "'Hier', HH:mm", "d MMM, HH:mm", "d MMM yyyy, HH:mm")
    override val alreadyRunning = "KaSH est déjà ouvert. Quittez-le (⌘Q) avant de le relancer."
    override val inbox = "Non lus"
    override fun inboxSummary(messages: Int, conversations: Int) =
        "$messages message${s(messages)} non lu${s(messages)} dans $conversations conversation${s(conversations)}"
    override val refresh = "Actualiser"
    override val filterPlaceholder = "Filtrer par nom ou par service"
    override val filterLabel = "Filtrer les conversations"
    override val byDate = "Par date"
    override val byCount = "Par nombre"
    override val byService = "Par service"
    override fun open(name: String) = "Ouvrir $name"
    override fun noResults(query: String) = "Aucune conversation non lue ne correspond à « $query »."
    override val emptyTitle = "Aucun message non lu"
    override val emptyHint = "Les conversations non lues de vos services apparaîtront ici."
    override fun unread(count: Int) = "$count non lu${s(count)}"
    override fun inboxTileLabel(count: Int) = if (count > 0) "Non lus, $count message${s(count)}" else "Non lus"
    override fun serviceTileLabel(title: String, count: Int) = if (count > 0) "$title, $count non lu${s(count)}" else title
    override val unreadMessages = "Messages non lus"
    override val directConversation = "Discussion"
    override val groupConversation = "Groupe"
    override val channelConversation = "Chaîne"
    override val unnamedGroup = "Groupe sans nom"
    override val unnamedConversation = "Sans nom"
    override val doNotDisturb = "Ne pas déranger"
    override val theme = "Thème"
    override val light = "Clair"
    override val dark = "Sombre"
    override val system = "Système"
    override fun themeButton(mode: String) = "Thème : $mode"
    override val addService = "Ajouter un service"
    override val settings = "Paramètres"
    override val back = "Précédent"
    override val forward = "Suivant"
    override val reload = "Recharger"
    override val muteService = "Couper les notifications de ce service"
    override val unmuteService = "Réactiver les notifications de ce service"
    override val moreOptions = "Plus d'options"
    override val devTools = "Outils de développement"
    override val edit = "Modifier…"
    override val resetCache = "Réinitialiser le cache"
    override val eraseData = "Effacer toutes les données…"
    override val delete = "Supprimer…"
    override val cacheReset = "Cache réinitialisé"
    override val services = "Services"

    override fun deleteTitle(title: String) = "Supprimer $title ?"
    override val deleteBody = "Ses cookies, son cache, ses bases IndexedDB et son stockage local sont supprimés avec lui."
    override fun eraseTitle(title: String) = "Effacer les données de $title ?"
    override val eraseBody = "Ses cookies, son cache, ses bases IndexedDB et son stockage local sont effacés : il faudra s'y reconnecter."
    override val erase = "Effacer"
    override val cancel = "Annuler"

    override val editService = "Modifier le service"
    override val service = "Service"
    override val custom = "Personnalisée"
    override val customApplication = "Application personnalisée"
    override val name = "Nom"
    override val namePlaceholder = "Mon application"
    override val url = "URL"
    override val urlPlaceholder = "https://exemple.com/app"
    override val urlInvalid = "Une adresse http:// ou https://"
    override val nameRequired = "Un nom est nécessaire"
    override val labelOptional = "Label sous l'icône (optionnel)"
    override val labelPlaceholder = "Perso, Travail…"
    override val labelHint = "Utile pour distinguer plusieurs comptes du même service."
    override val showBadge = "Afficher la pastille de non lus"
    override val customBadgeHint =
        "Le nombre est lu dans le titre de la page, par exemple « (3) Mon application ». Pour une application personnalisée, l'onglet Non lus n'affiche que ce total."
    override fun configOf(name: String) = "Configuration de $name"
    override val includeInInbox = "Inclure les conversations dans l'onglet Non lus"
    override val desktopNotifications = "Notifications du bureau"
    override val tabIcon = "Icône de l'onglet"
    override val iconFavicon = "Favicon du site (automatique)"
    override val iconImage = "Image personnalisée"
    override val chooseFile = "Choisir un fichier…"
    override val iconSquare = "Carré de couleur avec texte"
    override val color = "Couleur"
    override val hexColor = "Couleur hex RGB"
    override val hexError = "3 ou 6 chiffres hexadécimaux"
    override val squareText = "Texte (3 car. max.)"
    override val preview = "Aperçu"
    override val add = "Ajouter"
    override val save = "Enregistrer"

    override val search = "Rechercher"
    override val searchSetting = "Rechercher un paramètre"
    override val general = "Général"
    override val appearance = "Apparence"
    override val notifications = "Notifications"
    override val inboxTab = "Onglet Non lus"
    override val startup = "Démarrage"
    override val launchAtLogin = "Lancer à l'ouverture de la session"
    override val launchAtLoginUnavailable = "Disponible dans l'application installée (KaSH.app)."
    override val launchAtLoginApproval = "À autoriser dans Réglages Système › Général › Ouverture."
    override val language = "Langue"
    override val interfaceLanguage = "Langue de l'interface"
    override val systemLanguage = "Langue du système"
    override val sidebar = "Barre latérale"
    override val iconSize = "Taille des icônes"
    override val small = "Petite"
    override val medium = "Moyenne"
    override val large = "Grande"
    override val position = "Position"
    override val left = "À gauche"
    override val right = "À droite"
    override val top = "En haut"
    override val bottom = "En bas"
    override val showLabels = "Afficher les labels sous les icônes"
    override val showBadges = "Afficher les pastilles de non lus"
    override val alerts = "Alertes"
    override val playSound = "Jouer un son"
    override val dockBadge = "Pastille de l'icône du Dock"
    override val dockTotal = "Nombre total de messages non lus"
    override val dockDot = "Un simple point"
    override val dockNone = "Aucune pastille"
    override val display = "Affichage"
    override val defaultSort = "Tri"
    override val openInboxOnLaunch = "Ouvrir l'onglet Non lus au démarrage"
    override val content = "Contenu"
    override val skipMuted = "Ignorer les services dont les notifications sont coupées"
    override val directOnly = "Uniquement les messages directs et les mentions"
    override val directOnlyHint = "Pour les services dont KaSH lit les conversations, comme WhatsApp."
    override fun servicesCount(services: Int, enabled: Int) = "$services service${s(services)} · $enabled activé${s(enabled)}"
    override val enabled = "Activé"
    override fun actionsFor(title: String) = "Actions pour $title"
    override val dragToReorder = "Glisser pour réordonner"
    override val apply = "Appliquer"
    override val ok = "OK"
    override val noService = "Aucun service. Ajoutez-en un avec le bouton + de la barre latérale."

    private fun s(count: Int) = if (count > 1) "s" else ""
}

object English : Strings {
    override val timeLabel = TimeLabel(Locale.ENGLISH, "h:mm a", "'Yesterday', h:mm a", "MMM d, h:mm a", "MMM d, yyyy, h:mm a")
    override val alreadyRunning = "KaSH is already open. Quit it (⌘Q) before launching it again."
    override val inbox = "Unread"
    override fun inboxSummary(messages: Int, conversations: Int) =
        "$messages unread message${s(messages)} in $conversations conversation${s(conversations)}"
    override val refresh = "Refresh"
    override val filterPlaceholder = "Filter by name or service"
    override val filterLabel = "Filter the conversations"
    override val byDate = "By date"
    override val byCount = "By count"
    override val byService = "By service"
    override fun open(name: String) = "Open $name"
    override fun noResults(query: String) = "No unread conversation matches “$query”."
    override val emptyTitle = "No unread messages"
    override val emptyHint = "The unread conversations of your services will show up here."
    override fun unread(count: Int) = "$count unread"
    override fun inboxTileLabel(count: Int) = if (count > 0) "Unread, $count message${s(count)}" else "Unread"
    override fun serviceTileLabel(title: String, count: Int) = if (count > 0) "$title, $count unread" else title
    override val unreadMessages = "Unread messages"
    override val directConversation = "Chat"
    override val groupConversation = "Group"
    override val channelConversation = "Channel"
    override val unnamedGroup = "Unnamed group"
    override val unnamedConversation = "Unnamed"
    override val doNotDisturb = "Do not disturb"
    override val theme = "Theme"
    override val light = "Light"
    override val dark = "Dark"
    override val system = "System"
    override fun themeButton(mode: String) = "Theme: $mode"
    override val addService = "Add a service"
    override val settings = "Settings"
    override val back = "Back"
    override val forward = "Forward"
    override val reload = "Reload"
    override val muteService = "Mute this service's notifications"
    override val unmuteService = "Unmute this service's notifications"
    override val moreOptions = "More options"
    override val devTools = "Developer tools"
    override val edit = "Edit…"
    override val resetCache = "Reset the cache"
    override val eraseData = "Erase all data…"
    override val delete = "Delete…"
    override val cacheReset = "Cache reset"
    override val services = "Services"

    override fun deleteTitle(title: String) = "Delete $title?"
    override val deleteBody = "Its cookies, cache, IndexedDB databases and local storage are deleted with it."
    override fun eraseTitle(title: String) = "Erase the data of $title?"
    override val eraseBody = "Its cookies, cache, IndexedDB databases and local storage are erased: you will have to log in again."
    override val erase = "Erase"
    override val cancel = "Cancel"

    override val editService = "Edit the service"
    override val service = "Service"
    override val custom = "Custom"
    override val customApplication = "Custom application"
    override val name = "Name"
    override val namePlaceholder = "My application"
    override val url = "URL"
    override val urlPlaceholder = "https://example.com/app"
    override val urlInvalid = "An http:// or https:// address"
    override val nameRequired = "A name is needed"
    override val labelOptional = "Label under the icon (optional)"
    override val labelPlaceholder = "Personal, Work…"
    override val labelHint = "Useful to tell several accounts of the same service apart."
    override val showBadge = "Show the unread badge"
    override val customBadgeHint =
        "The count is read in the page's title, for instance “(3) My application”. For a custom application, the Unread tab only shows that total."
    override fun configOf(name: String) = "$name settings"
    override val includeInInbox = "Include the conversations in the Unread tab"
    override val desktopNotifications = "Desktop notifications"
    override val tabIcon = "Tab icon"
    override val iconFavicon = "Site's favicon (automatic)"
    override val iconImage = "Custom image"
    override val chooseFile = "Choose a file…"
    override val iconSquare = "Colored square with text"
    override val color = "Color"
    override val hexColor = "Hex RGB color"
    override val hexError = "3 or 6 hexadecimal digits"
    override val squareText = "Text (3 chars max.)"
    override val preview = "Preview"
    override val add = "Add"
    override val save = "Save"

    override val search = "Search"
    override val searchSetting = "Search a setting"
    override val general = "General"
    override val appearance = "Appearance"
    override val notifications = "Notifications"
    override val inboxTab = "Unread tab"
    override val startup = "Startup"
    override val launchAtLogin = "Launch at login"
    override val launchAtLoginUnavailable = "Available in the installed application (KaSH.app)."
    override val launchAtLoginApproval = "To allow in System Settings › General › Login Items."
    override val language = "Language"
    override val interfaceLanguage = "Interface language"
    override val systemLanguage = "System language"
    override val sidebar = "Sidebar"
    override val iconSize = "Icon size"
    override val small = "Small"
    override val medium = "Medium"
    override val large = "Large"
    override val position = "Position"
    override val left = "Left"
    override val right = "Right"
    override val top = "Top"
    override val bottom = "Bottom"
    override val showLabels = "Show the labels under the icons"
    override val showBadges = "Show the unread badges"
    override val alerts = "Alerts"
    override val playSound = "Play a sound"
    override val dockBadge = "Dock icon badge"
    override val dockTotal = "Total number of unread messages"
    override val dockDot = "A simple dot"
    override val dockNone = "No badge"
    override val display = "Display"
    override val defaultSort = "Sort"
    override val openInboxOnLaunch = "Open the Unread tab at launch"
    override val content = "Content"
    override val skipMuted = "Ignore the services whose notifications are muted"
    override val directOnly = "Only direct messages and mentions"
    override val directOnlyHint = "For the services whose conversations KaSH reads, such as WhatsApp."
    override fun servicesCount(services: Int, enabled: Int) = "$services service${s(services)} · $enabled enabled"
    override val enabled = "Enabled"
    override fun actionsFor(title: String) = "Actions for $title"
    override val dragToReorder = "Drag to reorder"
    override val apply = "Apply"
    override val ok = "OK"
    override val noService = "No service. Add one with the + button of the sidebar."

    private fun s(count: Int) = if (count > 1) "s" else ""
}

/** The strings of [language]: French when the system is in French, English otherwise. */
fun stringsOf(language: Language): Strings = when (language) {
    Language.FRENCH -> French
    Language.ENGLISH -> English
    Language.SYSTEM -> if (Locale.getDefault().language == "fr") French else English
}

val LocalStrings = staticCompositionLocalOf<Strings> { English }

/** The strings of the current language. */
val strings: Strings
    @Composable get() = LocalStrings.current
