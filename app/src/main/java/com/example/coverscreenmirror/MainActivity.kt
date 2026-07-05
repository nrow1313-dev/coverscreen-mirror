package com.example.coverscreenmirror

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.coverscreenmirror.theme.CoverScreenMirrorTheme
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

class MainActivity : ComponentActivity() {

    private var targetGoToHome = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startMirrorService(result.resultCode, result.data!!)
            launchCoverScreenActivity("MIRRORING")
        } else {
            Toast.makeText(this, "Permission denied for screen capture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoverScreenMirrorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFF121212) // Dark background
                ) {
                    AppScreen(
                        activity = this,
                        onStartMirror = { goToHome -> startMirroring(goToHome) },
                        onStopMirror = { stopMirroring() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            this.display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        if (currentDisplay?.displayId == 0) {
            CoverScreenAccessibilityService.instance?.showNavigationBar(false)
            thread {
                try {
                    if (Shizuku.pingBinder()) {
                        val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        method.isAccessible = true
                        val process = method.invoke(null, arrayOf("sh", "-c", "wm size -d 1 reset"), null, null) as Process
                        process.waitFor()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startMirroring(goToHome: Boolean = false) {
        targetGoToHome = goToHome
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                mpm.createScreenCaptureIntent(config)
            } catch (e: Throwable) {
                mpm.createScreenCaptureIntent()
            }
        } else {
            mpm.createScreenCaptureIntent()
        }
        screenCaptureLauncher.launch(intent)
    }

    private fun stopMirroring() {
        stopService(Intent(this, ScreenMirrorService::class.java))
        CoverScreenAccessibilityService.instance?.showNavigationBar(false)
        Toast.makeText(this, "Đã dừng trình chiếu", Toast.LENGTH_SHORT).show()
        thread {
            try {
                if (Shizuku.pingBinder()) {
                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true
                    // 1. Reset Display 1 override size
                    var process = method.invoke(null, arrayOf("sh", "-c", "wm size -d 1 reset"), null, null) as Process
                    process.waitFor()
                    // 2. Cancel device state overrides
                    process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 0 && sleep 0.1 && cmd device_state cancel"), null, null) as Process
                    process.waitFor()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startMirrorService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA", data)
        }
        startForegroundService(serviceIntent)
    }

    fun launchCoverScreenActivity(mode: String) {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val coverDisplay = displayManager.getDisplay(1) ?: displayManager.displays.firstOrNull { it.displayId != 0 }
        
        if (coverDisplay != null) {
            try {
                val options = android.app.ActivityOptions.makeBasic()
                options.launchDisplayId = coverDisplay.displayId
                val intent = Intent(this, CoverScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("MODE", mode)
                }
                startActivity(intent, options.toBundle())
                android.util.Log.e("ScreenMirror", "CoverScreenActivity launched via ActivityOptions on display ${coverDisplay.displayId} with mode $mode")
                Toast.makeText(this, "Đã bắt đầu trình chiếu màn hình phụ!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("ScreenMirror", "Failed to launch via ActivityOptions", e)
            }
        } else {
            android.util.Log.e("ScreenMirror", "Cover display not found")
        }

        // Apply device_state state 4 in background if Shizuku is running
        thread {
            try {
                if (Shizuku.pingBinder()) {
                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true
                    method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 4"), null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        super.onDestroy()
    }
}

@Composable
fun AppScreen(activity: ComponentActivity, onStartMirror: (Boolean) -> Unit, onStopMirror: () -> Unit) {
    val prefs = activity.getSharedPreferences("mirror_prefs", Context.MODE_PRIVATE)
    var controlMode by remember { mutableStateOf(prefs.getString("control_mode", "shizuku") ?: "shizuku") }
    
    var shizukuAvailable by remember { mutableStateOf(Shizuku.pingBinder()) }
    var hasShizukuPermission by remember { mutableStateOf(checkShizukuPermission()) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(activity)) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var targetGoToHome by remember { mutableStateOf(false) }

    LaunchedEffect(controlMode) {
        prefs.edit().putString("control_mode", controlMode).apply()
    }

    LaunchedEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            shizukuAvailable = true
            hasShizukuPermission = checkShizukuPermission()
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuAvailable = false
            hasShizukuPermission = false
        }
        val requestPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                hasShizukuPermission = true
            }
        }
        
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionListener)
        
        if (shizukuAvailable && !hasShizukuPermission) {
            try { Shizuku.requestPermission(0) } catch (e: Exception) {}
        }
    }

    LaunchedEffect(hasShizukuPermission) {
        if (hasShizukuPermission) {
            thread {
                try {
                    val appOps = activity.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(
                            "android:project_media",
                            android.os.Process.myUid(),
                            activity.packageName
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(
                            "android:project_media",
                            android.os.Process.myUid(),
                            activity.packageName
                        )
                    }
                    if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                        android.util.Log.e("ScreenMirror", "PROJECT_MEDIA not allowed, granting via Shizuku...")
                        val cmd = "appops set com.example.coverscreenmirror PROJECT_MEDIA allow"
                        val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        method.isAccessible = true
                        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                        process.waitFor()
                    } else {
                        android.util.Log.e("ScreenMirror", "PROJECT_MEDIA already allowed, skipping Shizuku command")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Xác nhận trình chiếu",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF1E1E1E)
                )
            },
            text = {
                Text(
                    text = "Hệ thống sẽ kích hoạt đồng thời cả hai màn hình (Dual Screen) để truyền tải hình ảnh chính xác. Bạn có muốn bắt đầu?",
                    color = Color(0xFF616161)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        thread {
                            try {
                                if (Shizuku.pingBinder()) {
                                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                                    method.isAccessible = true
                                    val process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 4"), null, null) as Process
                                    process.waitFor()
                                    Thread.sleep(500) // Delay 500ms to let display state stabilize
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            activity.runOnUiThread {
                                onStartMirror(targetGoToHome)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("CÓ", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        thread {
                            try {
                                if (Shizuku.pingBinder()) {
                                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                                    method.isAccessible = true
                                    val process = method.invoke(null, arrayOf("sh", "-c", "cmd device_state cancel"), null, null) as Process
                                    process.waitFor()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("KHÔNG", color = Color.White)
                }
            },
            containerColor = Color(0xEEFFFFFF),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        )
    }

    // Refresh accessibility state when screen resumes
    DisposableEffect(Unit) {
        val listener = android.app.Application.ActivityLifecycleCallbacks::class // Not needed, just run check on recomposition
        onDispose {}
    }
    accessibilityEnabled = isAccessibilityServiceEnabled(activity)

    // Minimalist light background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Mirror Screen",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF111111),
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Phản chiếu & điều khiển màn hình phụ",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF757575)
            )
            
            Spacer(modifier = Modifier.height(40.dp))

            // Minimalist monochrome Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFFFF)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFE5E5E5)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CHẾ ĐỘ ĐIỀU KHIỂN",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF111111),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (controlMode == "accessibility"),
                            onClick = { controlMode = "accessibility" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF111111),
                                unselectedColor = Color(0xFFB0B0B0)
                            )
                        )
                        Text(
                            text = "Trợ năng",
                            color = Color(0xFF111111),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(48.dp))
                        
                        RadioButton(
                            selected = (controlMode == "shizuku"),
                            onClick = { controlMode = "shizuku" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF111111),
                                unselectedColor = Color(0xFFB0B0B0)
                            )
                        )
                        Text(
                            text = "Shizuku",
                            color = Color(0xFF111111),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Status section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (controlMode == "shizuku") {
                            if (shizukuAvailable && hasShizukuPermission) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF2E7D32), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Shizuku sẵn sàng và đã cấp quyền",
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFC62828), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (shizukuAvailable) "Shizuku chưa được cấp quyền" else "Shizuku chưa khởi chạy",
                                        color = Color(0xFFC62828),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                    if (shizukuAvailable) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { try { Shizuku.requestPermission(0) } catch (e: Exception) {} },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text("Cấp quyền Shizuku", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (accessibilityEnabled) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF2E7D32), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dịch vụ trợ năng hoạt động",
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFC62828), androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Chưa bật dịch vụ trợ năng",
                                        color = Color(0xFFC62828),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                activity.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(activity, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Bật trong Cài đặt", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons (Matte Black and Minimalist Light Gray)
            Button(
                onClick = {
                    targetGoToHome = false
                    showConfirmDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111111) // Matte Black
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "BẮT ĐẦU TRÌNH CHIẾU",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
                    val resolveInfo = activity.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    val launcherPackage = resolveInfo?.activityInfo?.packageName ?: "com.sec.android.app.launcher"
                    val launcherActivity = resolveInfo?.activityInfo?.name ?: "com.sec.android.app.launcher.Launcher"
                    
                    thread {
                        try {
                            if (Shizuku.pingBinder()) {
                                val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                                method.isAccessible = true
                                // 1. Force dual display mode
                                var proc = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 4"), null, null) as Process
                                proc.waitFor()
                                Thread.sleep(500)
                                // 2. Override Display 1 size to portrait
                                proc = method.invoke(null, arrayOf("sh", "-c", "wm size -d 1 540x1100"), null, null) as Process
                                proc.waitFor()
                                Thread.sleep(300)
                                // 3. Launch default launcher on Display 1
                                val cmd = "am start -n $launcherPackage/$launcherActivity --display 1"
                                proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                                proc.waitFor()
                                
                                activity.runOnUiThread {
                                    CoverScreenAccessibilityService.instance?.showNavigationBar(true)
                                    Toast.makeText(activity, "Đã mở màn gốc tỉ lệ dọc ở màn ngoài!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                activity.runOnUiThread {
                                    Toast.makeText(activity, "Chế độ này yêu cầu Shizuku!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111111) // Matte Black
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "MỞ MÀN HÌNH CHÍNH GỐC",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStopMirror,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0F0F0) // Off-white / light gray
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
            ) {
                Text(
                    text = "DỪNG TRÌNH CHIẾU",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF111111),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun checkShizukuPermission(): Boolean {
    if (!Shizuku.pingBinder()) return false
    return if (Shizuku.isPreV11()) {
        false
    } else {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = android.content.ComponentName(context, CoverScreenAccessibilityService::class.java)
    val enabledServicesSetting = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}
