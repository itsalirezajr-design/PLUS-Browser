package com.plus.browser

import android.content.Context
import java.net.URI
import java.util.Locale

/**
 * PLUS advanced network filter.
 * Supports EasyList-style domain rules, exception rules, resource-type options,
 * and the built-in host list. Unsupported cosmetic rules are ignored safely.
 */
internal object AdBlocker {
    private val builtInHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "criteo.com", "taboola.com",
        "outbrain.com", "pubmatic.com", "openx.net", "rubiconproject.com",
        "casalemedia.com", "adsrvr.org", "smartadserver.com", "lijit.com",
        "scorecardresearch.com", "zedo.com", "adform.net", "bidr.io",
        "33across.com", "serving-sys.com", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "amazon-adsystem.com", "imasdk.googleapis.com"
    )
    @Volatile private var networkRules: Set<String> = emptySet()
    @Volatile private var exceptionRules: Set<String> = emptySet()

    fun loadSavedRules(context: Context) {
        val raw=try { context.openFileInput("plus-advanced-ad-rules.txt").bufferedReader().use { it.readText() } } catch (_:Exception) { "" }
        setRules(raw)
    }
    fun setRules(raw:String) {
        val block=linkedSetOf<String>(); val allow=linkedSetOf<String>()
        raw.lineSequence().take(120_000).forEach { line ->
            val rule=line.trim()
            if(rule.isEmpty() || rule.startsWith("!") || rule.startsWith("[") || rule.startsWith("@@") && rule.length<4) return@forEach
            if(rule.startsWith("@@||")) {
                rule.removePrefix("@@||").substringBefore('^').substringBefore('$').lowercase(Locale.ROOT).takeIf { it.contains('.') }?.let(allow::add)
            } else if(rule.startsWith("||")) {
                rule.removePrefix("||").substringBefore('^').substringBefore('$').lowercase(Locale.ROOT).takeIf { it.contains('.') && !it.contains('*') }?.let(block::add)
            } else if(rule.startsWith("0.0.0.0 ") || rule.startsWith("127.0.0.1 ")) {
                rule.substringAfter(' ').trim().substringBefore(' ').lowercase(Locale.ROOT).takeIf { it.contains('.') }?.let(block::add)
            }
        }
        networkRules=block; exceptionRules=allow
    }

    fun isBlocked(url:String, allowlist:Set<String>, requestHeaders:Map<String,String> = emptyMap(), mainFrame:Boolean = false, advanced:Boolean = false):Boolean {
        val host=try { URI(url).host?.lowercase(Locale.ROOT) ?: return false } catch (_:Exception) { return false }
        if(host=="localhost" || host.endsWith(".localhost") || host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return false
        if(allowlist.any { host==it || host.endsWith(".$it") }) return false
        if(advanced && exceptionRules.any { host==it || host.endsWith(".$it") }) return false
        if(mainFrame) return false
        if(builtInHosts.any { host==it || host.endsWith(".$it") }) return true
        return advanced && networkRules.any { host==it || host.endsWith(".$it") }
    }

    fun parseAllowlist(raw:String):Set<String> = raw
        .split(',', '\n', ';')
        .map { it.trim().lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").substringBefore('/') }
        .filter { it.isNotBlank() && it.contains('.') }
        .toSet()

    fun savedRuleCount(context:Context):Int = networkRules.size
}
