package com.plus.browser

import java.net.URI

/**
 * Lightweight, host-based ad/tracker filter for Android WebView.
 * This intentionally uses a conservative list to reduce accidental site breakage.
 */
internal object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "criteo.com",
        " taboola.com".trim(),
        "outbrain.com",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "casalemedia.com",
        "adsrvr.org",
        "smartadserver.com",
        " lijit.com".trim(),
        "scorecardresearch.com",
        "zedo.com",
        "adform.net",
        "bidr.io",
        "33across.com",
        "serving-sys.com"
    )

    fun isBlocked(url: String, allowlist: Set<String>): Boolean {
        val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        if (allowlist.any { domain -> domain.isNotBlank() && (host == domain || host.endsWith(".$domain")) }) return false
        return blockedHosts.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    fun parseAllowlist(raw: String): Set<String> = raw
        .split(',', '\\n', ';')
        .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/') }
        .filter { it.isNotBlank() && it.contains('.') }
        .toSet()
}
