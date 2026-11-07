package com.plus.browser

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.util.Locale

internal data class ProTheme(val name:String,val primary:Color,val secondary:Color,val background:Color,val surface:Color,val variant:Color)
internal val proThemes=listOf(
    ProTheme("Midnight Gold",Color(0xffe4c786),Color(0xff9b8bd2),Color(0xff090e20),Color(0xff171a29),Color(0xff2b2935)),
    ProTheme("Royal Purple",Color(0xffd5b7ff),Color(0xffb3a4ff),Color(0xff130d21),Color(0xff231a35),Color(0xff34294a)),
    ProTheme("Electric Blue",Color(0xff82d7ff),Color(0xffa6a1ff),Color(0xff061322),Color(0xff142439),Color(0xff1f3450)),
    ProTheme("Silver Ice",Color(0xffc4e3f5),Color(0xffa9b8d0),Color(0xff101a23),Color(0xff1d2b35),Color(0xff2b3c48)),
    ProTheme("Dark Obsidian",Color(0xffd9d9e9),Color(0xffa79adb),Color(0xff0a0b10),Color(0xff171922),Color(0xff292b37)),
    ProTheme("Champagne Black",Color(0xffead2a1),Color(0xffb9a5df),Color(0xff100e11),Color(0xff211d20),Color(0xff352d30)))

internal data class ScanItem(val url:String,val name:String,val extension:String,val group:String?,val part:Int?,val size:Long?=null)
internal fun classifyDownload(url:String, provided:String="", size:Long?=null):ScanItem {
    val path=try { Uri.decode(Uri.parse(url).lastPathSegment.orEmpty()) } catch (_:Exception) { "" }
    val name=(provided.ifBlank { path.ifBlank { "download" } }).take(200)
    val ext=name.substringAfterLast('.', "").lowercase(Locale.ROOT).take(12)
    val part=parseMultipart(name)
    val folder=try { Uri.parse(url).let { "${it.scheme}://${it.authority}" + it.path.orEmpty().substringBeforeLast('/', "") } } catch (_:Exception) { "" }
    val group=part?.let { folder + "/" + it.groupName }
    return ScanItem(url,name,ext,group,part?.number,size)
}

private val scanScript="""(function(){
  var out=[], seen=new Map(), ext=/\.(zip|rar|7z|pdf|apk|mp4|mp3|m4a|mkv|avi|mov|webm|wav|flac|tar|gz|bz2|xz|iso|exe|dmg|docx?|xlsx?|pptx?|epub|csv|torrent|\d{3,})(?:[?#]|$)/i;
  function add(u,n,force,size){try{if(typeof u!=='string'||!u.trim())return;u=new URL(u,location.href).href;if(!/^https?:/i.test(u))return;if(seen.has(u)){if(size>0)out[seen.get(u)].size=size;return;}if(out.length>=500||(!force&&!ext.test(u)))return;seen.set(u,out.length);out.push({url:u,name:n||'',size:size||0});}catch(e){}}
  Array.prototype.slice.call(document.querySelectorAll('a[href],area[href],button[data-href],button[data-url],input[data-href],input[data-url],video[src],audio[src],source[src]'),0,3000).forEach(function(e){
    var u=e.href||e.src||e.dataset.href||e.dataset.url;var label=(e.getAttribute('aria-label')||'')+' '+(e.innerText||'')+' '+(e.getAttribute('title')||'');
    add(u,e.getAttribute('download')||'',e.hasAttribute('download')||/\bdownload\b/i.test(label)||label.indexOf('دانلود')>=0,0);
  });
  try{performance.getEntriesByType('resource').slice(0,3000).forEach(function(e){add(e.name,'',false,e.encodedBodySize||0)});}catch(e){}
  return JSON.stringify(out);
})()"""
internal fun scanPage(web:WebView?,result:(List<ScanItem>,String?)->Unit) {
    if(web==null || web.url.isNullOrBlank()) {result(emptyList(),"Open a webpage before scanning.");return}
    web.evaluateJavascript(scanScript) { raw ->
        try {
            val encoded=JSONTokener(raw).nextValue() as? String ?: "[]"
            val a=JSONArray(encoded); val list=(0 until a.length()).mapNotNull { i -> val o=a.optJSONObject(i) ?: return@mapNotNull null; val u=o.optString("url"); if(u.startsWith("https://")||u.startsWith("http://")) classifyDownload(u,o.optString("name"),o.optLong("size",0).takeIf { it>0 }) else null }.distinctBy { it.url }
            result(list,null)
        } catch(e:Exception){result(emptyList(),"Could not read this page: ${e.localizedMessage}")}
    }
}

