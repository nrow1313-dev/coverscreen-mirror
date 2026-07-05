package com.example.coverscreenmirror

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import android.os.Bundle
import android.os.IBinder
import android.os.Messenger
import android.os.Message
import android.os.Parcel
import android.util.DisplayMetrics
import android.view.*
import androidx.activity.ComponentActivity
import rikka.shizuku.ShizukuBinderWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.View
import android.widget.Toast
import kotlin.concurrent.thread

class CoverScreenActivity : ComponentActivity() {
    companion object {
        var isRunningOnCover = false
            private set
        var captureMessenger: Messenger? = null
        var isBoundToCapture = false
        var serviceBinder: IBinder? = null
    }

    private var mode = "MIRRORING"
    private var virtualDisplay: VirtualDisplay? = null
    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: java.lang.reflect.Method? = null
    private var shizukuMonitorThread: Thread? = null
    private var isMonitoringShizuku = false

    private val stopReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    private lateinit var surfaceView: SurfaceView
    private var mainWidth = 1080
    private var mainHeight = 2640

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            android.util.Log.e("ScreenMirror", "Shizuku UserService connected!")
            serviceBinder = service
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                service?.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
                val messengerBinder = reply.readStrongBinder()
                if (messengerBinder != null) {
                    captureMessenger = Messenger(messengerBinder)
                    if (mode == "SILENT_MIRRORING") {
                        sendStartCaptureMessage()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenMirror", "Failed to retrieve Messenger binder", e)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            captureMessenger = null
            isBoundToCapture = false
            serviceBinder = null
        }
    }

