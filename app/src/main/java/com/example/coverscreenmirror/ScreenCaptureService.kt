package com.example.coverscreenmirror

import android.content.Context
import android.graphics.Rect
import android.os.*
import android.view.Surface
import android.view.SurfaceControl

class ScreenCaptureService(context: Context) : Binder() {
    private var mirrorBinder: IBinder? = null
    
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
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun bypassHiddenApiRestrictions() {
        try {
            val classClass = Class.forName("java.lang.Class")
            val forName = classClass.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = classClass.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                Class.forName("[Ljava.lang.Class;")
            )

            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntimeMethod = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
            val vmRuntime = getRuntimeMethod.invoke(null)

            val setHiddenApiExemptionsMethod = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf(Class.forName("[Ljava.lang.String;"))
            ) as java.lang.reflect.Method

            setHiddenApiExemptionsMethod.invoke(vmRuntime, arrayOf(arrayOf("L")))
            android.util.Log.e("ScreenMirror", "Successfully bypassed Hidden API restrictions in UserService!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to bypass Hidden API restrictions in UserService", e)
        }
    }

    private fun startCapture(surface: Surface?, width: Int, height: Int) {
        if (surface == null) {
            android.util.Log.e("ScreenMirror", "SurfaceCaptureService: Received null surface")
            return
        }
        try {
            stopCapture()
            
            // 1. Create a display mirror binder
            val createDisplayMethod = SurfaceControl::class.java.getDeclaredMethod(
                "createDisplay", 
                String::class.java, 
                java.lang.Boolean.TYPE
            )
            createDisplayMethod.isAccessible = true
            mirrorBinder = createDisplayMethod.invoke(null, "CoverMirrorDisplay", true) as IBinder

            // 2. Set screen projection surface
            val setDisplaySurfaceMethod = SurfaceControl::class.java.getDeclaredMethod(
                "setDisplaySurface", 
                IBinder::class.java, 
                Surface::class.java
            )
            setDisplaySurfaceMethod.isAccessible = true
            setDisplaySurfaceMethod.invoke(null, mirrorBinder, surface)

            // 3. Set physical layer stack (Display 0 has stack 0)
            val setDisplayLayerStackMethod = SurfaceControl::class.java.getDeclaredMethod(
                "setDisplayLayerStack",
                IBinder::class.java,
                java.lang.Integer.TYPE
            )
            setDisplayLayerStackMethod.isAccessible = true
            setDisplayLayerStackMethod.invoke(null, mirrorBinder, 0)

            // 4. Configure screen projection dimensions
            val setDisplaySizeMethod = SurfaceControl::class.java.getDeclaredMethod(
                "setDisplaySize",
                IBinder::class.java,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE
            )
            setDisplaySizeMethod.isAccessible = true
            setDisplaySizeMethod.invoke(null, mirrorBinder, width, height)

            // 5. Configure display projection viewport scaling
            val setDisplayProjectionMethod = SurfaceControl::class.java.getDeclaredMethod(
                "setDisplayProjection",
                IBinder::class.java,
                java.lang.Integer.TYPE,
                Rect::class.java,
                Rect::class.java
            )
            setDisplayProjectionMethod.isAccessible = true
            val layerStackRect = Rect(0, 0, width, height)
            val displayRect = Rect(0, 0, width, height)
            setDisplayProjectionMethod.invoke(null, mirrorBinder, 0, layerStackRect, displayRect)

            android.util.Log.e("ScreenMirror", "SurfaceControl Hardware Mirroring started successfully!")
        } catch (e: Exception) {
            android.util.Log.e("ScreenMirror", "Failed to start SurfaceControl mirroring", e)
        }
    }

    private fun stopCapture() {
        if (mirrorBinder != null) {
            try {
                val destroyDisplayMethod = SurfaceControl::class.java.getDeclaredMethod(
                    "destroyDisplay", 
                    IBinder::class.java
                )
                destroyDisplayMethod.isAccessible = true
                destroyDisplayMethod.invoke(null, mirrorBinder)
                mirrorBinder = null
                android.util.Log.e("ScreenMirror", "SurfaceControl Mirroring stopped.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
