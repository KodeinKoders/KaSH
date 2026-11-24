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
    fun anAddressThatJavaRefusesIsReadEscaped() {
        // What Chromium keeps in an address and java.net.URI refuses: a link to another site goes to the system, clicked or in a window.
        for (url in listOf("https://shop.example/?ids=1|2", "https://shop.example/?q={x}", "https://shop.example/a^b", "https://shop.example/?q=%zz", "https://shop.example/é")) {
            assertEquals(LinkTarget.SYSTEM, popupTarget(url, whatsApp), url)
            assertEquals(LinkTarget.SYSTEM, navigationTarget(url, whatsApp, whatsApp, userGesture = true, isRedirect = false, method = "GET"), url)
        }
        // The service's own site stays in KaSH.
        assertEquals(LinkTarget.KASH, navigationTarget("https://web.whatsapp.com/?a|b", whatsApp, whatsApp, userGesture = true, isRedirect = false, method = "GET"))
        // Escaped, the address opens as it was: `|` is `%7C`.
        assertEquals("https://shop.example/?ids=1%7C2", parseAddress("https://shop.example/?ids=1|2").toString())
        assertEquals("https://shop.example/?q=%25zz", parseAddress("https://shop.example/?q=%zz").toString())
    }

    @Test
    fun onlyThePagesOfTheServicesSiteAreItsOwn() {
        assertTrue(isServicePage("https://chat.google.com/u/0/app/home", "https://mail.google.com/chat/u/0/"))
        assertTrue(isServicePage("https://app.slack.com/client/T0/C1", "https://my-team.slack.com/"))
        assertTrue(isServicePage("https://web.whatsapp.com/", "https://web.whatsapp.com/"))
        // Another site in the tab or in a frame, a sign-in page, what is not an address of the web.
        assertFalse(isServicePage("https://ads.example.com/frame", "https://web.whatsapp.com/"))
        assertFalse(isServicePage("https://accounts.google.com/signin", "https://web.whatsapp.com/"))
        assertFalse(isServicePage("about:blank", "https://web.whatsapp.com/"))
        assertFalse(isServicePage(null, "https://web.whatsapp.com/"))
    }

    @Test
    fun aHostWithAnUnderscoreIsAHost() {
        assertEquals(LinkTarget.SYSTEM, popupTarget("https://my_site.example.org/", whatsApp))
        assertEquals(LinkTarget.SYSTEM, navigationTarget("https://my_site.example.org/", whatsApp, whatsApp, userGesture = true, isRedirect = false, method = "GET"))
        assertEquals(LinkTarget.KASH, navigationTarget("https://my_site.example.org/b", "https://my_site.example.org/", "https://my_site.example.org/a", userGesture = true, isRedirect = false, method = "GET"))
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

    @Test
    fun theAddressesThatAPluginOpensInKashStayThereClickedOrInAWindow() {
        val instagram = "https://www.instagram.com/direct/inbox/"
        val openInKash = listOf(addressPattern("https://www.facebook.com/oidc/*"))
        val oidc = "https://www.facebook.com/oidc/?app_id=1&redirect_uri=https%3A%2F%2Fwww.instagram.com%2F"
        // Instagram's sign-in with Facebook: not one of the sign-in pages that KaSH knows.
        assertEquals(LinkTarget.SYSTEM, navigationTarget(oidc, instagram, instagram, userGesture = true, isRedirect = false, method = "GET"))
        assertEquals(LinkTarget.KASH, navigationTarget(oidc, instagram, instagram, userGesture = true, isRedirect = false, method = "GET", openInKash = openInKash))
        assertEquals(LinkTarget.KASH, popupTarget(oidc, instagram, openInKash))
        // The rest of Facebook still goes to the system's browser.
        assertEquals(LinkTarget.SYSTEM, navigationTarget("https://www.facebook.com/videos/1", instagram, instagram, userGesture = true, isRedirect = false, method = "GET", openInKash = openInKash))
        assertEquals(LinkTarget.SYSTEM, popupTarget("https://www.facebook.com/oidc-not/", instagram, openInKash))
    }

    @Test
    fun anAddressPatternIsLiteralButItsStars() {
        val pattern = addressPattern("https://*.example.com/a.b/*")
        assertTrue(pattern.matches("https://www.example.com/a.b/c?d=e"))
        assertFalse(pattern.matches("https://www.example.com/axb/c"))
        assertTrue(addressPattern("https://example.com/").matches("HTTPS://EXAMPLE.COM/"))
    }
}