@Composable internal fun ProScreen(usage:ProUsage,themeName:String,adBlockEnabled:Boolean,onAdBlockChange:(Boolean)->Unit,onSetDefault:()->Unit,onTheme:(String)->Unit,onScan:()->Unit,onDownloads:()->Unit, modifier:Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember {context.getSharedPreferences("plus_pro",Context.MODE_PRIVATE)}
    val gold=Color(0xffe9ca85)
    var taps by remember {mutableIntStateOf(0)};var firstTap by remember {mutableLongStateOf(0L)}
    var codeDialog by remember {mutableStateOf(false)}; var code by remember {mutableStateOf("")}; var invalid by remember {mutableStateOf(false)}
    var tapWindow by remember {mutableIntStateOf(prefs.getInt("tap_window",10).coerceIn(5,30))}
    var maxDownloads by remember {mutableIntStateOf(prefs.getInt("max_downloads",3).coerceIn(1,4))}
    LazyColumn(modifier=modifier.fillMaxWidth(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xff1c1b2a)),modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().clickable(enabled=DebugAccess.enabled) {
                val now=SystemClock.elapsedRealtime();if(now-firstTap>tapWindow*1000) {taps=0;firstTap=now};taps++;if(taps>=15){taps=0;codeDialog=true}
            }.padding(20.dp)) {
                Text("✦  PLUS PRO+",color=gold,fontSize=27.sp,fontWeight=FontWeight.Black);Text("More Power. More Freedom.",color=Color(0xffcbbca5))
                Spacer(Modifier.height(14.dp))
                if(usage.isDefault) Text("PRO+ UNLOCKED",color=gold,fontWeight=FontWeight.Bold)
                else {
                    Text("Make PLUS your default browser to unlock all five PRO+ features.",color=Color.White)
                    if(usage.status==BrowserDefaultStatus.UNAVAILABLE) Text("Your device may not support a default browser role; check Android settings.",color=Color(0xffcbbca5),fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick=onSetDefault,colors=ButtonDefaults.buttonColors(containerColor=gold,contentColor=Color(0xff171321))) { Text("SET AS DEFAULT BROWSER",fontWeight=FontWeight.Bold) }
                }
                if(usage.developer) Text("DEVELOPER TEST ACCESS",color=gold,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))
                AnimatedVisibility(visible=usage.unlocked) { Text("✧  More power, unlocked.",color=gold,modifier=Modifier.padding(top=8.dp)) }
            } }
        }
        item { FeatureCard("01","PLUS SMART DOWNLOADER","Scan page links and manage downloads.",if(usage.unlocked) "ACTIVE" else "LOCKED") {
            Button(onClick=onScan,enabled=usage.unlocked) {Text("SCAN PAGE FOR DOWNLOADS")}; TextButton(onClick=onDownloads) {Text("Open Downloads")}
            if(usage.unlocked) { Text("Simultaneous downloads: $maxDownloads",fontSize=13.sp);Row { (1..4).forEach { n-> TextButton(onClick={maxDownloads=n;prefs.edit().putInt("max_downloads",n).apply()}) {Text(if(n==maxDownloads) "[$n]" else "$n")} } } }
        } }
        item { FeatureCard("02","PLUS AI","Unavailable: no on-device model is installed or integrated. No simulated answers are provided.","UNAVAILABLE") {} }
        item { FeatureCard("03","PLUS VAULT","Unavailable: secure vault and biometric protection are not implemented. Do not store sensitive content here.","UNAVAILABLE") {} }
        item { FeatureCard("04","PLUS BUTTER THEMES","Six persistent, PLUS-inspired interface palettes.",if(usage.unlocked) "ACTIVE" else "LOCKED") {
            if(usage.unlocked) proThemes.forEach { theme -> Row(Modifier.fillMaxWidth().clickable {onTheme(theme.name)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(22.dp).background(theme.background, RoundedCornerShape(6.dp)).border(2.dp,theme.primary,RoundedCornerShape(6.dp)));Spacer(Modifier.width(10.dp)); Text(theme.name,Modifier.weight(1f)); if(theme.name==themeName) Text("✓",color=gold) } }
        } }
        item { FeatureCard("05","PLUS DEEP FREEZE","Unavailable: safe hibernation of active media and critical page operations is not implemented.","UNAVAILABLE") {} }
        item { FeatureCard("06","PLUS AD BLOCKER","FREE for every user. No PLUS PRO+ unlock or paid subscription is required. Blocks known ad and tracker domains; some first-party or embedded ads may still appear.",if(adBlockEnabled) "FREE · ACTIVE" else "FREE · OFF") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(adBlockEnabled) "Ad and tracker blocking is enabled" else "Ad and tracker blocking is disabled",Modifier.weight(1f),fontSize=13.sp,color=Color(0xffc5c7d2))
                Switch(checked=adBlockEnabled,onCheckedChange=onAdBlockChange)
            }
        } }
        if(DebugAccess.enabled && usage.developer) item { TextButton(onClick={usage.revokeDeveloper()}) {Text("Revoke developer test access")};Text("Developer tap window: $tapWindow seconds",fontSize=12.sp); Row {listOf(5,10,20,30).forEach { n->TextButton(onClick={tapWindow=n;prefs.edit().putInt("tap_window",n).apply()}) {Text("${n}s")} } } }
    }
    if(DebugAccess.enabled && codeDialog) AlertDialog(onDismissRequest={codeDialog=false;code=""},title={Text("Developer test access")},text={Column {OutlinedTextField(value=code,onValueChange={code=it},singleLine=true,label={Text("Developer code")});if(invalid)Text("Invalid code",color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={if(DebugAccess.verify(code)){usage.developerUnlock();codeDialog=false;code="";invalid=false}else invalid=true}){Text("Unlock")}},dismissButton={TextButton(onClick={codeDialog=false;code=""}){Text("Cancel")}})
}
@Composable private fun FeatureCard(number:String,title:String,description:String,status:String,content:@Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xff1b1e30)),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment=Alignment.CenterVertically){ Text(number,color=Color(0xffd5b979),fontWeight=FontWeight.Bold); Spacer(Modifier.width(10.dp));Text(title,Modifier.weight(1f),color=Color(0xfff4e3be),fontWeight=FontWeight.Bold);Text(status,fontSize=10.sp,color=Color(0xffb8accb)) };Spacer(Modifier.height(7.dp));Text(description,fontSize=13.sp,color=Color(0xffc5c7d2));Spacer(Modifier.height(6.dp));content() } }
}

