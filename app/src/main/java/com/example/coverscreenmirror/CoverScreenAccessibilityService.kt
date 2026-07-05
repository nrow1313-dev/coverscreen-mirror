package com.example.coverscreenmirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import kotlin.concurrent.thread
import rikka.shizuku.Shizuku

class CoverScreenAccessibilityService : AccessibilityService() {
    companion object {
        var instance: CoverScreenAccessibilityService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var navigationBarView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        android.util.Log.e("ScreenMirror", "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events are not needed for UI automation anymore
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        showNavigationBar(false)
        instance = null
        super.onDestroy()
        android.util.Log.e("ScreenMirror", "AccessibilityService destroyed")
    }

    fun showNavigationBar(show: Boolean) {
        if (show) {
            if (navigationBarView != null) return // Already showing
            
            val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val coverDisplay = dm.getDisplay(1) ?: dm.displays.firstOrNull { it.displayId != 0 } ?: return
            
            val coverContext = createDisplayContext(coverDisplay)
            windowManager = coverContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val density = resources.displayMetrics.density
            val btnSize = (38 * density).toInt()
            val btnMargin = (4 * density).toInt()
            val buttonBarWidthPx = (42 * density).toInt()
            
            val buttonBar = LinearLayout(coverContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#77000000")) // Translucent dark glass
                    cornerRadius = 16 * density
                }
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            
            val stopBtn = VectorButton(coverContext, "stop") { 
                stopMirroringOrLauncher()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    setMargins(0, btnMargin, 0, btnMargin)
                }
            }
            val homeBtn = VectorButton(coverContext, "home") {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    setMargins(0, btnMargin, 0, btnMargin)
                }
            }
            val tabBtn = VectorButton(coverContext, "tab") {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    setMargins(0, btnMargin, 0, btnMargin)
                }
            }
            val backBtn = VectorButton(coverContext, "back") {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    setMargins(0, btnMargin, 0, btnMargin)
                }
            }
            
            buttonBar.addView(stopBtn)
            buttonBar.addView(homeBtn)
            buttonBar.addView(tabBtn)
            buttonBar.addView(backBtn)
            
            val params = WindowManager.LayoutParams(
                buttonBarWidthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                x = (8 * density).toInt()
            }
            
            try {
                windowManager?.addView(buttonBar, params)
                navigationBarView = buttonBar
                android.util.Log.e("ScreenMirror", "Navigation bar added to Display 1")
            } catch (e: Exception) {
                android.util.Log.e("ScreenMirror", "Failed to add navigation bar to WindowManager", e)
            }
        } else {
            if (navigationBarView != null) {
                try {
                    windowManager?.removeView(navigationBarView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                navigationBarView = null
                windowManager = null
                android.util.Log.e("ScreenMirror", "Navigation bar removed from Display 1")
            }
        }
    }

    private fun stopMirroringOrLauncher() {
        val context = this
        context.stopService(Intent(context, ScreenMirrorService::class.java))
        showNavigationBar(false)
        
        thread {
            try {
                if (Shizuku.pingBinder()) {
                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true
                    var proc = method.invoke(null, arrayOf("sh", "-c", "wm size -d 1 reset"), null, null) as Process
                    proc.waitFor()
                    proc = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 0 && sleep 0.1 && cmd device_state cancel"), null, null) as Process
                    proc.waitFor()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val intent = Intent("com.example.coverscreenmirror.ACTION_STOP").apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
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
