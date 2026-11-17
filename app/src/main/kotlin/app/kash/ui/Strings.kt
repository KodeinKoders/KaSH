package app.kash.ui

import app.kash.macos.NotificationAlertStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.kash.core.Language
import app.kash.core.TimeLabel
import java.util.Locale

/** Every text of the interface, in one language. */
@Suppress("PropertyName")
interface Strings {
    /** The code of the language (`fr`, `en`): the one that plugins' texts are taken in, and that their scripts are given. */
    val code: String
    // Main window
    val alreadyRunning: String

    /** How the Unread tab says when a conversation's last message came. */
    val timeLabel: TimeLabel

    /** What a time of the Unread tab is when KaSH saw the conversation, which came while it was not watching the service, [after] then (null: never). */
    fun cameWhileClosed(after: String?): String
    val inbox: String
    fun inboxSummary(messages: Int, conversations: Int): String
    fun topicsSummary(topics: Int): String
    val messagesSection: String
    val topicsSection: String
    val noUnreadMessages: String
    val noUnreadTopics: String
    val refresh: String
    val filterPlaceholder: String
    val filterLabel: String
    val byDate: String
    val byCount: String
    val byService: String
    fun open(name: String): String
    fun noResults(query: String): String

    // The search
    val searchField: String
    val searchPlaceholder: String
    val searchLabel: String
    val searchContacts: String
    val searchTopics: String
    fun searchNoMatch(query: String): String
    val searchNothingYet: String
    val searchNavigate: String
    val searchOpen: String
    val searchClose: String
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
    val muteNotifications: String
    val unmuteNotifications: String
    val disableService: String
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
    val optionRequired: String
    val optionTooLong: String
    val optionInvalid: String
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
    fun iconOf(name: String): String
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
    val notificationStyle: String
    val kashNotifications: String
    val kashNotificationsHint: String
    val systemNotifications: String
    val systemNotificationsHint: String
    val systemNotificationsUnavailable: String
    val systemNotificationsDenied: String
    val notificationDuration: String
    val temporaryNotifications: String
    val permanentNotifications: String
    fun systemDuration(style: NotificationAlertStyle): String
    val systemDurationHint: String
    val openSystemNotificationSettings: String
    val sendTestNotification: String
    val testNotificationTitle: String
    val testNotificationBody: String
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
    val showApproximateTimes: String
    val showApproximateTimesHint: String
    fun servicesCount(services: Int, enabled: Int): String
    val enabled: String
    fun actionsFor(title: String): String
    val dragToReorder: String
    val apply: String
    val ok: String
    val noService: String
    // Settings › Plugins
    val plugins: String
    val pluginsHint: String
    val installZip: String
    val installFolder: String
    val installAddress: String
    val chooseZip: String
    val chooseFolder: String
    val builtIn: String
    fun sourceZip(name: String): String
    fun sourceFolder(path: String): String
    fun sourceAddress(address: String): String
    fun versionCode(code: Int): String
    val watch: String
    val watchFolderHint: String
    val watchAddressHint: String
    fun updateTo(version: String): String
    val uninstall: String
    fun uninstallTitle(name: String): String
    fun uninstallBody(services: Int): String
    val pluginAddress: String
    val pluginAddressHint: String
    val readPlugin: String
    val readingPlugin: String
    fun installTitle(name: String, version: String): String
    fun replaceTitle(name: String, installed: String, version: String): String
    val pluginPages: String
    val install: String
    val replace: String
    val pluginError: String
    fun pluginUpdateTitle(name: String, version: String): String
    val pluginUpdateBody: String
    val update: String
    val updating: String
    fun updatePlugin(version: String): String
    fun pluginUpdateFailed(name: String): String

    // About
    fun aboutTitle(name: String): String
    fun aboutLogo(name: String): String
    fun aboutVersion(version: String): String
    fun aboutBranch(branch: String): String
    fun aboutCommitted(date: String): String
    val aboutMadeBy: String
}