    private fun sendStartCaptureMessage() {
        val messenger = captureMessenger ?: return
        try {
            val msg = Message.obtain(null, 1).apply {
                obj = Bundle().apply {
                    putParcelable("surface", surfaceView.holder.surface)
                    putInt("width", mainWidth)
                    putInt("height", mainHeight)
                }
            }
            messenger.send(msg)
            
            // Force redraw to prevent black screen on initial VirtualDisplay mirror
            thread {
                try {
                    Thread.sleep(500)
                    if (Shizuku.pingBinder()) {
                        val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        method.isAccessible = true
                        val process = method.invoke(null, arrayOf("sh", "-c", "input tap 1 1"), null, null) as Process
                        process.waitFor()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            android.util.Log.e("ScreenMirror", "Sent START_CAPTURE to Shizuku UserService")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendStopCaptureMessage() {
        val messenger = captureMessenger ?: return
        val binder = serviceBinder
        try {
            if (binder != null && binder.isBinderAlive) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, reply, 0)
                    android.util.Log.e("ScreenMirror", "Synchronous stop transaction successful")
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } else {
                val msg = Message.obtain(null, 2)
                messenger.send(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initInputInjector() {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val rawBinder = getServiceMethod.invoke(null, "input") as IBinder
            val shizukuBinder = ShizukuBinderWrapper(rawBinder)

            val iInputManagerClass = Class.forName("android.hardware.input.IInputManager")
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            inputManagerInstance = asInterfaceMethod.invoke(null, shizukuBinder)
            injectInputEventMethod = iInputManagerClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
            android.util.Log.e("ScreenMirror", "ShizukuInputInjector initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to initialize ShizukuInputInjector", e)
        }
    }

    private fun injectMotionEvent(event: MotionEvent) {
        try {
            if (inputManagerInstance == null || injectInputEventMethod == null) {
                initInputInjector()
            }
            injectInputEventMethod?.invoke(inputManagerInstance, event, 0)
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to inject touch event", e)
        }
    }

    private fun performGlobalSystemAction(action: Int) {
        if (mode == "VIRTUAL_DISPLAY" && action == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) {
            val vDisplayId = virtualDisplay?.display?.displayId ?: 1
            thread {
                try {
                    if (Shizuku.pingBinder()) {
                        val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        method.isAccessible = true
                        
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        val launcherPackage = resolveInfo?.activityInfo?.packageName ?: "com.sec.android.app.launcher"
                        val launcherActivity = resolveInfo?.activityInfo?.name ?: "com.sec.android.app.launcher.Launcher"
                        
                        val cmd = "am start -n $launcherPackage/$launcherActivity --display $vDisplayId"
                        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                        process.waitFor()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        val service = CoverScreenAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(action)
        } else {
            // Fallback: inject key event via Shizuku shell
            val keyEvent = when (action) {
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK -> 4
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME -> 3
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS -> 187
                else -> return
            }
            thread {
                try {
                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true
                    val process = method.invoke(null, arrayOf("sh", "-c", "input keyevent $keyEvent"), null, null) as Process
                    process.waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopMirroring() {
        if (mode == "MIRRORING") {
            stopService(Intent(this, ScreenMirrorService::class.java))
        } else if (mode == "SILENT_MIRRORING") {
            sendStopCaptureMessage()
        }
        virtualDisplay?.surface = null
        virtualDisplay?.release()
        virtualDisplay = null
        thread {
            try {
                val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 0 && sleep 0.1 && cmd device_state cancel"), null, null) as Process
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        bypassHiddenApiRestrictions()
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("MODE") ?: "MIRRORING"
        val currentDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            this.display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        if (currentDisplay?.displayId == 1) {
            isRunningOnCover = true
        }
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        // Calculate main display dimensions
        val display = displayManager.getDisplay(0)
        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        mainWidth = metrics.widthPixels
        mainHeight = metrics.heightPixels
        val mainAspectRatio = mainWidth.toFloat() / mainHeight.toFloat()

        // Get cover display dimensions
        val coverDisplay = displayManager.getDisplay(1) ?: displayManager.displays.firstOrNull { it.displayId != 0 }
        val coverMetrics = DisplayMetrics()
        coverDisplay?.getRealMetrics(coverMetrics)
        val coverWidth = coverMetrics.widthPixels
        val coverHeight = coverMetrics.heightPixels
        val coverAspectRatio = coverWidth.toFloat() / coverHeight.toFloat()

        // Resize surface to fit the main aspect ratio center-aligned
        var targetWidth = coverWidth
        var targetHeight = coverHeight

        if (mainAspectRatio > coverAspectRatio) {
            targetHeight = (coverWidth.toFloat() / mainAspectRatio).toInt()
        } else {
            targetWidth = (coverHeight.toFloat() * mainAspectRatio).toInt()
        }

        android.util.Log.e("ScreenMirror", "Resizing SurfaceView to match aspect ratio: ${targetWidth}x${targetHeight}")

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        var startX = 0f
        var startY = 0f
        var startTime = 0L

        surfaceView = SurfaceView(this).apply {
            setZOrderOnTop(true)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    android.util.Log.e("ScreenMirror", "CoverScreenActivity: surfaceCreated")
                }
                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    android.util.Log.e("ScreenMirror", "CoverScreenActivity: surfaceChanged ($width x $height) for mode $mode")
                    if (mode == "VIRTUAL_DISPLAY") {
                        if (virtualDisplay == null) {
                            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                            try {
                                virtualDisplay = dm.createVirtualDisplay(
                                    "CoverVirtualDisplay",
                                    mainWidth, mainHeight,
                                    resources.displayMetrics.densityDpi,
                                    holder.surface,
                                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                )
                                val vDisplayId = virtualDisplay?.display?.displayId ?: 1
                                android.util.Log.e("ScreenMirror", "VirtualDisplay created: ID = $vDisplayId")
                                
                                // The automatic launcher start logic has been removed as requested
                            } catch (e: Exception) {
                                android.util.Log.e("ScreenMirror", "Failed to create VirtualDisplay", e)
                            }
                        }
                    } else if (mode == "SILENT_MIRRORING") {
                        if (!isBoundToCapture) {
                            try {
                                val serviceArgs = Shizuku.UserServiceArgs(
                                    android.content.ComponentName(packageName, ScreenCaptureService::class.java.name)
                                ).apply {
                                    daemon(false)
                                    processNameSuffix("mirror_service")
                                    debuggable(true)
                                }
                                Shizuku.bindUserService(serviceArgs, serviceConnection)
                                isBoundToCapture = true
                            } catch (e: Exception) {
                                android.util.Log.e("ScreenMirror", "Failed to bind Shizuku UserService", e)
                            }
                        } else {
                            sendStartCaptureMessage()
                        }
                    } else {
                        ScreenMirrorService.setSurfaceAndSize(holder.surface, mainWidth, mainHeight)
                    }
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    android.util.Log.e("ScreenMirror", "CoverScreenActivity: surfaceDestroyed")
                    if (mode == "VIRTUAL_DISPLAY") {
                        virtualDisplay?.surface = null
                        virtualDisplay?.release()
                        virtualDisplay = null
                    } else if (mode == "SILENT_MIRRORING") {
                        sendStopCaptureMessage()
                    } else {
                        ScreenMirrorService.setSurfaceAndSize(null, 1280, 720)
                    }
                }
            })

            setOnTouchListener { view, event ->
                val viewWidth = view.width
                val viewHeight = view.height
                if (viewWidth > 0 && viewHeight > 0) {
                    val scaleX = mainWidth.toFloat() / viewWidth.toFloat()
                    val scaleY = mainHeight.toFloat() / viewHeight.toFloat()

                    val prefs = getSharedPreferences("mirror_prefs", Context.MODE_PRIVATE)
                    val controlMode = prefs.getString("control_mode", "shizuku") ?: "shizuku"

                    if (controlMode == "shizuku") {
                        val pointerCount = event.pointerCount
                        val pointerProperties = Array(pointerCount) { MotionEvent.PointerProperties() }
                        val pointerCoords = Array(pointerCount) { MotionEvent.PointerCoords() }

                        for (i in 0 until pointerCount) {
                            event.getPointerProperties(i, pointerProperties[i])
                            event.getPointerCoords(i, pointerCoords[i])
                            pointerCoords[i].x *= scaleX
                            pointerCoords[i].y *= scaleY
                        }

                        val scaledEvent = MotionEvent.obtain(
                            event.downTime,
                            event.eventTime,
                            event.action,
                            pointerCount,
                            pointerProperties,
                            pointerCoords,
                            event.metaState,
                            event.buttonState,
                            event.xPrecision,
                            event.yPrecision,
                            event.deviceId,
                            event.edgeFlags,
                            InputDevice.SOURCE_TOUCHSCREEN,
                            event.flags
                        )
                        
                        val vDisplayId = if (mode == "VIRTUAL_DISPLAY") {
                            virtualDisplay?.display?.displayId ?: 1
                        } else {
                            0
                        }
                        
                        try {
                            val setDisplayIdMethod = MotionEvent::class.java.getDeclaredMethod("setDisplayId", java.lang.Integer.TYPE)
                            setDisplayIdMethod.isAccessible = true
                            setDisplayIdMethod.invoke(scaledEvent, vDisplayId)
                        } catch (e: Exception) {
                            android.util.Log.e("ScreenMirror", "Failed to set display ID on MotionEvent", e)
                        }
                        
                        injectMotionEvent(scaledEvent)
                        scaledEvent.recycle()
                    } else {
                        val x = event.x
                        val y = event.y
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = x
                                startY = y
                                startTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                val endX = x
                                val endY = y
                                val endTime = System.currentTimeMillis()
                                val duration = endTime - startTime
                                val dx = endX - startX
                                val dy = endY - startY
                                val distance = Math.sqrt((dx * dx + dy * dy).toDouble())

                                val scaledStartX = startX * scaleX
                                val scaledStartY = startY * scaleY
                                val scaledEndX = endX * scaleX
                                val scaledEndY = endY * scaleY

                                val service = CoverScreenAccessibilityService.instance
                                if (service != null) {
                                    if (distance < 30 && duration < 300) {
                                        service.dispatchTouch(scaledStartX, scaledStartY)
                                    } else {
                                        val swipeDuration = Math.max(100L, duration)
                                        service.dispatchSwipe(scaledStartX, scaledStartY, scaledEndX, scaledEndY, swipeDuration)
                                    }
                                }
                            }
                        }
                    }
                }
                view.performClick()
                true
            }
        }
        container.addView(surfaceView)
        rootLayout.addView(container)

        // Glassmorphic buttons bar (vertical LinearLayout on the left side)
        val density = resources.displayMetrics.density
        val leftBarWidth = (coverWidth - targetWidth) / 2
        val buttonBarWidthPx = (42 * density).toInt()
        
        // Centered inside the left black bar, fallback to 8dp margin if no black bar
        val computedLeftMargin = if (leftBarWidth > buttonBarWidthPx) {
            (leftBarWidth - buttonBarWidthPx) / 2
        } else {
            (8 * density).toInt()
        }

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#77000000")) // Translucent dark glass
                cornerRadius = 16 * density
            }
            layoutParams = FrameLayout.LayoutParams(
                buttonBarWidthPx,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                leftMargin = computedLeftMargin
            }
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }

        val btnSize = (38 * density).toInt()
        val btnMargin = (4 * density).toInt()

        val stopBtn = VectorButton(this, "stop") { stopMirroring() }.apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, btnMargin, 0, btnMargin)
            }
        }
        val homeBtn = VectorButton(this, "home") {
            performGlobalSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, btnMargin, 0, btnMargin)
            }
        }
        val tabBtn = VectorButton(this, "tab") {
            performGlobalSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, btnMargin, 0, btnMargin)
            }
        }
        val backBtn = VectorButton(this, "back") {
            performGlobalSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                setMargins(0, btnMargin, 0, btnMargin)
            }
        }

