package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceCatalogTest {
    @Test
    fun aSlackWorkspaceIsWhatIsTypedOrReadFromItsAddress() {
        assertEquals("mon-equipe", ServiceCatalog.slackWorkspace("mon-equipe"))
        assertEquals("mon-equipe", ServiceCatalog.slackWorkspace("  Mon-Equipe "))
        assertEquals("mon-equipe", ServiceCatalog.slackWorkspace("https://mon-equipe.slack.com/"))
        assertEquals("mon-equipe", ServiceCatalog.slackWorkspace("mon-equipe.slack.com"))
        // An Enterprise Grid organization.
        assertEquals("acme.enterprise", ServiceCatalog.slackWorkspace("acme.enterprise.slack.com"))
    }

    @Test
    fun whatIsNotAWorkspaceIsRefused() {
        assertNull(ServiceCatalog.slackWorkspace(""))
        assertNull(ServiceCatalog.slackWorkspace("mon équipe"))
        assertNull(ServiceCatalog.slackWorkspace("-equipe"))
        assertNull(ServiceCatalog.slackWorkspace("equipe-"))
        assertNull(ServiceCatalog.slackWorkspace("mon_equipe"))
    }

    @Test
    fun aSlackServicesAddressIsItsWorkspacesAndBack() {
        assertEquals("https://mon-equipe.slack.com/", ServiceCatalog.slackUrl("mon-equipe"))
        assertEquals("mon-equipe", ServiceCatalog.slackWorkspaceOf("https://mon-equipe.slack.com/"))
        assertNull(ServiceCatalog.slackWorkspaceOf("https://app.slack.com/client"))
        assertNull(ServiceCatalog.slackWorkspaceOf("https://example.com/"))
    }
}
