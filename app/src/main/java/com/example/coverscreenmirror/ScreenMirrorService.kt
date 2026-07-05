package com.example.coverscreenmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.Surface
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class ScreenMirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    companion object {
        private var surface: Surface? = null
        private var surfaceWidth: Int = 1280
        private var surfaceHeight: Int = 720
        private var instance: ScreenMirrorService? = null
        
        fun setSurfaceAndSize(newSurface: Surface?, width: Int, height: Int) {
            android.util.Log.e("ScreenMirror", "ScreenMirrorService: setSurfaceAndSize called with $newSurface ($width x $height)")
            surface = newSurface
            surfaceWidth = width
            surfaceHeight = height
            instance?.updateVirtualDisplay()
        }
    }

    private var displayListener: android.hardware.display.DisplayManager.DisplayListener? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, buildNotification())

        // Auto-launch CoverScreenActivity on Display 1 when cover screen turns ON
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == 1) {
                    val display = displayManager.getDisplay(1)
                    if (display != null && display.state == android.view.Display.STATE_ON) {
                        if (!CoverScreenActivity.isRunningOnCover) {
                            android.util.Log.e("ScreenMirror", "Cover display turned ON! Launching CoverScreenActivity...")
                            launchCoverScreenActivityOnDisplay1()
                        }
                    }
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
    }

    private fun launchCoverScreenActivityOnDisplay1() {
        try {
            val options = android.app.ActivityOptions.makeBasic()
            options.launchDisplayId = 1
            val intent = Intent(this, CoverScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to launch CoverScreenActivity from Service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("DATA")
        }
        
        android.util.Log.e("ScreenMirror", "onStartCommand: resultCode=$resultCode, mediaProjection=$mediaProjection, resultData=$resultData")

        if (resultCode != 0 && resultData != null && mediaProjection == null) {
            android.util.Log.e("ScreenMirror", "Getting MediaProjection from manager")
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(resultCode, resultData)
            if (proj != null) {
                proj.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        android.util.Log.e("ScreenMirror", "MediaProjection stopped by system")
                        virtualDisplay?.release()
                        virtualDisplay = null
                        mediaProjection = null
                    }
                }, null)
                mediaProjection = proj
                
                // Promote to MEDIA_PROJECTION type once projection token is active (Android Q+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(1, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                }
                
                updateVirtualDisplay()
            } else {
                android.util.Log.e("ScreenMirror", "Failed to get MediaProjection (null)")
            }
        } else if (mediaProjection != null) {
            android.util.Log.e("ScreenMirror", "MediaProjection already exists, updating display")
            updateVirtualDisplay()
        }

        return START_NOT_STICKY
    }

    fun updateVirtualDisplay() {
        android.util.Log.e("ScreenMirror", "updateVirtualDisplay called. projection=$mediaProjection, surface=$surface")
        virtualDisplay?.release()
        virtualDisplay = null

        val currentProjection = mediaProjection
        val currentSurface = surface

        if (currentProjection != null && currentSurface != null) {
            val metrics = resources.displayMetrics
            android.util.Log.e("ScreenMirror", "Creating VirtualDisplay: ${surfaceWidth}x${surfaceHeight} dpi=${metrics.densityDpi}")
            try {
                virtualDisplay = currentProjection.createVirtualDisplay(
                    "CoverScreenMirror",
                    surfaceWidth,
                    surfaceHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                    currentSurface,
                    null,
                    null
                )
                android.util.Log.e("ScreenMirror", "VirtualDisplay created successfully: $virtualDisplay")
            } catch (e: Exception) {
                android.util.Log.e("ScreenMirror", "Error creating VirtualDisplay", e)
            }
        }
    }

    override fun onDestroy() {
        thread {
            try {
                if (Shizuku.pingBinder()) {
                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true
                    val process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 0 && sleep 0.1 && cmd device_state cancel"), null, null) as Process
                    process.waitFor()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        displayListener?.let {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            displayManager.unregisterDisplayListener(it)
        }
        displayListener = null
        virtualDisplay?.release()
        mediaProjection?.stop()
        instance = null
        surface = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "mirror_service",
            "Screen Mirror Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "mirror_service")
            .setContentTitle("Screen Mirroring")
            .setContentText("Casting screen to Cover Display")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
