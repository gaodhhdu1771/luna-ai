package com.luna.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        webView = WebView(this)
        setContentView(webView)
        
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // পারমিশন চেক করা হচ্ছে
        if (hasPerms()) {
            setupWebView()
        } else {
            requestPerms()
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun hasPerms(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(this, perms, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && hasPerms()) {
            setupWebView() // পারমিশন পেলে তবে ওয়েবভিউ লোড হবে
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
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
                    }
                }

                if (svc == null && !name.startsWith("open_")) {
                    reply("Accessibility service চালু নেই"); return
                }

                when (name) {
                    "open_app" -> openApp(args.optString("name"), reply)
                    "open_url" -> {
                        runOnUiThread {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.optString("url"))))
                        }
                        reply("ok")
                    }
                    "read_screen" -> reply(svc?.readScreen() ?: "")
                    "click_text" -> svc?.clickText(args.optString("text"), reply)
                    "tap" -> svc?.tap(args.optDouble("x").toFloat(), args.optDouble("y").toFloat(), reply)
                    "swipe" -> svc?.swipe(
                        args.optDouble("x1").toFloat(), args.optDouble("y1").toFloat(),
                        args.optDouble("x2").toFloat(), args.optDouble("y2").toFloat(),
                        args.optLong("duration_ms", 300), reply)
                    "scroll" -> svc?.scroll(args.optString("direction"), reply)
                    "type_text" -> svc?.typeText(args.optString("text"), reply)
                    "press" -> svc?.press(args.optString("key"), reply)
                    "wait" -> {
                        Thread {
                            Thread.sleep((args.optDouble("seconds", 1.0) * 1000).toLong())
                            reply("ok")
                        }.start()
                    }
                    else -> reply("unknown: $name")
                }
            }

            @JavascriptInterface
            fun openAccessibility(token: String) {
                runOnUiThread { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }

            @JavascriptInterface
            fun openOverlay(token: String) {
                runOnUiThread {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }

            @JavascriptInterface
            fun openAppInfo(token: String) {
                runOnUiThread {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }

            private fun openApp(name: String, reply: (String) -> Unit) {
                val pkgs = mapOf(
                    "youtube" to "com.google.android.youtube",
                    "whatsapp" to "com.whatsapp",
                    "chrome" to "com.android.chrome",
                    "facebook" to "com.facebook.katana",
                    "messenger" to "com.facebook.orca",
                    "instagram" to "com.instagram.android",
                    "tiktok" to "com.zhiliaoapp.musically",
                    "camera" to "com.android.camera",
                    "settings" to "com.android.settings",
                    "gmail" to "com.google.android.gm",
                    "telegram" to "org.telegram.messenger",
                    "gallery" to "com.android.gallery3d",
                    "play" to "com.android.vending"
                )
                val key = name.lowercase().trim()
                val pkg = pkgs[key] ?: pkgs.entries.firstOrNull { key.contains(it.key) }?.value
                if (pkg == null) { reply("app list-এ নেই"); return }
                try {
                    val i = packageManager.getLaunchIntentForPackage(pkg)
                    if (i == null) reply("install করা নেই")
                    else { runOnUiThread { startActivity(i) }; reply("ok") }
                } catch (e: Exception) { reply("error: ${e.message}") }
            }
        }, "LunaAndroid")

        webView.loadUrl("file:///android_asset/luna-ai.html")
    }

    // অ্যাপে ফিরে আসলে ভাসমান লুনা বন্ধ হয়ে যাবে
    override fun onResume() {
        super.onResume()
        stopService(Intent(this, LunaFloatService::class.java))
    }

    // অ্যাপ থেকে বেরিয়ে গেলে (ব্যাকগ্রাউন্ডে) ভাসমান লুনা চালু হবে
    override fun onPause() {
        super.onPause()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, LunaFloatService::class.java))
        }
    }
}
