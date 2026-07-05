package com.example.coverscreenmirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class CoverScreenAccessibilityService : AccessibilityService() {
    companion object {
        var instance: CoverScreenAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.e("ScreenMirror", "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Auto-click MediaProjection confirm dialog
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            
            // 1. Search by system button resource ID (android:id/button1 is standard positive button)
            val buttons = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
            if (buttons != null && buttons.isNotEmpty()) {
                for (button in buttons) {
                    val text = button.text?.toString() ?: ""
                    if (text.contains("Bắt đầu", ignoreCase = true) || 
                        text.contains("Start", ignoreCase = true) || 
                        text.contains("Cho phép", ignoreCase = true) ||
                        text.contains("Allow", ignoreCase = true)) {
                        button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        android.util.Log.e("ScreenMirror", "Auto-clicked media projection dialog button!")
                        return
                    }
                }
            }
            
            // 2. Fallback: search by text
            val textList = listOf("Bắt đầu ngay", "Start now", "Bắt đầu", "Start")
            for (text in textList) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (nodes != null && nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable) {
                            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            android.util.Log.e("ScreenMirror", "Auto-clicked media projection dialog via text: $text")
                            return
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        android.util.Log.e("ScreenMirror", "AccessibilityService destroyed")
    }

    fun dispatchTouch(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 10)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Accessibility dispatchTouch failed: ${e.message}")
        }
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        try {
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Accessibility dispatchSwipe failed: ${e.message}")
        }
    }
}
