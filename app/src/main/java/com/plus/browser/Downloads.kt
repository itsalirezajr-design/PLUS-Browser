package com.plus.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.UUID

internal data class DownloadItem(val id:String,val name:String,val url:String,val mime:String,val state:String,val bytes:Long,val total:Long,val speed:Long,val date:Long,val error:String,val token:String,val validator:String,val agent:String,val cookie:String)

internal class Downloads private constructor(private val context:Context):SQLiteOpenHelper(context,"plus_downloads.db",null,1) {
    companion object {
        @Volatile private var instance:Downloads?=null
        fun get(context:Context):Downloads = instance ?: synchronized(this) { instance ?: Downloads(context.applicationContext).also { instance=it } }
    }
    override fun onCreate(db:SQLiteDatabase) { db.execSQL("CREATE TABLE downloads (id TEXT PRIMARY KEY, name TEXT NOT NULL, url TEXT NOT NULL, mime TEXT NOT NULL, state TEXT NOT NULL, bytes INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT -1, speed INTEGER NOT NULL DEFAULT 0, date INTEGER NOT NULL, error TEXT NOT NULL DEFAULT '', token TEXT NOT NULL, validator TEXT NOT NULL DEFAULT '', agent TEXT NOT NULL DEFAULT '', cookie TEXT NOT NULL DEFAULT '')") }
    override fun onUpgrade(db:SQLiteDatabase, oldVersion:Int, newVersion:Int) {}
    fun all():List<DownloadItem> { val result=mutableListOf<DownloadItem>(); readableDatabase.rawQuery("SELECT id,name,url,mime,state,bytes,total,speed,date,error,token,validator,agent,cookie FROM downloads ORDER BY date DESC",null).use { c -> while(c.moveToNext()) result.add(DownloadItem(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getString(10),c.getString(11),c.getString(12),c.getString(13))) }; return result }
    fun item(id:String)=all().firstOrNull { it.id==id }
    fun file(id:String):File { val dir=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir,"downloads"); dir.mkdirs(); return File(dir,id) }
    @Synchronized fun add(url:String,agent:String?,disposition:String?,mime:String?,cookie:String?) {
        if(!url.startsWith("https://",true) && !url.startsWith("http://",true)) throw IllegalArgumentException("Only HTTP and HTTPS downloads are supported")
        if(all().any { it.url==url && it.state!="cancelled" }) throw IllegalStateException("Download already exists; use the existing record")
        val id=UUID.randomUUID().toString(); val token=UUID.randomUUID().toString()
        val name=URLUtil.guessFileName(url,disposition,mime).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").take(150).ifBlank { "download" }
        val v=ContentValues().apply { put("id",id);put("name",name);put("url",url);put("mime",mime ?: "application/octet-stream");put("state","queued");put("date",System.currentTimeMillis());put("token",token);put("agent",agent ?: "");put("cookie",cookie ?: "") }
        writableDatabase.insertOrThrow("downloads",null,v); enqueue(id,token)
    }
    private fun enqueue(id:String,token:String) {
        val req=OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("id" to id,"token" to token)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("download-$id",ExistingWorkPolicy.REPLACE,req)
    }
    fun update(id:String,token:String,values:ContentValues):Boolean = writableDatabase.update("downloads",values,"id=? AND token=?", arrayOf(id,token))>0
    fun pause(id:String) { val token=UUID.randomUUID().toString(); val v=ContentValues().apply {put("state","paused");put("token",token);put("speed",0)}; if(writableDatabase.update("downloads",v,"id=? AND state IN ('queued','running')",arrayOf(id))>0) { WorkManager.getInstance(context).cancelUniqueWork("download-$id"); kotlin.concurrent.thread { synchronized(id.intern()) { update(id,token,ContentValues().apply { put("bytes",file(id).length()) }) } } } }
    fun resume(id:String) { val token=UUID.randomUUID().toString();val v=ContentValues().apply{put("state","queued");put("token",token);put("error","");put("speed",0)}; if(writableDatabase.update("downloads",v,"id=? AND state IN ('paused','failed','cancelled')",arrayOf(id))>0) enqueue(id,token) }
    fun cancel(id:String) { val v=ContentValues().apply{put("state","cancelled");put("token",UUID.randomUUID().toString());put("speed",0);put("bytes",0);put("validator","")}; if(writableDatabase.update("downloads",v,"id=? AND state IN ('queued','running','paused','failed')",arrayOf(id))>0) { WorkManager.getInstance(context).cancelUniqueWork("download-$id"); kotlin.concurrent.thread { synchronized(id.intern()) { if(item(id)?.state=="cancelled") file(id).delete() } } } }
    fun delete(id:String) { writableDatabase.delete("downloads","id=?",arrayOf(id)); WorkManager.getInstance(context).cancelUniqueWork("download-$id"); kotlin.concurrent.thread { synchronized(id.intern()) { file(id).delete() } } }
    // Repair the tiny window between saving a queued record and scheduling work.
    fun reconcile() { val wm=WorkManager.getInstance(context); all().filter{it.state=="queued"||it.state=="running"}.forEach { d -> try { val work=wm.getWorkInfosForUniqueWork("download-${d.id}").get(); if(work.none { !it.state.isFinished }) { val token=UUID.randomUUID().toString(); val v=ContentValues().apply {put("state","queued");put("token",token);put("speed",0)}; if(writableDatabase.update("downloads",v,"id=? AND token=?",arrayOf(d.id,d.token))>0) enqueue(d.id,token) } } catch (_:Exception) {} } }
    fun open(item:DownloadItem,share:Boolean) { try { val f=file(item.id); if(!f.exists()) throw IllegalStateException("File is no longer available")
        // Give the receiving app a meaningful filename via a displayName query parameter.
        val base=FileProvider.getUriForFile(context,context.packageName+".files",f)
        val uri=base.buildUpon().appendQueryParameter("displayName",item.name).build()
        val i=if(share) Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM,uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri,item.mime)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(if(share) Intent.createChooser(i,"Share download").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else i)
    } catch(e:Exception) { Toast.makeText(context,"Cannot open file: ${e.localizedMessage}",Toast.LENGTH_LONG).show() } }
}

