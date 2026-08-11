package com.nithieeee.ren

import android.content.Context
import android.webkit.JavascriptInterface

class WebAppInterface(private val context: Context, private val activity: MainActivity) {

    @JavascriptInterface
    fun updateMediaState(title: String, isPlaying: Boolean, artworkUrl: String) {
        activity.runOnUiThread {
            AudioService.updateNotification(context, title, isPlaying, artworkUrl)
        }
    }
}
