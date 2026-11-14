package app.kash.browser

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinksTest {
    private val whatsApp = "https://web.whatsapp.com/"
    private val googleChat = "https://mail.google.com/chat/u/0/"

    @Test
    fun aSiteIsItsLastTwoLabelsOrThreeUnderACountrysSecondLevel() {
        assertEquals("whatsapp.com", siteOf("web.whatsapp.com"))
        assertEquals("google.com", siteOf("chat.google.com"))
        assertEquals("example.com", siteOf("example.com"))
        assertEquals("bbc.co.uk", siteOf("www.bbc.co.uk"))
        assertEquals("localhost", siteOf("localhost"))
        assertEquals("127.0.0.1", siteOf("127.0.0.1"))
    }

    @Test
    fun aWindowToAnotherSiteGoesToTheSystem() {
        assertEquals(LinkTarget.SYSTEM, popupTarget("https://kodein.net/article", whatsApp))
        assertEquals(LinkTarget.SYSTEM, popupTarget("https://docs.google.com/document/d/1", whatsApp))
        assertEquals(LinkTarget.SYSTEM, popupTarget("mailto:someone@example.com", whatsApp))
    }

    @Test
    fun aWindowOfTheServicesSiteOrASignInStaysInKash() {
        assertEquals(LinkTarget.KASH, popupTarget("https://chat.google.com/u/0/app/chat/x", googleChat))
        assertEquals(LinkTarget.KASH, popupTarget("https://accounts.google.com/o/oauth2/auth?client_id=x", "https://app.example.com/"))
        assertEquals(LinkTarget.KASH, popupTarget("https://www.facebook.com/v19.0/dialog/oauth?app_id=1", "https://app.example.com/"))
        assertEquals(LinkTarget.KASH, popupTarget("https://github.com/login/oauth/authorize", "https://app.example.com/"))
        // What the page fills itself, or shows from memory.
        assertEquals(LinkTarget.KASH, popupTarget("about:blank", whatsApp))
        assertEquals(LinkTarget.KASH, popupTarget("blob:https://web.whatsapp.com/1234", whatsApp))
    }

    @Test
    fun aLinkThroughGooglesRedirectorGoesWhereItLeads() {
        assertEquals(LinkTarget.SYSTEM, popupTarget("https://www.google.com/url?q=https%3A%2F%2Fkodein.net%2F&sa=D", googleChat))
        assertEquals(LinkTarget.KASH, popupTarget("https://www.google.com/url?q=https%3A%2F%2Fchat.google.com%2Fx", googleChat))
    }

    @Test
    fun onlyTheSignInPathsOfASiteAreSignIns() {
        assertTrue(isSignIn(URI("https://login.microsoftonline.com/common/oauth2/authorize")))
        assertTrue(isSignIn(URI("https://www.facebook.com/login.php")))
        assertFalse(isSignIn(URI("https://www.facebook.com/videos/123")))
        assertFalse(isSignIn(URI("https://github.com/JetBrains/jcef")))
    }

    @Test
    fun anApplicationsAddressIsNotLoadedEvenWhenTheSiteSendsThere() {
        val slack = "https://mon-equipe.slack.com/"
        // Slack sends to its desktop application after signing in, without a click.
        assertEquals(LinkTarget.NOWHERE, navigationTarget("slack://T0/magic-login/1?id=1", slack, slack, userGesture = false, isRedirect = true, method = "GET"))
        assertEquals(LinkTarget.NOWHERE, navigationTarget("zoommtg://zoom.us/join?confno=1", slack, slack, userGesture = true, isRedirect = false, method = "GET"))
        assertEquals(LinkTarget.NOWHERE, popupTarget("slack://open", slack))
        // The mail and the phone go to the system only when the user clicked.
        assertEquals(LinkTarget.SYSTEM, navigationTarget("mailto:a@example.com", slack, slack, userGesture = true, isRedirect = false, method = "GET"))
        assertEquals(LinkTarget.NOWHERE, navigationTarget("mailto:a@example.com", slack, slack, userGesture = false, isRedirect = false, method = "GET"))
        // What a page makes itself is loaded.
        assertEquals(LinkTarget.KASH, navigationTarget("about:blank", slack, slack, userGesture = false, isRedirect = false, method = "GET"))
    }

    @Test
    fun aClickToAnotherSiteInThePageGoesToTheSystem() {
        assertEquals(LinkTarget.SYSTEM, navigationTarget("https://kodein.net/", whatsApp, whatsApp, userGesture = true, isRedirect = false, method = "GET"))
    }

    @Test
    fun whatTheSiteDoesItselfStaysInKash() {
        val outside = "https://kodein.net/"
        // No click: the page navigates itself.
        assertEquals(LinkTarget.KASH, navigationTarget(outside, whatsApp, whatsApp, userGesture = false, isRedirect = false, method = "GET"))
        // A redirect, a form that is posted (signing in on a company's page).
        assertEquals(LinkTarget.KASH, navigationTarget(outside, whatsApp, whatsApp, userGesture = true, isRedirect = true, method = "GET"))
        assertEquals(LinkTarget.KASH, navigationTarget(outside, whatsApp, whatsApp, userGesture = true, isRedirect = false, method = "POST"))
    }

    @Test
    fun aClickOnTheServicesSiteOrThePagesSiteStaysInKash() {
        assertEquals(LinkTarget.KASH, navigationTarget("https://web.whatsapp.com/x", whatsApp, whatsApp, true, false, "GET"))
        // The page is a company's sign-in page, where the service sent it: its own links are part of signing in.
        assertEquals(LinkTarget.KASH, navigationTarget("https://sso.example.com/next", "https://app.service.io/", "https://sso.example.com/login", true, false, "GET"))
    }
}
