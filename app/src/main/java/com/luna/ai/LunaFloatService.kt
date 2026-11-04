package com.luna.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader

class LunaFloatService : Service() {

    private lateinit var wm: WindowManager
    private var view: FrameLayout? = null
    private var closeView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        showFloat(); return START_STICKY
    }

    private fun showFloat() {
        if (view != null) return

        val size = 160
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 250
        }

        val root = FrameLayout(this)

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@LunaFloatService))
                .build()

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url ?: return null
                    return loader.shouldInterceptRequest(url)
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/luna-ai.html?mini=1")
        }
        
        MainActivity.floatWebView = web

        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B4E"))
                setStroke(3, Color.WHITE)
            }
            background = bg
            visibility = View.GONE 
            val sz = 60
            val lp = FrameLayout.LayoutParams(sz, sz)
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = 50
            layoutParams = lp
            setOnClickListener { stopSelf() }
        }

        web.setOnTouchListener(object : View.OnTouchListener {
            var initX = 0; var initY = 0
            var tX = 0f; var tY = 0f
            var dragging = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x; initY = params.y
                        tX = e.rawX; tY = e.rawY
                        dragging = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tX).toInt()
                        val dy = (e.rawY - tY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            dragging = true
                            close.visibility = View.VISIBLE
                        }
                        if (dragging) {
                            params.x = initX - dx
                            params.y = initY - dy
                            wm.updateViewLayout(root, params)
                            
                            if (e.rawY > resources.displayMetrics.heightPixels - 200) {
                                close.setBackgroundColor(Color.parseColor("#FF0000"))
                            } else {
                                close.setBackgroundColor(Color.parseColor("#FF3B4E"))
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            close.visibility = View.GONE
                            if (e.rawY > resources.displayMetrics.heightPixels - 200) {
                                stopSelf()
                            }
                        } else {
                            val intent = Intent(this@LunaFloatService, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            stopSelf()
                        }
                    }
                }
                return false
            }
        })

        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(close)
        closeView = close
        view = root
        wm.addView(root, params)
    }

    override fun onDestroy() {
        view?.let { wm.removeView(it) }
        view = null
        MainActivity.floatWebView = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