@Composable internal fun ScanResults(items:List<ScanItem>,error:String?,agent:String,onDownloads:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    val selected=remember(items) { mutableStateMapOf<String,Boolean>().apply { items.forEach { put(it.url,false) } } }
    var message by remember {mutableStateOf("")}
    fun enqueue(list:List<ScanItem>) { if(list.isEmpty()) return; scope.launch { val result=withContext(Dispatchers.IO) { var added=0;var failed=0;list.forEach { item -> try {Downloads.get(context).add(item.url,agent,null,null,CookieManager.getInstance().getCookie(item.url));added++}catch(_:Exception){failed++} };added to failed }; message="${result.first} queued, ${result.second} not queued (duplicate or error)." } }
    val groups=remember(items) { items.filter{it.group!=null && it.part!=null}.groupBy { it.group!! }.filterValues { it.size>=2 } }
    val groupedUrls=remember(groups) { groups.values.flatten().map{it.url}.toSet() }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Text("Candidates only: links are not verified. Login, scripts, protected content, blob URLs and cross-frame links may be inaccessible. Resource byte counts are shown when exposed; file sizes may be unknown until transfer.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant); if(error!=null) Text(error,color=MaterialTheme.colorScheme.error);if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.primary) }
        item {Column { Row {TextButton(onClick={items.forEach{selected[it.url]=true}}){Text("Select all")};TextButton(onClick={enqueue(items.filter{selected[it.url]==true})}) {Text("Download selected")}};TextButton(onClick=onDownloads){Text("Open Downloads")} } }
        if(items.isEmpty()) item {Text("No candidates detected.")}
        groups.forEach { (group,parts) ->
            val sorted=parts.sortedBy{it.part}; val numbers=sorted.mapNotNull{it.part}.toSet(); val min=if(numbers.contains(0)) 0 else 1; val max=numbers.maxOrNull() ?: min; val missing=if(max-min<=500) (min..max).filter {it !in numbers} else emptyList()
            item(key="group:$group") {Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {Text("Collection: $group",fontWeight=FontWeight.Bold);Text(if(max-min>500) "Part range too large to check" else if(missing.isNotEmpty()) "Missing parts: ${missing.joinToString()}" else "Parts appear consecutive",fontSize=12.sp); TextButton(onClick={enqueue(sorted)}){Text("Download collection")} } } }
            items(sorted,key={it.url}) {item->ScanRow(item,selected[item.url]==true,{selected[item.url]=it},{enqueue(listOf(item))})}
        }
        val singles=items.filter{it.url !in groupedUrls};if(singles.isNotEmpty()) item {Text("Other candidates",fontWeight=FontWeight.Bold)}
        items(singles,key={it.url}) {item->ScanRow(item,selected[item.url]==true,{selected[item.url]=it},{enqueue(listOf(item))})}
    }
}
@Composable private fun ScanRow(item:ScanItem,checked:Boolean,onCheck:(Boolean)->Unit,onDownload:()->Unit) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=checked,onCheckedChange=onCheck);Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.SemiBold,maxLines=2);Text("${item.extension.ifBlank { "unknown" }} · "+(item.size?.let { "resource: $it bytes" } ?: "size unknown")+(item.part?.let {" · part $it"} ?: ""),fontSize=12.sp);Text(item.url,maxLines=2,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick=onDownload){Text("Get")} } } }