        buttonBar.addView(stopBtn)
        buttonBar.addView(homeBtn)
        buttonBar.addView(tabBtn)
        buttonBar.addView(backBtn)

        rootLayout.addView(buttonBar)

        setContentView(rootLayout)
        startShizukuMonitor()
    }

    override fun onDestroy() {
        isRunningOnCover = false
        stopShizukuMonitor()
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        CoverScreenAccessibilityService.instance?.showNavigationBar(false)

        if (isBoundToCapture) {
            sendStopCaptureMessage()
            try {
                val serviceArgs = Shizuku.UserServiceArgs(
                    android.content.ComponentName(packageName, ScreenCaptureService::class.java.name)
                ).apply {
                    daemon(false)
                    processNameSuffix("mirror_service")
                }
                Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBoundToCapture = false
        }

        virtualDisplay?.release()
        virtualDisplay = null
        try {
            val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 0 && sleep 0.1 && cmd device_state cancel"), null, null) as Process
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun startShizukuMonitor() {
        val prefs = getSharedPreferences("mirror_prefs", Context.MODE_PRIVATE)
        val controlMode = prefs.getString("control_mode", "shizuku") ?: "shizuku"
        if (controlMode != "shizuku") return

        isMonitoringShizuku = true
        shizukuMonitorThread = thread {
            while (isMonitoringShizuku) {
                try {
                    Thread.sleep(2000)
                    if (!checkShizukuPermission()) {
                        android.util.Log.e("ScreenMirror", "Shizuku binder dead or permission revoked!")
                        runOnUiThread {
                            Toast.makeText(this@CoverScreenActivity, "Shizuku bị mất quyền/kết nối! Đang khôi phục màn hình...", Toast.LENGTH_LONG).show()
                            stopMirroring()
                        }
                        break
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopShizukuMonitor() {
        isMonitoringShizuku = false
        shizukuMonitorThread?.interrupt()
        shizukuMonitorThread = null
    }

    private fun bypassHiddenApiRestrictions() {
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L")
            android.util.Log.e("ScreenMirror", "Successfully bypassed Hidden API restrictions in App process!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to bypass Hidden API restrictions in App process", e)
        }
    }
}

// Custom View to draw Vector shapes for Home, Back, Recents, Stop buttons
class VectorButton(context: Context, val type: String, val onClick: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = true
        isFocusable = true
        val outValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        setOnClickListener { onClick() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val size = Math.min(w, h) * 0.35f

        when (type) {
            "stop" -> {
                // Circle with a red fill and a white cross
                paint.style = Paint.Style.FILL
                paint.color = Color.RED
                canvas.drawCircle(cx, cy, size, paint)
                
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * resources.displayMetrics.density
                val offset = size * 0.4f
                canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, paint)
                canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, paint)
            }
            "back" -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * resources.displayMetrics.density
                val path = Path().apply {
                    moveTo(cx + size * 0.3f, cy - size * 0.5f)
                    lineTo(cx - size * 0.3f, cy)
                    lineTo(cx + size * 0.3f, cy + size * 0.5f)
                }
                canvas.drawPath(path, paint)
            }
            "home" -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * resources.displayMetrics.density
                val path = Path().apply {
                    moveTo(cx, cy - size * 0.6f)
                    lineTo(cx - size * 0.6f, cy)
                    lineTo(cx - size * 0.6f, cy + size * 0.6f)
                    lineTo(cx + size * 0.6f, cy + size * 0.6f)
                    lineTo(cx + size * 0.6f, cy)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "tab" -> {
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * resources.displayMetrics.density
                val rectSize = size * 0.7f
                // Back card outline
                canvas.drawRoundRect(
                    cx - rectSize * 0.2f, cy - rectSize * 0.5f, 
                    cx + rectSize * 0.6f, cy + rectSize * 0.3f, 
                    4f, 4f, paint
                )
                // Front card filled + outline
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#444444")
                canvas.drawRoundRect(
                    cx - rectSize * 0.6f, cy - rectSize * 0.3f, 
                    cx + rectSize * 0.2f, cy + rectSize * 0.5f, 
                    4f, 4f, paint
                )
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                canvas.drawRoundRect(
                    cx - rectSize * 0.6f, cy - rectSize * 0.3f, 
                    cx + rectSize * 0.2f, cy + rectSize * 0.5f, 
                    4f, 4f, paint
                )
            }
        }
    }
}

