package com.plus.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.cos
import kotlin.math.sin

private val Gold=Color(0xffead093)
private val MenuText=Color(0xffeef2ff)
private val MenuMuted=Color(0xffa6b0ca)

@Composable internal fun PlusMark(modifier:Modifier=Modifier) {
    Image(painterResource(R.drawable.plus_mark),contentDescription="PLUS",modifier=modifier)
}

@Composable private fun ProStatusText(usage:ProUsage, isFa:Boolean, color:Color, modifier:Modifier=Modifier) {
    val status=when {
        usage.isDefault -> if(isFa) "PRO+ فعال شد" else "PRO+ UNLOCKED"
        usage.developer -> if(isFa) "دسترسی آزمایشی توسعه‌دهنده" else "DEVELOPER TEST ACCESS"
        else -> if(isFa) "PLUS را مرورگر پیش‌فرض کنید" else "Set PLUS as your default browser"
    }
    Text(status,modifier=modifier,fontSize=11.sp,color=color,fontWeight=FontWeight.SemiBold,maxLines=2)
}

@Composable internal fun HomeProCard(usage:ProUsage,isFa:Boolean,onOpen:()->Unit) {
    val transition=rememberInfiniteTransition(label="pro-light")
    val glow by transition.animateFloat(initialValue=-60f,targetValue=140f,animationSpec=infiniteRepeatable(tween(4800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="glow")
    val shape=RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().border(1.dp,Color(0xff675e62),shape).background(
        Brush.linearGradient(listOf(Color(0xff192238),Color(0xff25213b),Color(0xff111b2d)),start=Offset(glow,0f),end=Offset(glow+400f,240f)),shape
    ).clickable(onClick=onOpen).padding(15.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(0xff3d342a),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) { MenuIcon("pro",Gold,Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PLUS PRO+",color=Gold,fontWeight=FontWeight.Bold,fontSize=16.sp,letterSpacing=.5.sp)
                Text(if(isFa) "قدرت بیشتر. آزادی بیشتر." else "More Power. More Freedom.",color=Color(0xffe5dfd0),fontSize=12.sp)
                Text(if(isFa) "پنج قابلیت قدرتمند مرورگر را کشف کنید." else "Discover four powerful browser features.",color=MenuMuted,fontSize=11.sp)
                Spacer(Modifier.height(6.dp)); ProStatusText(usage,isFa,Gold)
            }
            Text("›",color=Gold,fontSize=24.sp)
        }
    }
}

@Composable internal fun PlusOverflowMenu(usage:ProUsage,isFa:Boolean,onAction:(String)->Unit,onOpen:()->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val visibility=remember { MutableTransitionState(false) }
    visibility.targetState=expanded
    val config=LocalConfiguration.current
    val maxWidth=(config.screenWidthDp-24).coerceAtLeast(180).dp
    val maxHeight=(config.screenHeightDp-96).coerceAtLeast(180).dp
    fun label(en:String,fa:String)=if(isFa) fa else en
    val items=listOf(
        Triple("new",label("New Tab","زبانه جدید"),"new"),
        Triple("private",label("New Private Tab","زبانه خصوصی جدید"),"private"),
        Triple("bookmarks",label("Bookmarks","نشانک‌ها"),"bookmark"),
        Triple("history",label("History","تاریخچه"),"history"),
        Triple("downloads",label("Downloads","دانلودها"),"downloads"),
        Triple("settings",label("Settings","تنظیمات"),"settings"),
        Triple("about",label("About","درباره"),"about")
    )
    Box {
        IconButton(onClick={onOpen();expanded=true}) {Text("⋮",fontSize=26.sp)}
        if(visibility.currentState || visibility.targetState) Popup(alignment=Alignment.TopEnd,onDismissRequest={expanded=false},properties=PopupProperties(focusable=true,clippingEnabled=true)) {
            AnimatedVisibility(visibleState=visibility,enter=fadeIn(tween(140))+scaleIn(tween(180),initialScale=.96f,transformOrigin=TransformOrigin(1f,0f)),exit=fadeOut(tween(110))+scaleOut(tween(110),targetScale=.98f,transformOrigin=TransformOrigin(1f,0f))) {
                Surface(modifier=Modifier.widthIn(max=maxWidth).width(300.dp).heightIn(max=maxHeight),shape=RoundedCornerShape(24.dp),color=Color(0xff111a2e),shadowElevation=18.dp,border=BorderStroke(1.dp,Color(0xff34425c))) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                            PlusMark(Modifier.size(33.dp)); Spacer(Modifier.width(10.dp))
                            Column {Text("PLUS",color=MenuText,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp); Text("MORE THAN APPS.",color=Color(0xff9e91c7),fontSize=9.sp,letterSpacing=1.sp)}
                        }
                        HorizontalDivider(Modifier.padding(vertical=6.dp),color=Color(0xff303c51))
                        items.take(2).forEach { (action,title,icon)-> MenuRow(title,icon) {expanded=false;onAction(action)} }
                        HorizontalDivider(Modifier.padding(vertical=6.dp),color=Color(0xff303c51))
                        items.drop(2).take(3).forEach { (action,title,icon)-> MenuRow(title,icon) {expanded=false;onAction(action)} }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff3b3440),Color(0xff262538))),RoundedCornerShape(16.dp)).border(1.dp,Color(0xff766543),RoundedCornerShape(16.dp)).clickable {expanded=false;onAction("pro")}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            MenuIcon("pro",Gold,Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text("PLUS PRO+",color=Gold,fontWeight=FontWeight.Bold);ProStatusText(usage,isFa,Color(0xffd3c3a2))};Text("›",color=Gold,fontSize=20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        items.takeLast(2).forEach { (action,title,icon)-> MenuRow(title,icon) {expanded=false;onAction(action)} }
                    }
                }
            }
        }
    }
}
@Composable private fun MenuRow(label:String,icon:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=43.dp).clickable(onClick=onClick).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
        MenuIcon(icon,Color(0xffa4bff6),Modifier.size(20.dp));Spacer(Modifier.width(13.dp));Text(label,color=MenuText,fontSize=14.sp,fontWeight=FontWeight.Medium)
    }
}

