package com.luna.ai

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader

class LunaFloatService : Service() {

    private lateinit var wm: WindowManager
    private var view: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        showFloat(); return START_STICKY
    }

    private fun showFloat() {
        if (view != null) return

        val size = 150
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
            setBackgroundColor(0x00000000)
            
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@LunaFloatService))
                .build()

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    // এখানেও লিংক ফাঁকা কিনা চেক করা হয়েছে
                    val url = request?.url ?: return null
                    return loader.shouldInterceptRequest(url)
                }
            }
            
            loadUrl("https://appassets.androidplatform.net/assets/luna-ai.html?mini=1")
        }

        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        val handler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable { stopSelf() }
        var isLongPressed = false

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
                        isLongPressed = false
                        handler.postDelayed(longPressRunnable, 5000)
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tX).toInt()
                        val dy = (e.rawY - tY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            dragging = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        if (dragging) {
                            params.x = initX - dx
                            params.y = initY - dy
                            wm.updateViewLayout(root, params)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (!dragging && !isLongPressed) {
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

        view = root
        wm.addView(root, params)
    }

    override fun onDestroy() {
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
