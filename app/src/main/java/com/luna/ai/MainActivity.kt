package com.luna.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null
        var floatWebView: WebView? = null

        fun sendMessageToWebView(app: String, sender: String, text: String) {
            val js = "window.onNewMessage(${JSONObject.quote(app)}, ${JSONObject.quote(sender)}, ${JSONObject.quote(text)});"
            instance?.runOnUiThread { instance?.webView?.evaluateJavascript(js, null) }
            floatWebView?.post { floatWebView?.evaluateJavascript(js, null) }
        }
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        WindowCompat.setDecorFitsSystemWindows(window, false)
        webView = WebView(this)
        setContentView(webView)
        
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (hasPerms()) { setupWebView() } else { requestPerms() }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun hasPerms(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, 
            Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.CALL_PHONE
        )
        ActivityCompat.requestPermissions(this, perms, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) setupWebView()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.clearCache(true)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url ?: return null
                return loader.shouldInterceptRequest(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
        }

        webView.addJavascriptInterface(object {

            @JavascriptInterface
            fun status(token: String): String {
                val acc = LunaAccessibilityService.instance != null
                val ov = Settings.canDrawOverlays(this@MainActivity)
                return JSONObject().put("acc", acc).put("overlay", ov).toString()
            }

            @JavascriptInterface
            fun invoke(token: String, id: Int, name: String, argsJson: String) {
                val args = try { JSONObject(argsJson) } catch (e: Exception) { JSONObject() }
                val svc = LunaAccessibilityService.instance

                val reply = { r: String ->
                    runOnUiThread {
                        val safe = r.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n")
                        webView.evaluateJavascript("window.__lunaRes($id,'$safe')", null)
                        floatWebView?.post { floatWebView?.evaluateJavascript("window.__lunaRes($id,'$safe')", null) }
                    }
                }

                when (name) {
                    "make_call" -> {
                        val number = args.optString("number")
                        try {
                            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                startActivity(intent)
                                reply("Calling $number")
                            } else {
                                reply("Permission denied for calling")
                            }
                        } catch (e: Exception) { reply("Call error: ${e.message}") }
                    }
                    "accept_call" -> {
                        svc?.clickText("Answer") { r ->
                            if (r == "ok" || r == "clicked") reply("Call accepted")
                            else {
                                svc?.clickText("Accept") { r2 ->
                                    reply(if (r2 == "ok") "Call accepted" else "Could not find answer button")
                                }
                            }
                        } ?: reply("Accessibility service not running")
                    }
                    "speaker_on" -> {
                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.isSpeakerphoneOn = true
                        reply("Speaker turned ON")
                    }
                    "auto_reply" -> {
                        val app = args.optString("app")
                        val sender = args.optString("sender")
                        val message = args.optString("message")
                        reply(svc?.autoReply(app, sender, message) ?: "Error")
                    }
                    "control_volume" -> {
                        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val act = args.optString("action")
                        if (act == "up") am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        else if (act == "down") am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        else if (act == "mute") am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                        reply("Volume adjusted")
                    }
                    "get_phone_status" -> {
                        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        val charging = bm.isCharging
                        reply("Battery: $level%, Charging: $charging")
                    }
                    "open_app" -> {
                        val query = args.optString("name").lowercase().trim()
                        val pm = packageManager
                        
                        // ১. প্রথমে কিছু কমন অ্যাপের প্যাকেজ নেম (এতে দ্রুত কাজ হয়)
                        val knownPkgs = mapOf(
                            "youtube" to "com.google.android.youtube", "whatsapp" to "com.whatsapp",
                            "facebook" to "com.facebook.katana", "messenger" to "com.facebook.orca",
                            "instagram" to "com.instagram.android", "tiktok" to "com.zhiliaoapp.musically",
                            "chrome" to "com.android.chrome", "camera" to "com.android.camera",
                            "settings" to "com.android.settings", "play" to "com.android.vending",
                            "playstore" to "com.android.vending", "capcut" to "com.lemon.lvoverseas",
                            "phone" to "com.android.dialer", "gallery" to "com.android.gallery3d",
                            "telegram" to "org.telegram.messenger", "gmail" to "com.google.android.gm"
                        )
                        
                        var targetPkg = knownPkgs.entries.firstOrNull { query.contains(it.key) }?.value
                        
                        // ২. যদি কমন লিস্টে না থাকে, তবে ফোনের সব অ্যাপের ভেতর খুঁজবে
                        if (targetPkg == null) {
                            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                            val appInfo = apps.firstOrNull { 
                                val label = pm.getApplicationLabel(it).toString().lowercase()
                                label == query || label.contains(query) || it.packageName.lowercase().contains(query)
                            }
                            targetPkg = appInfo?.packageName
                        }
                        
                        if (targetPkg != null) {
                            try {
                                val intent = pm.getLaunchIntentForPackage(targetPkg)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    reply("ok")
                                } else reply("App cannot be launched")
                            } catch (e: Exception) { reply("Launch error") }
                        } else {
                            reply("App not found")
                        }
                    }
                    "open_url" -> { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.optString("url")))); reply("ok") }
                    "read_screen" -> reply(svc?.readScreen() ?: "")
                    "click_text" -> svc?.clickText(args.optString("text"), reply)
                    "tap" -> svc?.tap(args.optDouble("x").toFloat(), args.optDouble("y").toFloat(), reply)
                    "swipe" -> svc?.swipe(args.optDouble("x1").toFloat(), args.optDouble("y1").toFloat(), args.optDouble("x2").toFloat(), args.optDouble("y2").toFloat(), args.optLong("duration_ms", 300), reply)
                    "scroll" -> svc?.scroll(args.optString("direction"), reply)
                    "type_text" -> svc?.typeText(args.optString("text"), reply)
                    "press" -> svc?.press(args.optString("key"), reply)
                    "wait" -> { Thread { Thread.sleep((args.optDouble("seconds", 1.0) * 1000).toLong()); reply("ok") }.start() }
                    else -> reply("unknown: $name")
                }
            }

            @JavascriptInterface fun openAccessibility(token: String) { runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
            @JavascriptInterface fun openOverlay(token: String) { runOnUiThread { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } }
            @JavascriptInterface fun openAppInfo(token: String) { runOnUiThread { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } }
            @JavascriptInterface fun openNotificationSettings(token: String) { runOnUiThread { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } }
        }, "LunaAndroid")

        val timestamp = System.currentTimeMillis()
        webView.loadUrl("https://appassets.androidplatform.net/assets/luna-ai.html?t=$timestamp")
    }

    override fun onResume() { super.onResume(); stopService(Intent(this, LunaFloatService::class.java)) }
    override fun onPause() { super.onPause(); if (Settings.canDrawOverlays(this)) startService(Intent(this, LunaFloatService::class.java)) }
}
