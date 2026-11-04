package com.plus.browser

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hosts WebChromeClient's fullscreen content as a separate window-level view. */
internal class FullscreenVideo(private val activity:ComponentActivity) {
    var isShowing by mutableStateOf(false); private set
    private var host:FrameLayout?=null
    private var owner:WebView?=null
    private var callback:WebChromeClient.CustomViewCallback?=null
    private var oldOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var oldBehavior=WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    private var oldStatusVisible=true
    private var oldNavigationVisible=true

    fun show(web:WebView, view:View, done:WebChromeClient.CustomViewCallback) {
        if(isShowing) { done.onCustomViewHidden(); return }
        val decor=activity.window.decorView as? ViewGroup ?: run { done.onCustomViewHidden();return }
        if(view.parent!=null) { done.onCustomViewHidden();return }
        val controller=WindowCompat.getInsetsController(activity.window,decor)
        val insets=ViewCompat.getRootWindowInsets(decor)
        oldStatusVisible=insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        oldNavigationVisible=insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        oldBehavior=controller.systemBarsBehavior
        oldOrientation=activity.requestedOrientation
        val frame=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK); addView(view,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)) }
        try { decor.addView(frame,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)) }
        catch(_:Exception) { frame.removeView(view);done.onCustomViewHidden();return }
        owner=web; callback=done; host=frame; isShowing=true
        controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        try { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } catch (_:Exception) { /* Some devices do not support rotation. */ }
    }
    fun hide() {
        if(!isShowing) return
        val done=callback
        isShowing=false;callback=null;owner=null
        host?.let { frame -> (frame.parent as? ViewGroup)?.removeView(frame);frame.removeAllViews() };host=null
        val decor=activity.window.decorView
        val controller=WindowCompat.getInsetsController(activity.window,decor)
        controller.systemBarsBehavior=oldBehavior
        if(oldStatusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
        if(oldNavigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        try {activity.requestedOrientation=oldOrientation} catch (_:Exception) {}
        done?.onCustomViewHidden()
    }
    fun hideFor(web:WebView) { if(owner===web) hide() }
    fun release(web:WebView) { if(owner===web) hide() }
}
