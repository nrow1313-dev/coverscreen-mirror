package com.example.coverscreenmirror

import android.content.Context
import android.os.*
import android.view.Surface
import android.view.SurfaceControl

class ScreenCaptureService(context: Context) : Binder() {
    private var mirrorBinder: IBinder? = null
    
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
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun startCapture(surface: Surface?, width: Int, height: Int) {
        if (surface == null) {
            android.util.Log.e("ScreenMirror", "SurfaceCaptureService: Received null surface")
            return
        }
        try {
            stopCapture()
            
            // 1. Create a display mirror binder (boolean primitive type)
            val createDisplayMethod = SurfaceControl::class.java.getMethod(
                "createDisplay", 
                String::class.java, 
                java.lang.Boolean.TYPE
            )
            mirrorBinder = createDisplayMethod.invoke(null, "CoverMirrorDisplay", true) as IBinder

            // 2. Set screen projection surface
            val setDisplaySurfaceMethod = SurfaceControl::class.java.getMethod(
                "setDisplaySurface", 
                IBinder::class.java, 
                Surface::class.java
            )
            setDisplaySurfaceMethod.invoke(null, mirrorBinder, surface)

            // 3. Set physical layer stack (int primitive type)
            val setDisplayLayerStackMethod = SurfaceControl::class.java.getMethod(
                "setDisplayLayerStack",
                IBinder::class.java,
                java.lang.Integer.TYPE
            )
            setDisplayLayerStackMethod.invoke(null, mirrorBinder, 0)

            // 4. Configure screen projection dimensions (int primitive types)
            val setDisplaySizeMethod = SurfaceControl::class.java.getMethod(
                "setDisplaySize",
                IBinder::class.java,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE
            )
            setDisplaySizeMethod.invoke(null, mirrorBinder, width, height)

            android.util.Log.e("ScreenMirror", "SurfaceControl Hardware Mirroring started successfully!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to start SurfaceControl mirroring", e)
        }
    }

    private fun stopCapture() {
        if (mirrorBinder != null) {
            try {
                val destroyDisplayMethod = SurfaceControl::class.java.getMethod(
                    "destroyDisplay", 
                    IBinder::class.java
                )
                destroyDisplayMethod.invoke(null, mirrorBinder)
                mirrorBinder = null
                android.util.Log.e("ScreenMirror", "SurfaceControl Mirroring stopped.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