private object DownloadGate {
    private val lock=java.lang.Object()
    private var running=0
    fun enter(context:Context,stopped:()->Boolean):Boolean = synchronized(lock) {
        while(!stopped()) {
            val limit=context.getSharedPreferences("plus_pro",Context.MODE_PRIVATE).getInt("max_downloads",3).coerceIn(1,4)
            if(running<limit) { running++; return@synchronized true }
            try { lock.wait(500) } catch(_:InterruptedException) { return@synchronized false }
        }
        false
    }
    fun leave() = synchronized(lock) { running--;lock.notifyAll() }
}

internal class DownloadWorker(context:Context, params:WorkerParameters):Worker(context,params) {
    private val db=Downloads.get(context)
    private fun values(vararg pairs:Pair<String,Any>):ContentValues=ContentValues().apply { for((k,v) in pairs) when(v) { is String->put(k,v); is Long->put(k,v); is Int->put(k,v) } }
    override fun doWork():Result {
        val id=inputData.getString("id") ?: return Result.failure(); val token=inputData.getString("token") ?: return Result.failure()
        if(!DownloadGate.enter(applicationContext) { isStopped }) return Result.success()
        try { return transfer(id,token) } finally { DownloadGate.leave() }
    }
    private fun transfer(id:String,token:String):Result {
        synchronized(id.intern()) {
            val item=db.item(id) ?: return Result.success(); if(item.token!=token) return Result.success()
            try {
                setForegroundAsync(foreground(item)).get()
                if(!db.update(id,token,values("state" to "running","speed" to 0))) return Result.success()
                val file=db.file(id); var offset=if(file.exists() && item.validator.isNotBlank()) file.length() else 0L
                var connection:HttpURLConnection?=null
                try {
                    var target=URL(item.url); val original=target; var redirects=0
                    while(true) {
                        if(isStopped) return Result.success()
                        val c=(target.openConnection() as HttpURLConnection).apply { instanceFollowRedirects=false;connectTimeout=20000;readTimeout=20000;setRequestProperty("Accept-Encoding","identity");if(item.agent.isNotEmpty()) setRequestProperty("User-Agent",item.agent); if(item.cookie.isNotEmpty() && target.host.equals(original.host,true) && target.protocol==original.protocol && target.port==original.port) setRequestProperty("Cookie",item.cookie); if(offset>0) {setRequestProperty("Range","bytes=$offset-");setRequestProperty("If-Range",item.validator)} }
                        connection=c; val code=c.responseCode
                        if(code in listOf(301,302,303,307,308)) { val location=c.getHeaderField("Location") ?: throw Exception("Redirect missing location"); val next=URL(target,location); if(next.protocol!="http" && next.protocol!="https") throw Exception("Unsupported redirect"); if(target.protocol=="https" && next.protocol!="https") throw Exception("Insecure redirect blocked"); if(++redirects>8) throw Exception("Too many redirects"); c.disconnect();target=next;continue }
                        if(code==416 && offset>0) { c.disconnect(); offset=0; continue }
                        if(code !in 200..299) throw Exception("HTTP $code")
                        if(offset>0 && code!=206) offset=0
                        if(code==206 && !c.getHeaderField("Content-Range").orEmpty().startsWith("bytes $offset-")) throw Exception("Invalid range response")
                        val length=c.getHeaderFieldLong("Content-Length",-1); val rangeTotal=if(code==206) c.getHeaderField("Content-Range").orEmpty().substringAfterLast("/", "").toLongOrNull() else null; val total=rangeTotal ?: if(length>=0) offset+length else -1L
                        val etag=c.getHeaderField("ETag").orEmpty().takeUnless { it.startsWith("W/") }.orEmpty(); val validator=etag.ifEmpty { c.getHeaderField("Last-Modified").orEmpty() }
                        val responseMime=c.contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { item.mime }
                        if(offset==0L) FileOutputStream(file,false).close()
                        if(!db.update(id,token,values("bytes" to offset,"total" to total,"validator" to validator,"mime" to responseMime))) return Result.success()
                        var done=offset; var lastTime=System.currentTimeMillis(); var lastBytes=done
                        c.inputStream.use { input -> FileOutputStream(file,offset>0).use { out -> val buffer=ByteArray(64*1024); while(true) { if(isStopped) return Result.success(); val n=input.read(buffer); if(n<0) break; out.write(buffer,0,n); done+=n; val now=System.currentTimeMillis(); if(now-lastTime>=500) { val speed=(done-lastBytes)*1000/(now-lastTime).coerceAtLeast(1); if(!db.update(id,token,values("bytes" to done,"speed" to speed))) return Result.success(); lastTime=now;lastBytes=done } };out.fd.sync() } }
                        if(total>=0 && done!=total) throw Exception("Incomplete transfer")
                        db.update(id,token,values("bytes" to done,"total" to done,"state" to "completed","speed" to 0,"error" to "","cookie" to "")); return Result.success()
                    }
                } finally { connection?.disconnect() }
            } catch(e:Exception) { if(!isStopped) db.update(id,token,values("state" to "failed","speed" to 0,"error" to (e.localizedMessage ?: "Download failed"))); return Result.success() }
        }
    }
    private fun foreground(item:DownloadItem):ForegroundInfo { val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; manager.createNotificationChannel(NotificationChannel("plus_downloads","PLUS downloads",NotificationManager.IMPORTANCE_LOW)); val n=NotificationCompat.Builder(applicationContext,"plus_downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("PLUS Browser download").setContentText(item.name).setOngoing(true).setProgress(0,0,true).build(); return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(item.id.hashCode(),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(item.id.hashCode(),n) }
}

@Composable internal fun DownloadsScreen() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val db=remember { Downloads.get(context) }
    var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
    val scope=rememberCoroutineScope()
    val actionMutex=remember { Mutex() }
    fun action(block:()->Unit) { scope.launch { actionMutex.withLock { withContext(Dispatchers.IO) { block() }; downloads=withContext(Dispatchers.IO) { db.all() } } } }
    val fa=java.util.Locale.getDefault().language=="fa"
    fun t(en:String, per:String)=if(fa) per else en
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { db.reconcile() }; while(true) { downloads=withContext(Dispatchers.IO) { db.all() }; delay(if(downloads.any { it.state=="running" || it.state=="queued" }) 900 else 3000) } }
    if(downloads.isEmpty()) Text(t("No downloads yet.","هنوز دانلودی وجود ندارد."),Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    else LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { items(downloads,key={it.id}) { d ->
        Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.75f)),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(d.name,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=2); Spacer(Modifier.height(6.dp))
            val status=when(d.state){"queued"->t("Queued","در صف");"running"->t("Downloading","در حال دانلود");"paused"->t("Paused","متوقف");"completed"->t("Completed","تکمیل شد");"failed"->t("Failed","ناموفق");else->t("Cancelled","لغو شد")}
            Text(status,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Spacer(Modifier.height(8.dp))
            if(d.total>0) { LinearProgressIndicator(progress={ (d.bytes.toFloat()/d.total).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth()); Text("${(d.bytes*100/d.total).coerceIn(0,100)}%  ·  ${Formatter.formatFileSize(context,d.bytes)} / ${Formatter.formatFileSize(context,d.total)}",fontSize=12.sp) }
            else { if(d.state=="running") LinearProgressIndicator(modifier=Modifier.fillMaxWidth()); Text(Formatter.formatFileSize(context,d.bytes)+" / "+t("Unknown size","اندازه نامشخص"),fontSize=12.sp) }
            if(d.state=="running" && d.speed>0) { val eta=if(d.total>d.bytes) " · ETA ~${(d.total-d.bytes)/d.speed}s" else ""; Text(Formatter.formatFileSize(context,d.speed)+"/s"+eta,fontSize=12.sp,color=MaterialTheme.colorScheme.secondary) }
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(d.date)),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(d.error.isNotEmpty()) Text(d.error,fontSize=12.sp,color=MaterialTheme.colorScheme.error)
            Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                if(d.state=="running"||d.state=="queued") TextButton(onClick={action { db.pause(d.id) }}) {Text(t("Pause","مکث"))}
                if(d.state=="paused"||d.state=="failed"||d.state=="cancelled") TextButton(onClick={action { db.resume(d.id) }}) {Text(if(d.state=="paused") t("Resume","ادامه") else t("Retry","تلاش مجدد"))}
                if(d.state=="running"||d.state=="queued"||d.state=="paused") TextButton(onClick={action { db.cancel(d.id) }}) {Text(t("Cancel","لغو"))}
                if(d.state=="completed") { TextButton(onClick={db.open(d,false)}) {Text(t("Open","باز کردن"))};TextButton(onClick={db.open(d,true)}) {Text(t("Share","اشتراک"))} }
                TextButton(onClick={action { db.delete(d.id) }}) {Text(t("Delete","حذف"))}
            }
        } }
    } }
}
