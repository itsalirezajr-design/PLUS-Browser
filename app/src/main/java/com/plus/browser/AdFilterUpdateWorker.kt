package com.plus.browser

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Refreshes EasyList/EasyPrivacy domain filters weekly; failures keep the last good rules. */
internal class AdFilterUpdateWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = withContext(Dispatchers.IO) {
        try {
            val prefs=applicationContext.getSharedPreferences("plus",Context.MODE_PRIVATE)
            val current=prefs.getString("advanced_ad_rules","") ?: ""
            val easyList=download("https://easylist.to/easylist/easylist.txt")
            val privacy=download("https://easylist.to/easylist/easyprivacy.txt")
            val combined=(easyList+"\n"+privacy).take(8_000_000)
            if(combined.lineSequence().count()>100) {
                prefs.edit().putString("advanced_ad_rules",combined).putLong("advanced_ad_rules_updated",System.currentTimeMillis()).apply()
                AdBlocker.setRules(combined)
                Result.success()
            } else {
                if(current.isNotBlank()) Result.success() else Result.retry()
            }
        } catch (_:Exception) { Result.retry() }
    }
    private fun download(address:String):String {
        val connection=(URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout=12_000; readTimeout=20_000; requestMethod="GET"
            setRequestProperty("User-Agent","PLUS-Browser-AdFilter/1.0")
        }
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out=StringBuilder()
                val buffer=CharArray(8192)
                while(true) { val n=reader.read(buffer); if(n<0) break; if(out.length+n>4_000_000) break; out.append(buffer,0,n) }
                out.toString()
            }
        } finally { connection.disconnect() }
    }
    companion object {
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<AdFilterUpdateWorker>(7,TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("plus_ad_filter_refresh",ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
}
