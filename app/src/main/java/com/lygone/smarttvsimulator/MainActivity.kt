package com.lygone.smarttvsimulator

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/launcher.html")
        }
        setContentView(web)
        web.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.refreshApps&&refreshApps()", null)
    }

    // Un launcher no debe cerrarse con Atrás.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    inner class Bridge {
        @JavascriptInterface
        fun apps(): String {
            val pm = packageManager
            fun q(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            val list = q(Intent.CATEGORY_LEANBACK_LAUNCHER).ifEmpty { q(Intent.CATEGORY_LAUNCHER) }
            val out = JSONArray()
            list.distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .forEach { r ->
                    val o = JSONObject()
                    o.put("label", r.loadLabel(pm).toString())
                    o.put("pkg", r.activityInfo.packageName)
                    runCatching {
                        val d = r.loadIcon(pm)
                        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        d.setBounds(0, 0, 96, 96); d.draw(Canvas(bmp))
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                        o.put("icon", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                    }
                    out.put(o)
                }
            return out.toString()
        }

        @JavascriptInterface
        fun open(pkg: String) {
            runOnUiThread {
                val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                    ?: packageManager.getLaunchIntentForPackage(pkg)
                runCatching { i?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
        }
    }
}