object French : Strings {
    override val code = "fr"
    override fun cameWhileClosed(after: String?) =
        if (after == null) "L'heure à laquelle KaSH a reçu ce message, et non celle de son envoi : il était déjà là quand KaSH l'a vu pour la première fois."
        else "L'heure à laquelle KaSH a reçu ce message, et non celle de son envoi : il est arrivé pendant que KaSH ne surveillait pas ce service, après $after."
    override val timeLabel = TimeLabel(Locale.FRENCH, "HH:mm", "'Hier', HH:mm", "d MMM, HH:mm", "d MMM yyyy, HH:mm", "'Aujourd''hui'", "'Hier'", "d MMM", "d MMM yyyy")
    override val alreadyRunning = "KaSH est déjà ouvert. Quittez-le (⌘Q) avant de le relancer."
    override val inbox = "Non lus"
    override fun inboxSummary(messages: Int, conversations: Int) =
        "$messages message${s(messages)} non lu${s(messages)} dans $conversations conversation${s(conversations)}"
    override fun topicsSummary(topics: Int) = "$topics sujet${s(topics)}"
    override val messagesSection = "Messages"
    override val topicsSection = "Sujets"
    override val noUnreadMessages = "Aucun message non lu."
    override val noUnreadTopics = "Aucun sujet non lu."
    override val refresh = "Actualiser"
    override val filterPlaceholder = "Filtrer par nom ou par service"
    override val filterLabel = "Filtrer les conversations"
    override val byDate = "Par date"
    override val byCount = "Par nombre"
    override val byService = "Par service"
    override fun open(name: String) = "Ouvrir $name"
    override fun noResults(query: String) = "Aucune conversation non lue ne correspond à « $query »."
    override val searchField = "Rechercher…"
    override val searchPlaceholder = "Rechercher un contact ou un sujet…"
    override val searchLabel = "Rechercher un contact ou un sujet"
    override val searchContacts = "CONTACTS"
    override val searchTopics = "SUJETS"
    override fun searchNoMatch(query: String) = "Aucun contact ni sujet ne correspond à « $query »."
    override val searchNothingYet = "Les services n'ont encore montré aucun contact."
    override val searchNavigate = "Naviguer"
    override val searchOpen = "Ouvrir"
    override val searchClose = "Fermer"
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
    override val muteNotifications = "Couper les notifications"
    override val unmuteNotifications = "Réactiver les notifications"
    override val disableService = "Désactiver"
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
    override val optionRequired = "Obligatoire"
    override val optionTooLong = "Trop long"
    override val optionInvalid = "Valeur non valide"
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
    override fun iconOf(name: String) = "Icône de $name"
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
    override val notificationStyle = "Style des notifications"
    override val kashNotifications = "Notifications de KaSH"
    override val kashNotificationsHint = "Une fenêtre de KaSH en haut à droite de l'écran."
    override val systemNotifications = "Notifications du système"
    override val systemNotificationsHint = "Le Centre de notifications de macOS, réglé dans Réglages Système › Notifications."
    override val systemNotificationsUnavailable = "Seulement dans l'application signée (KaSH.app), pas en développement."
    override val systemNotificationsDenied = "macOS refuse les notifications de KaSH : autorisez-les dans Réglages Système › Notifications › KaSH."
    override val notificationDuration = "Durée d'affichage"
    override val temporaryNotifications = "Temporaires : elles disparaissent après 6 secondes"
    override val permanentNotifications = "Permanentes : elles restent jusqu'à ce qu'on les ferme"
    override fun systemDuration(style: NotificationAlertStyle) = when (style) {
        NotificationAlertStyle.BANNERS -> "macOS les affiche en bannières, qui disparaissent après quelques secondes."
        NotificationAlertStyle.ALERTS -> "macOS les affiche en alertes, qui restent jusqu'à ce qu'on les ferme."
        NotificationAlertStyle.NONE -> "macOS ne les affiche pas à l'écran, seulement dans le Centre de notifications."
        NotificationAlertStyle.UNAVAILABLE -> "macOS les affiche en bannières ou en alertes, selon ses réglages."
    }
    override val systemDurationHint = "KaSH ne peut pas le choisir : c'est le style des alertes de KaSH dans Réglages Système › Notifications."
    override val openSystemNotificationSettings = "Changer dans les Réglages Système…"
    override val sendTestNotification = "Envoyer une notification de test"
    override val testNotificationTitle = "Notification de test"
    override val testNotificationBody = "Les notifications des services ressembleront à celle-ci."
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
    override val showApproximateTimes = "Afficher les heures approximatives"
    override val showApproximateTimesHint = "Les messages arrivés pendant que KaSH était fermé, pour les services qui ne donnent pas l'heure (Slack, Discord). Sans elles, ils vont en bas de la liste."
    override fun servicesCount(services: Int, enabled: Int) = "$services service${s(services)} · $enabled activé${s(enabled)}"
    override val enabled = "Activé"
    override fun actionsFor(title: String) = "Actions pour $title"
    override val dragToReorder = "Glisser pour réordonner"
    override val apply = "Appliquer"
    override val ok = "OK"
    override val noService = "Aucun service. Ajoutez-en un avec le bouton + de la barre latérale."
    override val plugins = "Plugins"
    override val pluginsHint = "Un plugin ajoute un service au catalogue, et lit ses messages non lus."
    override val installZip = "Installer un zip…"
    override val installFolder = "Installer un dossier…"
    override val installAddress = "Installer depuis une adresse…"
    override val chooseZip = "Choisir le zip d'un plugin"
    override val chooseFolder = "Choisir le dossier d'un plugin"
    override val builtIn = "Intégré à KaSH"
    override fun sourceZip(name: String) = "Zip : $name"
    override fun sourceFolder(path: String) = "Dossier : $path"
    override fun sourceAddress(address: String) = "Adresse : $address"
    override fun versionCode(code: Int) = "Code de version $code"
    override val watch = "Surveiller"
    override val watchFolderHint = "KaSH relit le dossier toutes les 10 secondes, et propose chaque nouvelle version."
    override val watchAddressHint = "KaSH relit l'adresse toutes les heures, et propose chaque nouvelle version."
    override fun updateTo(version: String) = "Mettre à jour vers $version"
    override val uninstall = "Désinstaller…"
    override fun uninstallTitle(name: String) = "Désinstaller $name ?"
    override fun uninstallBody(services: Int) = when (services) {
        0 -> "Aucun service ne l'utilise."
        else -> "$services service${s(services)} l'utilise${if (services > 1) "nt" else ""} : ${if (services > 1) "ils restent" else "il reste"}, mais KaSH ne lira plus ${if (services > 1) "leurs" else "ses"} messages non lus."
    }
    override val pluginAddress = "Adresse du plugin"
    override val pluginAddressHint = "L'adresse d'un zip, ou d'un manifest.json avec ses fichiers à côté (https)."
    override val readPlugin = "Continuer"
    override val readingPlugin = "Lecture du plugin…"
    override fun installTitle(name: String, version: String) = "Installer $name $version ?"
    override fun replaceTitle(name: String, installed: String, version: String) = "Remplacer $name $installed par $version ?"
    override val pluginPages = "Son script s'exécutera dans ces pages, et pourra lire tout ce qu'elles affichent :"
    override val install = "Installer"
    override val replace = "Remplacer"
    override val pluginError = "Ce plugin ne peut pas être installé"
    override fun pluginUpdateTitle(name: String, version: String) = "$name $version est disponible"
    override val pluginUpdateBody = "Mettez-le à jour dans Paramètres › Plugins."
    override val update = "Mettre à jour"
    override val updating = "Mise à jour…"
    override fun updatePlugin(version: String) = "Mettre à jour le plugin ($version)"
    override fun pluginUpdateFailed(name: String) = "$name ne peut pas être mis à jour"
    override fun aboutTitle(name: String) = "À propos de $name"
    override fun aboutLogo(name: String) = "Logo de $name"
    override fun aboutVersion(version: String) = "Version $version"
    override fun aboutBranch(branch: String) = "Branche $branch"
    override fun aboutCommitted(date: String) = "Commit du $date"
    override val aboutMadeBy = "Fait avec amour par "

