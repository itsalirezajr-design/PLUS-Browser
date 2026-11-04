package com.plus.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
import java.util.Locale

internal enum class SearchCategory { WEB, IMAGES, VIDEOS, NEWS }
internal fun searchUrl(engine:String,category:SearchCategory,query:String):String {
    val q=URLEncoder.encode(query.trim(),"UTF-8")
    return when(engine) {
        "DuckDuckGo" -> "https://duckduckgo.com/?q=$q" + when(category) { SearchCategory.WEB->"&ia=web";SearchCategory.IMAGES->"&ia=images&iax=images";SearchCategory.VIDEOS->"&ia=videos&iax=videos";SearchCategory.NEWS->"&ia=news&iar=news" }
        "Bing" -> "https://www.bing.com/" + when(category) {SearchCategory.WEB->"search";SearchCategory.IMAGES->"images/search";SearchCategory.VIDEOS->"videos/search";SearchCategory.NEWS->"news/search"} + "?q=$q"
        else -> "https://www.google.com/search?q=$q" + when(category) {SearchCategory.WEB->"";SearchCategory.IMAGES->"&tbm=isch";SearchCategory.VIDEOS->"&tbm=vid";SearchCategory.NEWS->"&tbm=nws"}
    }
}

@Composable internal fun PlusSearchScreen(query:String,onQuery:(String)->Unit,engine:String,onSearch:(String)->Unit,modifier:Modifier=Modifier) {
    val language=Locale.getDefault().language
    val rtl=language=="fa" || language=="ar"
    fun label(en:String,fa:String,ar:String)=when(language){"fa"->fa;"ar"->ar;else->en}
    var category by remember { mutableStateOf(SearchCategory.WEB) }
    var focused by remember { mutableStateOf(false) }
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val border by animateColorAsState(if(focused) Color(0xff79d4ff) else Color(0xff536486),label="search-focus")
    fun submit() { if(query.isNotBlank()) { keyboard?.hide();focusManager.clearFocus();onSearch(searchUrl(engine,category,query)) } }
    CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xff090f23),Color(0xff171432),Color(0xff090f23)))).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(110.dp).background(Brush.radialGradient(listOf(Color(0x55503fff),Color.Transparent)),RoundedCornerShape(40.dp)),contentAlignment=Alignment.Center) { PlusMark(Modifier.size(82.dp)) }
            Spacer(Modifier.height(8.dp));Text("PLUS SEARCH",color=Color(0xfff4f6ff),fontSize=24.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
            Text("MORE THAN APPS.",color=Color(0xffb8a8e3),fontSize=10.sp,letterSpacing=2.sp)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(value=query,onValueChange=onQuery,modifier=Modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused}.border(1.dp,border,RoundedCornerShape(20.dp)),singleLine=true,shape=RoundedCornerShape(20.dp),textStyle=TextStyle(color=Color.White,fontSize=16.sp),placeholder={Text(label("Search the web","جستجو در وب","ابحث في الويب"),color=Color(0xffb2bad1))},leadingIcon={Text("⌕",color=Color(0xff8ccfff),fontSize=25.sp)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={onQuery("")}) {Text("×",color=Color.White,fontSize=24.sp)}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,focusedContainerColor=Color(0xff1c2940),unfocusedContainerColor=Color(0xff1c2940),cursorColor=Color(0xff8ccfff)))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(SearchCategory.WEB to label("Web","وب","الويب"),SearchCategory.IMAGES to label("Images","تصاویر","صور"),SearchCategory.VIDEOS to label("Videos","ویدیو","فيديو"),SearchCategory.NEWS to label("News","اخبار","أخبار")).forEach { (type,title) ->
                    FilterChip(selected=category==type,onClick={category=type},label={Text(title,maxLines=1,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(labelColor=Color(0xffcad3ea),selectedLabelColor=Color.White,selectedContainerColor=Color(0xff493e82)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick={submit()},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xff4e71cf))) { Text(label("SEARCH","جستجو","بحث"),fontWeight=FontWeight.Bold) }
            Spacer(Modifier.height(18.dp))
            Text(label("Results open in $engine inside PLUS Browser. The provider's own page and branding are shown.","نتایج $engine در مرورگر PLUS باز می‌شوند و صفحه و نشان ارائه‌دهنده نمایش داده می‌شود.","تفتح نتائج $engine داخل متصفح PLUS وتظهر صفحة المزود وعلامته."),fontSize=12.sp,color=Color(0xffaab7d2),lineHeight=18.sp)
        }
    }
}
