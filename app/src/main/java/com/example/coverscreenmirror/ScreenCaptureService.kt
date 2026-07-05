package com.example.coverscreenmirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.*
import android.view.Surface
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ScreenCaptureService(private val context: Context) : Binder() {
    private var virtualDisplay: VirtualDisplay? = null
    
    init {
        bypassHiddenApiRestrictions()
    }
    
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> { // START_CAPTURE
                    val data = msg.obj as? Bundle ?: return
                    data.classLoader = Surface::class.java.classLoader
                    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        data.getParcelable("surface", Surface::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        data.getParcelable("surface")
                    }
                    val width = data.getInt("width")
                    val height = data.getInt("height")
                    startCapture(surface, width, height)
                }
                2 -> { // STOP_CAPTURE
                    stopCapture()
                }
            }
        }
    }
    
    private val messenger = Messenger(handler)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.FIRST_CALL_TRANSACTION) {
            reply?.writeStrongBinder(messenger.binder)
            return true
        } else if (code == IBinder.FIRST_CALL_TRANSACTION + 1) {
            // Synchronous stop request
            val thread = Thread { stopCapture() }
            thread.start()
            thread.join(2000) // Wait up to 2 seconds
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun bypassHiddenApiRestrictions() {
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            android.util.Log.e("ScreenMirror", "Successfully bypassed Hidden API restrictions in UserService!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to bypass Hidden API restrictions in UserService", e)
        }
    }

    private fun startCapture(surface: Surface?, width: Int, height: Int) {
        if (surface == null) {
            android.util.Log.e("ScreenMirror", "ScreenCaptureService: Received null surface")
            return
        }
        try {
            stopCapture()
            
            // Create context for com.android.shell to match UID 2000
            val shellContext = context.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
            val displayManager = shellContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            
            // Public Virtual Display Flags
            // VIRTUAL_DISPLAY_FLAG_PUBLIC = 1
            // VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 16
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            
            virtualDisplay = displayManager.createVirtualDisplay(
                "CoverMirrorDisplay",
                width,
                height,
                320, // densityDpi
                surface,
                flags
            )

            android.util.Log.e("ScreenMirror", "VirtualDisplay Auto-Mirroring started successfully under Shell package Context!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to start VirtualDisplay mirroring", e)
        }
    }

    private fun stopCapture() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay?.surface = null
                Thread.sleep(150) // Give system_server time to process surface detachment
                virtualDisplay?.release()
                virtualDisplay = null
                android.util.Log.e("ScreenMirror", "VirtualDisplay Mirroring stopped and surface cleanly detached.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