/** Small purpose-drawn line icons; the coordinates are in a 24-unit viewport. */
@Composable private fun MenuIcon(kind:String,color:Color,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val unit=size.width/24f
        scale(unit,unit,pivot=Offset.Zero) {
            val stroke=Stroke(width=1.7f,cap=StrokeCap.Round)
            fun line(x:Float,y:Float,x2:Float,y2:Float) = drawLine(color,Offset(x,y),Offset(x2,y2),strokeWidth=1.7f,cap=StrokeCap.Round)
            when(kind) {
                "new" -> { drawRoundRect(color,Offset(3f,3f),Size(18f,18f),CornerRadius(3f),style=stroke);line(12f,8f,12f,16f);line(8f,12f,16f,12f) }
                "private" -> { val p=Path().apply { moveTo(12f,2f);lineTo(20f,5f);lineTo(19f,13f);quadraticBezierTo(18f,19f,12f,22f);quadraticBezierTo(6f,19f,5f,13f);lineTo(4f,5f);close() };drawPath(p,color,style=stroke);drawCircle(color,1.5f,Offset(9f,11f));drawCircle(color,1.5f,Offset(15f,11f)) }
                "bookmark" -> { val p=Path().apply {moveTo(6f,3f);lineTo(18f,3f);lineTo(18f,21f);lineTo(12f,17f);lineTo(6f,21f);close()};drawPath(p,color,style=stroke) }
                "history" -> {drawArc(color,-75f,300f,false,Offset(3f,3f),Size(18f,18f),style=stroke);line(12f,7f,12f,12f);line(12f,12f,16f,14f);line(3f,4f,3f,8f);line(3f,8f,7f,8f)}
                "downloads" -> {line(12f,3f,12f,15f);line(8f,11f,12f,15f);line(16f,11f,12f,15f);line(4f,19f,20f,19f)}
                "settings" -> {drawCircle(color,7f,Offset(12f,12f),style=stroke);drawCircle(color,2.5f,Offset(12f,12f),style=stroke);for(i in 0..7) {val a=i*Math.PI/4;line((12+8*cos(a)).toFloat(),(12+8*sin(a)).toFloat(),(12+10*cos(a)).toFloat(),(12+10*sin(a)).toFloat())} }
                "about" -> {drawCircle(color,9f,Offset(12f,12f),style=stroke);drawCircle(color,1.1f,Offset(12f,7.5f));line(12f,11f,12f,17f)}
                "pro" -> {val p=Path();for(i in 0..9) { val a=-Math.PI/2+i*Math.PI/5;val r=if(i%2==0) 10.0 else 4.5; val x=(12+r*cos(a)).toFloat();val y=(12+r*sin(a)).toFloat();if(i==0)p.moveTo(x,y) else p.lineTo(x,y)};p.close();drawPath(p,color)}
            }
        }
    }
}
