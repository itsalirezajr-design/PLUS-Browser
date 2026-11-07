package com.plus.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerHostMatchingTest {
    private val blocked = setOf("doubleclick.net", "googlesyndication.com")

    @Test fun blocksExactHost() {
        assertTrue(matchesBlockedHost("doubleclick.net", blocked))
    }

    @Test fun blocksSubdomainsAtDomainBoundary() {
        assertTrue(matchesBlockedHost("ads.doubleclick.net", blocked))
        assertTrue(matchesBlockedHost("a.b.googlesyndication.com", blocked))
    }

    @Test fun matchingIsCaseInsensitiveAndIgnoresTrailingDot() {
        assertTrue(matchesBlockedHost("ADS.DOUBLECLICK.NET.", blocked))
    }

    @Test fun doesNotBlockUnrelatedSuffixLookalikes() {
        assertFalse(matchesBlockedHost("notdoubleclick.net", blocked))
        assertFalse(matchesBlockedHost("doubleclick.net.example.org", blocked))
    }

    @Test fun allowsUnlistedHosts() {
        assertFalse(matchesBlockedHost("www.example.com", blocked))
    }
}
