package com.plus.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Returns true only when the host is an exact blocked domain or its subdomain.
 * Kept independent of Android APIs so the matching rules can be unit-tested.
 */
internal fun matchesBlockedHost(hostValue: String, blockedHosts: Set<String>): Boolean {
    val host = hostValue.trim().trimEnd('.').lowercase(Locale.ROOT)
    if (host.isBlank()) return false
    var candidate = host
    while (true) {
        if (candidate in blockedHosts) return true
        val dot = candidate.indexOf('.')
        if (dot < 0) return false
        candidate = candidate.substring(dot + 1)
    }
}

/**
 * Lightweight host-based ad/tracker blocking for Android WebView.
 * Downloads the maintained StevenBlack hosts list and keeps a small
 * built-in fallback so blocking works before the first successful refresh.
 */
object AdBlocker {
    private const val PREFS = "plus"
    private const val ENABLED_KEY = "adBlockEnabled"
    private const val UPDATED_KEY = "adBlockUpdatedAt"
    private const val LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private val worker = Executors.newSingleThreadExecutor()
    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var enabled = true
    @Volatile private var initialized = false

    private val fallbackHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "adnxs.com", "adform.net", "taboola.com",
        "outbrain.com", "criteo.com", "rubiconproject.com", "pubmatic.com",
        "openx.net", "casalemedia.com", "scorecardresearch.com", "quantserve.com",
        "adroll.com", "serving-sys.com", "moatads.com", "popads.net",
        "popcash.net", "exoclick.com", "trafficjunky.net", "zedo.com",
        " lijit.com".trim(), "bidswitch.net", "sharethrough.com"
    )

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            enabled = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED_KEY, true)
            blockedHosts.addAll(fallbackHosts)
            loadCachedList(app)
            initialized = true
            val lastUpdate = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(UPDATED_KEY, 0L)
            if (System.currentTimeMillis() - lastUpdate >= REFRESH_INTERVAL_MS) {
                refresh(app)
            }
        }
    }

    /** Persist synchronously so every control and WebView request sees the same state. */
    fun setEnabled(context: Context, value: Boolean): Boolean {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, value)
            .commit()
        enabled = value
        return saved
    }

    fun isEnabled(): Boolean = enabled

    fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        val host = try {
            Uri.parse(url).host ?: return false
        } catch (_: Exception) {
            return false
        }
        return matchesBlockedHost(host, blockedHosts)
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame || !shouldBlock(request.url.toString())) return null
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun loadCachedList(context: Context) {
        val file = File(context.filesDir, "plus_adblock_hosts.txt")
        if (!file.isFile) return
        try {
            file.useLines { lines ->
                lines.forEach { line -> parseHostLine(line)?.let(blockedHosts::add) }
            }
        } catch (_: Exception) {
            // Keep the built-in list if the cache is unreadable.
        }
    }

    private fun refresh(context: Context) {
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(LIST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "PLUS-Browser/1.0")
                    instanceFollowRedirects = true
                }
                if (connection.responseCode !in 200..299) return@execute
                val temp = File(context.filesDir, "plus_adblock_hosts.txt.tmp")
                val parsed = HashSet<String>()
                temp.bufferedWriter(Charsets.UTF_8).use { writer ->
                    connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            val host = parseHostLine(line)
                            if (host != null) {
                                parsed.add(host)
                                writer.append("0.0.0.0 ").append(host).append('\n')
                            }
                        }
                    }
                }
                if (parsed.isNotEmpty()) {
                    val target = File(context.filesDir, "plus_adblock_hosts.txt")
                    if (temp.renameTo(target)) {
                        blockedHosts.addAll(parsed)
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putLong(UPDATED_KEY, System.currentTimeMillis()).apply()
                    } else {
                        temp.delete()
                    }
                } else {
                    temp.delete()
                }
            } catch (_: Exception) {
                // Offline or blocked update: retain cached and fallback rules.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseHostLine(raw: String): String? {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return null
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2) return null
        val ip = parts[0]
        if (ip != "0.0.0.0" && ip != "127.0.0.1" && ip != "::") return null
        val host = parts[1].lowercase(Locale.ROOT).trimEnd('.')
        if (host.isBlank() || host == "localhost" || host == "localhost.localdomain" ||
            !host.contains('.') || host.any { !(it.isLetterOrDigit() || it == '-' || it == '.') }) return null
        return host
    }
}
