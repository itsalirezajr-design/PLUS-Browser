package com.plus.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.app.role.RoleManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.core.view.WindowCompat
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class Page(val title:String,val url:String)
private data class SpeedDial(val name:String,val url:String)
private class Tab(val id:Int, val privateMode:Boolean) {
    var web: WebView? = null
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var error by mutableStateOf(false)
    var back by mutableStateOf(false)
    var forward by mutableStateOf(false)
}
private class Store(context:Context) {
    val prefs=context.getSharedPreferences("plus", Context.MODE_PRIVATE)
    fun get(key:String):List<Page> = try { val a=JSONArray(prefs.getString(key,"[]")); (0 until a.length()).map { a.getJSONObject(it).let { o -> Page(o.optString("title"),o.getString("url")) } } } catch (_:Exception) { emptyList() }
    fun save(key:String, list:List<Page>) { val a=JSONArray(); list.take(200).forEach { a.put(JSONObject().put("title",it.title).put("url",it.url)) }; prefs.edit().putString(key,a.toString()).apply() }
    fun getSpeedDials():List<SpeedDial> = try { val a=JSONArray(prefs.getString("speed_dials","[]")); (0 until a.length()).map { a.getJSONObject(it).let { o -> SpeedDial(o.optString("name"),o.getString("url")) } } } catch (_:Exception) { emptyList() }
    fun saveSpeedDials(items:List<SpeedDial>) { val a=JSONArray(); items.take(24).forEach { a.put(JSONObject().put("name",it.name).put("url",it.url)) }; prefs.edit().putString("speed_dials",a.toString()).apply() }
}
class MainActivity:ComponentActivity() {
    private lateinit var usage:ProUsage
    private lateinit var fullscreen:FullscreenVideo
    private val browserRoleRequest=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if(::usage.isInitialized) usage.refresh() }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); AdBlocker.initialize(applicationContext); usage=ProUsage(this); fullscreen=FullscreenVideo(this); WindowCompat.setDecorFitsSystemWindows(window,false); setContent { Browser(intent?.dataString,usage,::requestDefaultBrowser,fullscreen) } }
    override fun onResume() { super.onResume(); if(::usage.isInitialized) usage.refresh() }
    override fun onDestroy() { if(::fullscreen.isInitialized) fullscreen.hide(); super.onDestroy() }
    private fun requestDefaultBrowser() {
        val roleIntent=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
            try { getSystemService(RoleManager::class.java)?.let { if(it.isRoleAvailable(RoleManager.ROLE_BROWSER)) it.createRequestRoleIntent(RoleManager.ROLE_BROWSER) else null } } catch (_:Exception) { null }
        } else null
        val candidates=listOfNotNull(roleIntent, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for(intent in candidates) {
            try { browserRoleRequest.launch(intent); return } catch (_:Exception) { /* Try the next system settings screen. */ }
        }
        Toast.makeText(this,"Default browser settings are not available on this device.",Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Browser(initialUrl:String?, usage:ProUsage, onSetDefault:()->Unit, fullscreen:FullscreenVideo) {
    val context=LocalContext.current
    val store=remember { Store(context) }
    var adBlockEnabled by remember { mutableStateOf(AdBlocker.isEnabled()) }
    val scope=rememberCoroutineScope()
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var dark by remember { mutableStateOf(store.prefs.getBoolean("dark",true)) }
    var thirdParty by remember { mutableStateOf(store.prefs.getBoolean("thirdParty",false)) }
    var engine by remember { mutableStateOf(store.prefs.getString("engine","Google") ?: "Google") }
    val isFa=Locale.getDefault().language == "fa"
    fun t(en:String, fa:String)=if(isFa) fa else en
    var proTheme by remember { mutableStateOf(context.getSharedPreferences("plus_pro",Context.MODE_PRIVATE).getString("theme","") ?: "") }
    val palette=proThemes.firstOrNull { it.name==proTheme && usage.unlocked && dark }
    val colors=if(palette!=null) darkColorScheme(primary=palette.primary,secondary=palette.secondary,background=palette.background,surface=palette.surface,surfaceVariant=palette.variant) else if(dark) darkColorScheme(primary=Color(0xff80caff), secondary=Color(0xffb9a4ff), background=Color(0xff080e20), surface=Color(0xff141e35), surfaceVariant=Color(0xff202d49)) else lightColorScheme(primary=Color(0xff315eb9), secondary=Color(0xff6950a1), background=Color(0xfff5f7ff), surface=Color.White, surfaceVariant=Color(0xffe6ecfa))
    val savedSession=remember { try { JSONObject(store.prefs.getString("session", "{}") ?: "{}") } catch (_:Exception) { JSONObject() } }
    val tabs=remember { mutableStateListOf<Tab>().apply { try { val a=savedSession.optJSONArray("tabs") ?: JSONArray(); for(i in 0 until a.length()) { val o=a.getJSONObject(i); add(Tab(o.getInt("id"),false).apply { url=o.optString("url"); title=o.optString("title") }) } } catch (_:Exception) { clear() }; if(isEmpty()) add(Tab(0,false)) } }
    var nextId by remember { mutableIntStateOf((tabs.maxOfOrNull { it.id } ?: 0)+1) }
    var activeId by remember { mutableIntStateOf(savedSession.optInt("active",tabs.first().id).let { id -> if(tabs.any{it.id==id}) id else tabs.first().id }) }
    var lastNormalId by remember { mutableIntStateOf(activeId) }
    LaunchedEffect(activeId) { if(tabs.any { it.id==activeId && !it.privateMode }) lastNormalId=activeId }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val normal=tabs.filter { !it.privateMode }
            val a=JSONArray(); normal.forEach { a.put(JSONObject().put("id",it.id).put("url",it.url).put("title",it.title)) }
            val selected=normal.firstOrNull { it.id==activeId }?.id ?: normal.firstOrNull { it.id==lastNormalId }?.id ?: normal.lastOrNull()?.id ?: -1
            JSONObject().put("tabs",a).put("active",selected).toString()
        }.collect { serialized -> withContext(Dispatchers.IO) { store.prefs.edit().putString("session",serialized).commit() } }
    }
    val tab=tabs.firstOrNull { it.id==activeId } ?: tabs.first()
    fun applyAdBlockSetting(enabled:Boolean) {
        val changed = AdBlocker.isEnabled() != enabled
        val saved = AdBlocker.setEnabled(context, enabled)
        adBlockEnabled = AdBlocker.isEnabled()
        if (changed && tab.url.isNotBlank()) {
            tab.error = false
            tab.web?.reload()
        }
        val message = when {
            !saved -> t("Could not save ad blocker setting","ذخیره وضعیت مسدودکننده تبلیغات ناموفق بود")
            enabled -> t("Ad blocker ON · page reloaded","مسدودکننده روشن شد؛ صفحه دوباره بارگیری شد")
            else -> t("Ad blocker OFF · page reloaded","مسدودکننده خاموش شد؛ صفحه دوباره بارگیری شد")
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    var screen by remember { mutableStateOf("browser") }
    var address by remember(tab.id) { mutableStateOf(tab.url) }
    var editing by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(store.get("bookmarks")) }
    var history by remember { mutableStateOf(store.get("history")) }
    var speedDials by remember { mutableStateOf(store.getSpeedDials().ifEmpty { listOf(SpeedDial("Google","https://www.google.com"),SpeedDial("YouTube","https://www.youtube.com"),SpeedDial("Wikipedia","https://www.wikipedia.org")) }) }
    var showSpeedDialDialog by remember { mutableStateOf(false) }
    var editingSpeedDial by remember { mutableStateOf<SpeedDial?>(null) }
    var speedDialName by remember { mutableStateOf("") }
    var speedDialUrl by remember { mutableStateOf("") }
    var scanItems by remember { mutableStateOf(emptyList<ScanItem>()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanAgent by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var qrResult by remember { mutableStateOf<String?>(null) }
    val qrLauncher=rememberLauncherForActivityResult(ScanContract()) { result ->
        if(result.contents!=null) qrResult=result.contents
    }
    fun addTab(privateMode:Boolean) { val n=Tab(nextId++, privateMode); tabs.add(n); activeId=n.id; address=""; screen="browser" }
    fun closeTab(item:Tab) { tabs.remove(item); if(tabs.isEmpty()) { val n=Tab(nextId++,false); tabs.add(n) }; if(activeId==item.id) activeId=tabs.last().id }
    fun openUrl(url:String) { tab.error=false;tab.url=url;address=url;screen="browser";editing=false;keyboard?.hide();focusManager.clearFocus();tab.web?.loadUrl(url) }
    fun go(input:String) {
        val value=input.trim(); if(value.isBlank()) return
        val isUrl=value.startsWith("http://",true)||value.startsWith("https://",true)||(!value.contains(' ') && (value.contains('.') || value.startsWith("localhost")))
        if(isUrl) openUrl(if(value.startsWith("http://",true)||value.startsWith("https://",true)) value else "https://$value")
        else {searchQuery=value;screen="search";editing=false;keyboard?.hide();focusManager.clearFocus()}
    }
    fun home() { tab.web?.stopLoading(); tab.url=""; tab.title=""; tab.error=false; address=""; screen="browser" }
    fun bookmark() { if(tab.url.isNotBlank()) { bookmarks=if(bookmarks.any{it.url==tab.url}) bookmarks.filterNot{it.url==tab.url} else listOf(Page(tab.title.ifBlank{tab.url},tab.url))+bookmarks; store.save("bookmarks",bookmarks) } }
    fun persistSpeedDials(items:List<SpeedDial>) { speedDials=items; store.saveSpeedDials(items) }
    fun editSpeedDial(item:SpeedDial?=null) { editingSpeedDial=item; speedDialName=item?.name ?: ""; speedDialUrl=item?.url ?: ""; showSpeedDialDialog=true }
    fun saveSpeedDial() { val name=speedDialName.trim(); var url=speedDialUrl.trim(); if(name.isBlank() || url.isBlank()) { Toast.makeText(context,t("Enter a name and website address","نام و آدرس سایت را وارد کنید"),Toast.LENGTH_SHORT).show(); return }; if(!url.startsWith("https://",true) && !url.startsWith("http://",true)) url="https://$url"; if(!android.util.Patterns.WEB_URL.matcher(url).matches()) { Toast.makeText(context,t("Enter a valid website address","آدرس سایت معتبر وارد کنید"),Toast.LENGTH_SHORT).show(); return }; val updated=speedDials.filterNot { it.url==editingSpeedDial?.url || it.url.equals(url,true) } + SpeedDial(name,url); persistSpeedDials(updated); showSpeedDialDialog=false }
    fun updateHistory(item:Tab,url:String,title:String) { if(!item.privateMode && (url.startsWith("https://")||url.startsWith("http://"))) { history=listOf(Page(title.ifBlank { url },url))+history.filterNot{it.url==url}; history=history.take(200); store.save("history",history) } }
    LaunchedEffect(tab.id,tab.url,editing) { if(!editing) address=tab.url }
    LaunchedEffect(tab.id) { editing=false }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestTab by rememberUpdatedState(tab)
    val latestScreen by rememberUpdatedState(screen)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_PAUSE) tabs.forEach { it.web?.onPause() }
            if(event==Lifecycle.Event.ON_RESUME && latestScreen=="browser") latestTab.web?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activeId,screen) {
        if(screen!="browser") { keyboard?.hide(); focusManager.clearFocus() }
        tabs.forEach { if(it.id==activeId && screen=="browser") it.web?.onResume() else it.web?.onPause() }
    }
    LaunchedEffect(initialUrl) { if(!initialUrl.isNullOrBlank()) go(initialUrl) }
    BackHandler(enabled=fullscreen.isShowing || screen!="browser" || tab.back || tab.url.isNotEmpty()) { if(fullscreen.isShowing) fullscreen.hide() else if(screen!="browser") screen="browser" else if(tab.web?.canGoBack()==true) tab.web?.goBack() else home() }
    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Column(Modifier.fillMaxSize().then(if(screen!="browser") Modifier.clearAndSetSemantics {} else Modifier)) {
                    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={searchQuery="";screen="search"},modifier=Modifier.size(44.dp).semantics { contentDescription=if(tab.privateMode) "Private tab. Open PLUS Search" else "Open PLUS Search" }) { Box(Modifier.size(30.dp)) { PlusMark(Modifier.size(27.dp)); if(tab.privateMode) Box(Modifier.align(Alignment.BottomEnd).size(7.dp).background(Color(0xffba8bff),RoundedCornerShape(50))) } }
                        BasicTextField(value=address,onValueChange={address=it;editing=true},singleLine=true,textStyle=TextStyle(color=MaterialTheme.colorScheme.onSurface,fontSize=14.sp),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={go(address)}),modifier=Modifier.weight(1f).height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).semantics { contentDescription=if(isFa) "جستجو یا آدرس" else "Search or address" },decorationBox={inner ->
                            Row(Modifier.fillMaxSize().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("⌕",fontSize=20.sp,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(7.dp));Box(Modifier.weight(1f)) { if(address.isEmpty()) Text(t("Search or address","جستجو یا آدرس"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1);inner() }
                                if(address.isNotEmpty() && editing) IconButton(onClick={address=""},modifier=Modifier.size(36.dp)) { Text("×",fontSize=22.sp) }
                            }
                        })
                        IconButton(onClick={ applyAdBlockSetting(!AdBlocker.isEnabled()) },modifier=Modifier.size(40.dp).semantics { contentDescription=if(adBlockEnabled) "Ad blocker enabled. Tap to disable and reload page." else "Ad blocker disabled. Tap to enable and reload page." }) { Text("🛡",fontSize=19.sp,color=if(adBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick={ qrLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(t("Scan a QR code","کد QR را اسکن کنید")).setBeepEnabled(false).setOrientationLocked(false)) },modifier=Modifier.size(44.dp)) { Text("▦",fontSize=22.sp,color=MaterialTheme.colorScheme.primary) }
                        IconButton(onClick={screen="tabs"},modifier=Modifier.size(44.dp)) { BadgedBox(badge={ Badge { Text("${tabs.size}") } }) { Text("▣", fontSize=22.sp) } }
                        PlusOverflowMenu(usage,isFa,onOpen={keyboard?.hide();focusManager.clearFocus()},onAction={ action -> when(action) {"new"->addTab(false);"private"->addTab(true);else->screen=action} })
                    }
                    PageProgress(tab)
                    Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        tabs.forEach { item -> key(item.id) {
                            if(item.web!=null || (item.id==activeId && item.url.isNotBlank())) BrowserWebView(item, item.id==activeId && item.url.isNotBlank(), thirdParty, MaterialTheme.colorScheme.background.toArgb(), fullscreen, { url,title -> updateHistory(item,url,title) }, { url,agent,disposition,mime ->
                                scope.launch(Dispatchers.IO) {
                                    try { Downloads.get(context).add(url,agent,disposition,mime,CookieManager.getInstance().getCookie(url)); withContext(Dispatchers.Main) { Toast.makeText(context,if(isFa) "دانلود آغاز شد" else "Download started",Toast.LENGTH_SHORT).show() } }
                                    catch(e:Exception) { withContext(Dispatchers.Main) { Toast.makeText(context,"Download failed: ${e.message}",Toast.LENGTH_LONG).show() } }
                                }
                            })
                        } }
                        if(tab.url.isBlank()) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))).padding(20.dp)) {
                                Spacer(Modifier.height(28.dp)); Text(if(tab.privateMode) t("A quieter space.","فضایی خصوصی‌تر.") else t("Explore more.","بیشتر کاوش کنید."),fontSize=32.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(8.dp)); Text(if(tab.privateMode) t("Pages visited in this tab are not saved in PLUS history. WebView cookies and site data are shared with normal tabs; this is not full private isolation.","صفحات این زبانه در تاریخچه ذخیره نمی‌شوند. کوکی‌ها و داده‌های وب با زبانه‌های عادی مشترک هستند؛ این حالت کاملاً ایزوله نیست.") else t("Your gateway to the web, beautifully simple.","دروازه‌ای ساده و زیبا به اینترنت."),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick={searchQuery="";screen="search"}) { Text("⌕  PLUS SEARCH") }
                                Spacer(Modifier.height(22.dp)); HomeProCard(usage,isFa,onOpen={screen="pro"})
                                Spacer(Modifier.height(24.dp)); Text(t("QUICK ACCESS","دسترسی سریع"),fontSize=11.sp,letterSpacing=1.5.sp,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(t("Your favorite sites","سایت‌های محبوب شما"),fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick={editSpeedDial()}) { Text("+  "+t("Add","افزودن")) } }
                                speedDials.chunked(3).forEach { group -> Row(horizontalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxWidth()) { group.forEach { item -> Card(onClick={go(item.url)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) { Box(Modifier.size(38.dp).background(Brush.linearGradient(listOf(Color(0xff536dfe),Color(0xff9b68e8))),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) { Text(item.name.take(1).uppercase(),fontSize=19.sp,color=Color.White,fontWeight=FontWeight.Bold) }; Spacer(Modifier.height(7.dp)); Text(item.name,fontSize=11.sp,maxLines=1); Row { TextButton(onClick={editSpeedDial(item)},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) { Text(t("Edit","ویرایش"),fontSize=10.sp) }; TextButton(onClick={persistSpeedDials(speedDials.filterNot{it.url==item.url})},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) { Text("×",fontSize=14.sp,color=MaterialTheme.colorScheme.error) } } } } }; if(group.size<3) repeat(3-group.size) { Spacer(Modifier.weight(1f)) } } }
                                Spacer(Modifier.height(30.dp)); Row(verticalAlignment=Alignment.CenterVertically) { Text(t("Bookmarks","نشانک‌ها"),fontWeight=FontWeight.Bold,fontSize=19.sp,modifier=Modifier.weight(1f)); TextButton(onClick={screen="bookmarks"}) { Text(t("See all","همه")) } }
                                if(bookmarks.isEmpty()) Text(t("Save a page to find it here.","صفحه‌ای را ذخیره کنید تا اینجا ببینید."),color=MaterialTheme.colorScheme.onSurfaceVariant) else bookmarks.take(3).forEach { p -> PageRow(p,{go(p.url)},null) }
                                if(!tab.privateMode) { Spacer(Modifier.height(22.dp)); Text(t("Recent","اخیر"),fontWeight=FontWeight.Bold,fontSize=19.sp); if(history.isEmpty()) Text(t("Your recent pages will appear here.","صفحات اخیر شما اینجا نمایش داده می‌شوند."),color=MaterialTheme.colorScheme.onSurfaceVariant) else history.take(3).forEach { p->PageRow(p,{go(p.url)},null) } }
                            }
                        } else {
                            if(tab.error) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Text("☁",fontSize=48.sp,color=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(18.dp)); Text(t("Couldn’t load this page","صفحه بارگیری نشد"),fontSize=22.sp,fontWeight=FontWeight.Bold); Text(t("Check your connection and try again.","اتصال خود را بررسی کرده و دوباره تلاش کنید.")); Spacer(Modifier.height(16.dp)); Button(onClick={tab.error=false;tab.web?.reload()}) { Text(t("Try again","تلاش دوباره")) } }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().height(64.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={tab.web?.goBack()},enabled=tab.back) { Text("‹", fontSize=30.sp) }
                        IconButton(onClick={tab.web?.goForward()},enabled=tab.forward) { Text("›", fontSize=30.sp) }
                        IconButton(onClick={home()}) { Text("⌂", fontSize=26.sp) }
                        ReloadButton(tab)
                        IconButton(onClick={bookmark()},enabled=tab.url.isNotBlank()) { Text(if(bookmarks.any{it.url==tab.url}) "★" else "☆",fontSize=24.sp) }
                    }
                }
                if(screen!="browser") {
                  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent(PointerEventPass.Final) } } }) {
                    Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={screen="browser"}) { Text("‹", fontSize=30.sp) }; Text(when(screen){"tabs"->t("Tabs","زبانه‌ها");"bookmarks"->t("Bookmarks","نشانک‌ها");"history"->t("History","تاریخچه");"downloads"->t("Downloads","دانلودها");"search"->"PLUS SEARCH";"pro"->"PLUS PRO+";"detected"->"DETECTED DOWNLOADS";"settings"->t("Settings","تنظیمات");else->t("About PLUS","درباره PLUS")},fontSize=22.sp,fontWeight=FontWeight.Bold) }
                    when(screen) {
                        "search" -> PlusSearchScreen(searchQuery,{searchQuery=it},engine,onSearch={openUrl(it)},modifier=Modifier.weight(1f))
                        "downloads" -> DownloadsScreen()
                        "pro" -> ProScreen(usage,proTheme,adBlockEnabled=adBlockEnabled,onAdBlockChange={ enabled -> applyAdBlockSetting(enabled) },onSetDefault=onSetDefault,onTheme={ selected -> proTheme=selected;dark=true;store.prefs.edit().putBoolean("dark",true).apply();context.getSharedPreferences("plus_pro",Context.MODE_PRIVATE).edit().putString("theme",selected).apply() },onScan={
                            if(usage.unlocked) { scanAgent=tab.web?.settings?.userAgentString ?: "";scanPage(tab.web) { found,error -> scanItems=found;scanError=error;screen="detected" } }
                        },onDownloads={screen="downloads"},modifier=Modifier.weight(1f))
                        "detected" -> if(usage.unlocked) ScanResults(scanItems,scanError,scanAgent,onDownloads={screen="downloads"}) else Text("PLUS PRO+ is locked.",Modifier.padding(20.dp))
                        "tabs" -> Column(Modifier.fillMaxSize().padding(16.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={addTab(false)}) { Text(t("+ New tab","+ زبانه جدید")) }; OutlinedButton(onClick={addTab(true)}) { Text(t("+ Private","+ خصوصی")) } }; Spacer(Modifier.height(12.dp)); LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(tabs.toList(),key={it.id}) { item -> Card(onClick={activeId=item.id;screen="browser"},modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(if(item.privateMode) "◈" else "◎",fontSize=24.sp,color=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title.ifBlank{if(item.privateMode)t("Private tab","زبانه خصوصی") else t("New tab","زبانه جدید")},maxLines=1,fontWeight=FontWeight.SemiBold); Text(item.url.ifBlank { t("Home","خانه") },maxLines=1,fontSize=12.sp) }; IconButton(onClick={closeTab(item)}) { Text("×",fontSize=24.sp) } } } } } }
                        "bookmarks", "history" -> { val isBook=screen=="bookmarks"; val pages=if(isBook)bookmarks else history
                            Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) { if(!isBook && pages.isNotEmpty()) TextButton(onClick={history=emptyList();store.save("history",history)}) { Text(t("Clear history","پاک کردن تاریخچه")) }; if(pages.isEmpty()) Text(t("Nothing here yet.","هنوز موردی وجود ندارد."),modifier=Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn { items(pages,key={it.url}) { p -> PageRow(p,{go(p.url)},{ if(isBook) { bookmarks=bookmarks.filterNot{it.url==p.url};store.save("bookmarks",bookmarks) } else {history=history.filterNot{it.url==p.url};store.save("history",history)} }) } } }
                        }
                        "settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            Text(t("APPEARANCE","ظاهر"),color=MaterialTheme.colorScheme.secondary,fontSize=12.sp,letterSpacing=1.sp); Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(t("Dark theme","پوسته تیره"),modifier=Modifier.weight(1f)); Switch(checked=dark,onCheckedChange={dark=it;store.prefs.edit().putBoolean("dark",it).apply()}) }
                            Spacer(Modifier.height(22.dp)); Text(t("SEARCH ENGINE","موتور جستجو"),color=MaterialTheme.colorScheme.secondary,fontSize=12.sp,letterSpacing=1.sp); listOf("Google","DuckDuckGo","Bing").forEach { name -> Row(Modifier.fillMaxWidth().clickable { engine=name;store.prefs.edit().putString("engine",name).apply() },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=engine==name,onClick={engine=name;store.prefs.edit().putString("engine",name).apply()}); Text(name) } }
                            Spacer(Modifier.height(22.dp)); Text(t("PRIVACY","حریم خصوصی"),color=MaterialTheme.colorScheme.secondary,fontSize=12.sp,letterSpacing=1.sp); Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t("Block ads and trackers","مسدودسازی تبلیغات و ردیاب‌ها")); Text(t("Uses a maintained host filter list","با فهرست فیلتر به‌روز"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked=adBlockEnabled,onCheckedChange={applyAdBlockSetting(it)}) }; Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t("Allow third-party cookies","اجازه کوکی‌های شخص ثالث")); Text(t("Off by default", "به طور پیش‌فرض خاموش"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked=thirdParty,onCheckedChange={thirdParty=it;store.prefs.edit().putBoolean("thirdParty",it).apply()}) }
                            TextButton(onClick={history=emptyList();store.save("history",history)}) { Text(t("Clear browsing history","پاک کردن تاریخچه مرور")) }
                            TextButton(onClick={CookieManager.getInstance().removeAllCookies { scope.launch(Dispatchers.IO) { CookieManager.getInstance().flush() } };WebStorage.getInstance().deleteAllData();tabs.forEach { it.web?.clearCache(true) };Toast.makeText(context,"Site data cleared",Toast.LENGTH_SHORT).show()}) { Text(t("Clear cookies and site data","پاک کردن کوکی‌ها و داده‌های سایت")) }
                            Text(t("Private tabs omit PLUS history, but Android WebView shares cookies and site storage across tabs. Downloads and websites may still retain information.","زبانه‌های خصوصی در تاریخچه PLUS ذخیره نمی‌شوند، اما کوکی‌ها و داده‌های سایت در WebView مشترک هستند. دانلودها و وب‌سایت‌ها ممکن است اطلاعات را حفظ کنند."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Column(Modifier.fillMaxWidth().padding(24.dp)) { Text("✦  PLUS",fontSize=36.sp,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary); Text("More Than Apps.",fontSize=17.sp,color=MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(28.dp)); Text("PLUS Browser 1.0",fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(t("A native Android browser powered by Android WebView.","مرورگر بومی اندروید با فناوری Android WebView.")) }
                    }
                  }
                }
                if(qrResult!=null) {
                    val scanned=qrResult!!.trim()
                    val qrUrl=when {
                        scanned.startsWith("https://",true) || scanned.startsWith("http://",true) -> scanned
                        scanned.startsWith("www.",true) && android.util.Patterns.WEB_URL.matcher(scanned).matches() -> "https://$scanned"
                        android.util.Patterns.WEB_URL.matcher(scanned).matches() -> "https://$scanned"
                        else -> null
                    }
                    AlertDialog(
                        onDismissRequest={qrResult=null},
                        title={Text(t("QR code detected","کد QR شناسایی شد"))},
                        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(if(qrUrl!=null) t("A website link was found. Review it before opening.","یک لینک وب پیدا شد. پیش از بازکردن آن را بررسی کنید.") else t("The QR code contains this text:","محتوای این کد QR:"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(scanned, color=MaterialTheme.colorScheme.onSurface, fontSize=14.sp)
                        }},
                        confirmButton={
                            if(qrUrl!=null) TextButton(onClick={qrResult=null;openUrl(qrUrl)}) { Text(t("Open in PLUS","بازکردن در PLUS")) }
                            else TextButton(onClick={ val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PLUS QR",scanned)); Toast.makeText(context,t("Copied","کپی شد"),Toast.LENGTH_SHORT).show(); qrResult=null }) { Text(t("Copy text","کپی متن")) }
                        },
                        dismissButton={TextButton(onClick={qrResult=null}) { Text(t("Close","بستن")) }}
                    )
                }
                if(showSpeedDialDialog) {
                    AlertDialog(
                        onDismissRequest={showSpeedDialDialog=false},
                        title={Text(if(editingSpeedDial==null) t("Add a site","افزودن سایت") else t("Edit site","ویرایش سایت"))},
                        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value=speedDialName,onValueChange={speedDialName=it},singleLine=true,label={Text(t("Site name","نام سایت"))})
                            OutlinedTextField(value=speedDialUrl,onValueChange={speedDialUrl=it},singleLine=true,label={Text(t("Website address","آدرس سایت"))},placeholder={Text("https://example.com")})
                        }},
                        confirmButton={TextButton(onClick={saveSpeedDial()}) { Text(t("Save","ذخیره")) }},
                        dismissButton={TextButton(onClick={showSpeedDialDialog=false}) { Text(t("Cancel","لغو")) }}
                    )
                }
            }
        }
    }
}
@Composable private fun PageRow(page:Page,open:()->Unit,remove:(()->Unit)?) { Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(vertical=10.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) { Text("◎",fontSize=24.sp,color=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(page.title,maxLines=1,fontWeight=FontWeight.Medium); Text(page.url,maxLines=1,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; if(remove!=null) IconButton(onClick=remove) { Text("×",fontSize=24.sp) } } }

/** Each tab owns one WebView for its lifetime. Stable keyed nodes keep it attached while
 * tabs or browser panels are switched, preserving renderer state and scroll position. */
@Composable
private fun BrowserWebView(
    tab: Tab,
    visible: Boolean,
    thirdParty: Boolean,
    background: Int,
    fullscreen: FullscreenVideo,
    onVisit: (String, String) -> Unit,
    onDownload: (String, String?, String?, String?) -> Unit
) {
    val context=LocalContext.current
    val latestVisible by rememberUpdatedState(visible)
    val latestVisit by rememberUpdatedState(onVisit)
    val latestDownload by rememberUpdatedState(onDownload)
    DisposableEffect(tab) {
        onDispose {
            tab.web?.let { w ->
                fullscreen.release(w)
                (w.parent as? ViewGroup)?.removeView(w)
                w.stopLoading()
                w.webChromeClient=null
                w.webViewClient=WebViewClient()
                w.destroy()
            }
            tab.web=null
        }
    }
    AndroidView(modifier=Modifier.fillMaxSize(),factory={ ctx ->
        WebView(ctx).also { w ->
            tab.web=w
            w.setBackgroundColor(background)
            w.visibility=if(visible) View.VISIBLE else View.INVISIBLE
            w.settings.javaScriptEnabled=true
            w.settings.domStorageEnabled=true
            w.settings.cacheMode=if(tab.privateMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            w.settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(w,thirdParty && !tab.privateMode)
            w.webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val uri=request.url
                    if(uri.scheme=="http"||uri.scheme=="https") return false
                    return try { context.startActivity(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)); true }
                    catch(_:Exception) { Toast.makeText(context,"No app can open this link",Toast.LENGTH_SHORT).show(); true }
                }
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                    return AdBlocker.intercept(request) ?: super.shouldInterceptRequest(view,request)
                }
                override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?) {
                    tab.error=false; tab.url=url ?: ""; tab.back=view.canGoBack(); tab.forward=view.canGoForward()
                }
                override fun onPageFinished(view:WebView,url:String?) {
                    tab.title=view.title ?: ""; tab.back=view.canGoBack(); tab.forward=view.canGoForward()
                    if(!tab.error && url!=null) latestVisit(url,tab.title)
                }
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame) tab.error=true }
            }
            w.webChromeClient=object:WebChromeClient() {
                override fun onShowCustomView(view:View, callback:CustomViewCallback) { if(latestVisible) fullscreen.show(w,view,callback) else callback.onCustomViewHidden() }
                override fun onHideCustomView() { fullscreen.hideFor(w) }
                override fun onProgressChanged(view:WebView,newProgress:Int) { tab.progress=newProgress }
                override fun onReceivedTitle(view:WebView,title:String?) { tab.title=title ?: "" }
            }
            w.setDownloadListener { url,agent,disposition,mime,_ -> latestDownload(url,agent,disposition,mime) }
            if(tab.url.isNotBlank()) w.loadUrl(tab.url)
        }
    },update={ view ->
        view.setBackgroundColor(background)
        val target=if(visible) View.VISIBLE else View.INVISIBLE
        if(view.visibility!=target) view.visibility=target
        val accept=thirdParty && !tab.privateMode
        if(CookieManager.getInstance().acceptThirdPartyCookies(view)!=accept) CookieManager.getInstance().setAcceptThirdPartyCookies(view,accept)
    })
}

@Composable private fun PageProgress(tab:Tab) { Box(Modifier.fillMaxWidth().height(3.dp)) { if(tab.progress<100 && tab.url.isNotBlank()) LinearProgressIndicator(progress={tab.progress/100f},modifier=Modifier.fillMaxSize()) } }
@Composable private fun ReloadButton(tab:Tab) { IconButton(onClick={if(tab.progress<100) {tab.web?.stopLoading();tab.progress=100} else {tab.error=false;tab.web?.reload()}},enabled=tab.url.isNotBlank()) { Text(if(tab.progress<100) "×" else "↻",fontSize=24.sp) } }