    private fun s(count: Int) = if (count > 1) "s" else ""
}

object English : Strings {
    override val code = "en"
    override fun cameWhileClosed(after: String?) =
        if (after == null) "When KaSH received this message, not when it was sent: it was already there when KaSH first saw it."
        else "When KaSH received this message, not when it was sent: it came while KaSH was not watching this service, after $after."
    override val timeLabel = TimeLabel(Locale.ENGLISH, "h:mm a", "'Yesterday', h:mm a", "MMM d, h:mm a", "MMM d, yyyy, h:mm a", "'Today'", "'Yesterday'", "MMM d", "MMM d, yyyy")
    override val alreadyRunning = "KaSH is already open. Quit it (⌘Q) before launching it again."
    override val inbox = "Unread"
    override fun inboxSummary(messages: Int, conversations: Int) =
        "$messages unread message${s(messages)} in $conversations conversation${s(conversations)}"
    override fun topicsSummary(topics: Int) = "$topics topic${s(topics)}"
    override val messagesSection = "Messages"
    override val topicsSection = "Topics"
    override val noUnreadMessages = "No unread messages."
    override val noUnreadTopics = "No unread topics."
    override val refresh = "Refresh"
    override val filterPlaceholder = "Filter by name or service"
    override val filterLabel = "Filter the conversations"
    override val byDate = "By date"
    override val byCount = "By count"
    override val byService = "By service"
    override fun open(name: String) = "Open $name"
    override fun noResults(query: String) = "No unread conversation matches “$query”."
    override val searchField = "Search…"
    override val searchPlaceholder = "Search a contact or a topic…"
    override val searchLabel = "Search a contact or a topic"
    override val searchContacts = "CONTACTS"
    override val searchTopics = "TOPICS"
    override fun searchNoMatch(query: String) = "No contact or topic matches “$query”."
    override val searchNothingYet = "The services have not shown any contact yet."
    override val searchNavigate = "Navigate"
    override val searchOpen = "Open"
    override val searchClose = "Close"
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
    override val muteNotifications = "Mute notifications"
    override val unmuteNotifications = "Unmute notifications"
    override val disableService = "Disable"
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
    override val optionRequired = "Required"
    override val optionTooLong = "Too long"
    override val optionInvalid = "Not a valid value"
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
    override fun iconOf(name: String) = "$name's icon"
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
    override val notificationStyle = "Notification style"
    override val kashNotifications = "KaSH's notifications"
    override val kashNotificationsHint = "A window of KaSH at the top right of the screen."
    override val systemNotifications = "System notifications"
    override val systemNotificationsHint = "macOS's Notification Center, set in System Settings › Notifications."
    override val systemNotificationsUnavailable = "Only in the signed application (KaSH.app), not in development."
    override val systemNotificationsDenied = "macOS refuses KaSH's notifications: allow them in System Settings › Notifications › KaSH."
    override val notificationDuration = "How long they stay"
    override val temporaryNotifications = "Temporary: they go away after 6 seconds"
    override val permanentNotifications = "Permanent: they stay until they are closed"
    override fun systemDuration(style: NotificationAlertStyle) = when (style) {
        NotificationAlertStyle.BANNERS -> "macOS shows them as banners, which go away after a few seconds."
        NotificationAlertStyle.ALERTS -> "macOS shows them as alerts, which stay until they are closed."
        NotificationAlertStyle.NONE -> "macOS does not show them on the screen, only in the Notification Center."
        NotificationAlertStyle.UNAVAILABLE -> "macOS shows them as banners or alerts, as its settings say."
    }
    override val systemDurationHint = "KaSH cannot choose it: it is KaSH's alert style in System Settings › Notifications."
    override val openSystemNotificationSettings = "Change in System Settings…"
    override val sendTestNotification = "Send a test notification"
    override val testNotificationTitle = "Test notification"
    override val testNotificationBody = "The services' notifications will look like this one."
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
    override val showApproximateTimes = "Show approximate times"
    override val showApproximateTimesHint = "The messages that came while KaSH was closed, for the services that do not tell the time (Slack, Discord). Without them, they go to the bottom of the list."
    override fun servicesCount(services: Int, enabled: Int) = "$services service${s(services)} · $enabled enabled"
    override val enabled = "Enabled"
    override fun actionsFor(title: String) = "Actions for $title"
    override val dragToReorder = "Drag to reorder"
    override val apply = "Apply"
    override val ok = "OK"
    override val noService = "No service. Add one with the + button of the sidebar."
    override val plugins = "Plugins"
    override val pluginsHint = "A plugin adds a service to the catalog, and reads its unread messages."
    override val installZip = "Install a zip…"
    override val installFolder = "Install a folder…"
    override val installAddress = "Install from an address…"
    override val chooseZip = "Choose a plugin's zip"
    override val chooseFolder = "Choose a plugin's folder"
    override val builtIn = "Built into KaSH"
    override fun sourceZip(name: String) = "Zip: $name"
    override fun sourceFolder(path: String) = "Folder: $path"
    override fun sourceAddress(address: String) = "Address: $address"
    override fun versionCode(code: Int) = "Version code $code"
    override val watch = "Watch"
    override val watchFolderHint = "KaSH reads the folder again every 10 seconds, and offers each new version."
    override val watchAddressHint = "KaSH reads the address again every hour, and offers each new version."
    override fun updateTo(version: String) = "Update to $version"
    override val uninstall = "Uninstall…"
    override fun uninstallTitle(name: String) = "Uninstall $name?"
    override fun uninstallBody(services: Int) = when (services) {
        0 -> "No service uses it."
        1 -> "1 service uses it: it stays, but KaSH will no longer read its unread messages."
        else -> "$services services use it: they stay, but KaSH will no longer read their unread messages."
    }
    override val pluginAddress = "Plugin's address"
    override val pluginAddressHint = "The address of a zip, or of a manifest.json with its files next to it (https)."
    override val readPlugin = "Continue"
    override val readingPlugin = "Reading the plugin…"
    override fun installTitle(name: String, version: String) = "Install $name $version?"
    override fun replaceTitle(name: String, installed: String, version: String) = "Replace $name $installed with $version?"
    override val pluginPages = "Its script will run in these pages, and will be able to read everything they show:"
    override val install = "Install"
    override val replace = "Replace"
    override val pluginError = "This plugin cannot be installed"
    override fun pluginUpdateTitle(name: String, version: String) = "$name $version is available"
    override val pluginUpdateBody = "Update it in Settings › Plugins."
    override val update = "Update"
    override val updating = "Updating…"
    override fun updatePlugin(version: String) = "Update the plugin ($version)"
    override fun pluginUpdateFailed(name: String) = "$name cannot be updated"
    override fun aboutTitle(name: String) = "About $name"
    override fun aboutLogo(name: String) = "$name logo"
    override fun aboutVersion(version: String) = "Version $version"
    override fun aboutBranch(branch: String) = "Branch $branch"
    override fun aboutCommitted(date: String) = "Committed: $date"
    override val aboutMadeBy = "Made with love by "

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
