package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SUBSCRIPTION_PASTE_V123
 *
 * Adding a subscription by pasting its link failed for three separate reasons, and each one is a
 * case below: a clipboard hands over more than the URL, a name used to be mandatory, and a plain
 * `http://` link must be recognised as a subscription (so the caller can refuse it with a reason)
 * rather than mistaken for a list of config links.
 */
class SubscriptionLinkTest {

    private val link = "https://sub.provider.example/api/v1/client/subscribe?token=abc123"

    @Test
    fun acceptsACleanHttpsLink() {
        assertEquals(link, SubscriptionLink.normalize(link))
        assertTrue(SubscriptionLink.isSecure(link))
    }

    @Test
    fun stripsWhatAClipboardAddsAroundALink() {
        listOf(
            "  $link\n",
            "\"$link\"",
            "<$link>",
            "($link)",
            "your sub: $link — enjoy",
            "$link.",
            "$link,",
            "link:$link"
        ).forEach { pasted ->
            assertEquals(
                "clipboard form must normalize: ${pasted.replace("\n", "\\n")}",
                link,
                SubscriptionLink.normalize(pasted)
            )
        }
    }

    @Test
    fun takesTheUrlOutOfASentenceItWasPastedWith() {
        val pasted = "Here is your subscription https://panel.example/sub/9f2 thanks!"
        assertEquals("https://panel.example/sub/9f2", SubscriptionLink.normalize(pasted))
    }

    @Test
    fun configLinksAreNotSubscriptionLinks() {
        listOf(
            "vless://a1b2@1.2.3.4:443?security=reality#DE",
            "vmess://eyJhZGQiOiIxLjIuMy40In0=",
            "ss://YWVzLTI1Ni1nY206cGFzcw==@1.2.3.4:8388",
            "trojan://password@host:443",
            "{ \"outbounds\": [] }",
            "",
            "   "
        ).forEach { pasted ->
            assertFalse(
                "must not read as a subscription: $pasted",
                SubscriptionLink.isSubscriptionUrl(pasted)
            )
        }
    }

    @Test
    fun plainHttpIsRecognisedButNotSecure() {
        val insecure = "http://sub.provider.example/sub"
        assertEquals(insecure, SubscriptionLink.normalize(insecure))
        assertTrue(SubscriptionLink.isSubscriptionUrl(insecure))
        assertFalse(SubscriptionLink.isSecure(insecure))
    }

    @Test
    fun rejectsLinksThatCannotBeFetched() {
        listOf(
            "https://",
            "https://localhost",
            "https:///subscribe",
            "ftp://sub.provider.example/sub",
            "https://sub provider.example/sub",
            "not a link at all"
        ).forEach { pasted ->
            assertNull("must be refused: $pasted", SubscriptionLink.normalize(pasted))
        }
    }

    @Test
    fun derivesAReadableGroupNameFromTheHost() {
        assertEquals("Sub Provider", SubscriptionLink.nameFor(link))
        assertEquals("Panel", SubscriptionLink.nameFor("https://www.panel.example/sub"))
        assertEquals(
            "My Vpn Shop Example",
            SubscriptionLink.nameFor("https://my-vpn_shop.example.org/subscribe")
        )
    }

    @Test
    fun aNameIsAlwaysAvailable() {
        assertEquals("Subscription", SubscriptionLink.nameFor(""))
        assertEquals("Subscription", SubscriptionLink.nameFor("nonsense"))
    }
}
