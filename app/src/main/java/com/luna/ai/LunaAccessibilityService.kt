package com.luna.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class LunaAccessibilityService : AccessibilityService() {

    companion object { var instance: LunaAccessibilityService? = null }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return """{"elements":[]}"""
        val arr = JSONArray()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            val label = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            if (label.isNotEmpty()) {
                val r = Rect(); node.getBoundsInScreen(r)
                arr.put(JSONObject().apply {
                    put("text", label); put("x", r.centerX()); put("y", r.centerY())
                })
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return JSONObject().put("elements", arr).toString()
    }

    fun tap(x: Float, y: Float, cb: (String) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { cb("ok") }
            override fun onCancelled(d: GestureDescription?) { cb("cancelled") }
        }, null)
    }

    fun clickText(target: String, cb: (String) -> Unit) {
        val root = rootInActiveWindow ?: return cb("no window")
        val nodes = root.findAccessibilityNodeInfosByText(target)
        val node = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
            ?: return cb("not found")
        var cur: AccessibilityNodeInfo? = node
        while (cur != null && !cur.isClickable) cur = cur.parent
        cb(if (cur?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "ok" else "not clickable")
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long, cb: (String) -> Unit) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { cb("ok") }
            override fun onCancelled(d: GestureDescription?) { cb("cancelled") }
        }, null)
    }

    fun scroll(dir: String, cb: (String) -> Unit) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        when (dir) {
            "up" -> swipe(w/2, h*0.75f, w/2, h*0.25f, 300, cb)
            "down" -> swipe(w/2, h*0.25f, w/2, h*0.75f, 300, cb)
            "left" -> swipe(w*0.8f, h/2, w*0.2f, h/2, 300, cb)
            "right" -> swipe(w*0.2f, h/2, w*0.8f, h/2, 300, cb)
            else -> cb("bad direction")
        }
    }

    fun typeText(t: String, cb: (String) -> Unit) {
        val root = rootInActiveWindow ?: return cb("no window")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return cb("no input field")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
        }
        cb(if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "ok" else "failed")
    }

    fun press(key: String, cb: (String) -> Unit) {
        val action = when (key.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "enter" -> {
                val root = rootInActiveWindow
                val f = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                cb(if (f?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "ok" else "no field")
                return
            }
            else -> return cb("unknown key")
        }
        cb(if (performGlobalAction(action)) "ok" else "failed")
    }
}
