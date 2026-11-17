package app.kash.core

import java.text.Normalizer

/** The sections of the search: the people, then what is not a person (groups, channels, spaces, servers). */
public enum class SearchSection {
    CONTACTS,
    TOPICS,
    ;

    public companion object {
        /** The section of a conversation of [kind]. */
        public fun of(kind: ConversationKind): SearchSection = if (kind == ConversationKind.DIRECT) CONTACTS else TOPICS
    }
}

/**
 * A line of the search: a contact or a topic of a service ([Contact]), with what is unread in it.
 *
 * @property conversation Its id in its service, which opens it.
 * @property count Its unread messages, 0 when it has none or is a topic.
 * @property topic Whether it is a topic with new messages (see [Conversation.topic]).
 */
public data class SearchEntry(
    val service: Service,
    val conversation: String,
    val name: String,
    val kind: ConversationKind,
    val count: Int = 0,
    val topic: Boolean = false,
) {
    val section: SearchSection get() = SearchSection.of(kind)

    /** Whether something is unread in it. */
    val unread: Boolean get() = count > 0 || topic

    /** What tells it apart from every other line. */
    val key: String get() = "${service.id.value}/$conversation"
}

/** A line of the search that matches the query, the letters of its name that matched, and how well. */
public data class SearchMatch(val entry: SearchEntry, val indices: List<Int>, val score: Int)

/**
 * How well [query] matches [text], and the indices of the letters of [text] that it matched, or null when it does not: its letters, in order,
 * without regard for the case, the accents (`e` finds `é`) and the spaces of the query. Letters together, at the start of the text or of a word, in a
 * short text, are better. KaTTY's palette's, with the accents.
 */
public fun fuzzyMatch(query: String, text: String): Pair<Int, List<Int>>? {
    val needle = query.filterNot { it.isWhitespace() }.map(::fold)
    if (needle.isEmpty()) return 0 to emptyList()
    // One character for each of the text's, so that the indices are the text's.
    val hay = text.map(::fold)
    var best: Pair<Int, List<Int>>? = null
    var start = hay.indexOf(needle[0])
    while (start >= 0) {
        val indices = ArrayList<Int>(needle.size)
        var at = start
        var score = 0
        var previous = -2
        var ok = true
        for (letter in needle) {
            val found = hay.indexOf(letter, at)
            if (found < 0) {
                ok = false
                break
            }
            indices += found
            score += if (found == previous + 1) 6 else -(found - previous - 1).coerceAtMost(3)
            if (found == 0 || !text[found - 1].isLetterOrDigit()) score += 5
            previous = found
            at = found + 1
        }
        if (ok) {
            score -= (hay.size - needle.size) / 4
            if (best == null || score > best.first) best = score to indices
        }
        start = hay.indexOf(needle[0], start + 1)
    }
    return best
}

// A character without its case and its accents: the first of its decomposition (é is e and an accent), in lower case.
private fun fold(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar()

private fun List<Char>.indexOf(c: Char, from: Int): Int {
    for (i in from until size) if (this[i] == c) return i
    return -1
}

/** What the search lists, made from the contacts of the services and what is unread in each. */
public object Search {
    // Slack's replies in threads: a line of the Unread tab, not a contact.
    private const val THREADS = "threads"

    /**
     * Every line, before the query: the contacts of each enabled service of a plugin ([directory]), in the services' order, then its unread
     * conversations that are not among them (so that what is unread is always found), each once, with what is unread in it ([unread]).
     */
    public fun entries(services: List<Service>, directory: Map<ServiceId, List<Contact>>, unread: Map<ServiceId, ServiceUnread>): List<SearchEntry> =
        services.filter { it.enabled && it.plugin != null }.flatMap { service ->
            val conversations = unread[service.id]?.conversations.orEmpty().filter { it.id != THREADS }
            val byId = conversations.associateBy { it.id }
            val seen = HashSet<String>()
            val known = directory[service.id].orEmpty().mapNotNull { contact ->
                if (!seen.add(contact.id)) return@mapNotNull null
                val conversation = byId[contact.id]
                SearchEntry(service, contact.id, contact.name, contact.kind, conversation?.count ?: 0, conversation?.topic ?: false)
            }
            known + conversations.filter { it.name.isNotBlank() && seen.add(it.id) }.map { SearchEntry(service, it.id, it.name, it.kind, it.count, it.topic) }
        }

    /**
     * The lines that match [query] ([fuzzyMatch], on their names), Contacts then Topics. In a section, the best match first; with no query, the
     * unread ones first, then in their order.
     */
    public fun search(entries: List<SearchEntry>, query: String): List<SearchMatch> {
        val matches = entries.mapNotNull { entry ->
            val (score, indices) = fuzzyMatch(query, entry.name) ?: return@mapNotNull null
            SearchMatch(entry, indices, score)
        }
        return SearchSection.entries.flatMap { section ->
            val inSection = matches.filter { it.entry.section == section }
            if (query.isBlank()) inSection.sortedByDescending { it.entry.unread } else inSection.sortedByDescending { it.score }
        }
    }
}
