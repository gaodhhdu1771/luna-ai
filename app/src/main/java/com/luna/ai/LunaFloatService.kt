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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

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

        val size = 150 // ভাসমান সার্কেলের সাইজ
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
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/luna-ai.html?mini=1")
            
            // লুনার ওপর ট্যাপ করলে মেইন অ্যাপ ওপেন হবে
            setOnClickListener {
                val intent = Intent(this@LunaFloatService, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                stopSelf()
            }
        }

        // ক্লোজ (✕) বাটন তৈরি
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCCFF3B4E.toInt()) // লাল রঙ
            }
            background = bg
            val sz = 70
            val lp = FrameLayout.LayoutParams(sz, sz)
            lp.gravity = Gravity.TOP or Gravity.END
            layoutParams = lp
            setOnClickListener {
                stopSelf() // সার্ভিস বন্ধ হয়ে লুনা মুছে যাবে
            }
        }

        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(closeBtn)

        // লুনাকে ড্র্যাগ করে সরানোর জন্য
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
                            wm.updateViewLayout(root, params)
                            return true
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
