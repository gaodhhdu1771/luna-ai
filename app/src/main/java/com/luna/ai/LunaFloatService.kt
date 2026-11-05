package com.luna.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient

class LunaFloatService : Service() {

    private lateinit var wm: WindowManager
    private var view: WebView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        showFloat(); return START_STICKY
    }

    private fun showFloat() {
        if (view != null) return

        val size = 160 // Nova Orb Size
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL // নিচে এবং মাঝখানে
            x = 0
            y = 100 // নিচ থেকে দূরত্ব (বাড়ালে আরও উপরে উঠবে)
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/luna_float.html")
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
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true
                        if (dragging) {
                            params.x = initX - dx
                            params.y = initY - dy
                            wm.updateViewLayout(view, params)
                            return true
                        }
                    }
                }
                return false
            }
        })

        view = web
        wm.addView(web, params)
    }

    override fun onDestroy() {
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
