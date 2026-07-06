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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable

class MainActivity : ComponentActivity() {

    private var targetGoToHome = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startMirrorService(result.resultCode, result.data!!)
            launchCoverScreenActivity("MIRRORING")
            
            if (targetGoToHome) {
                thread {
                    try {
                        Thread.sleep(800) // Wait 800ms to let projection activate completely
                    } catch (e: Exception) {}
                    runOnUiThread {
                        try {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(homeIntent)
                            android.util.Log.e("ScreenMirror", "Sent HOME intent to minimize app")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
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
    var showMainConfirmDialog by remember { mutableStateOf(false) }
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

    if (showMainConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMainConfirmDialog = false },
            title = { Text("Xác nhận Màn Chính") },
            text = { Text("Bạn có muốn khởi động chế độ Màn Chính không? Tính năng này sẽ tự động dọn dẹp hệ thống trước khi chạy để tránh lỗi.") },
            containerColor = Color(0xFFF2F2F7), // iOS System Background
            titleContentColor = Color.Black,
            textContentColor = Color.DarkGray,
            confirmButton = {
                TextButton(
                    onClick = {
                        showMainConfirmDialog = false
                        thread {
                            try {
                                if (Shizuku.pingBinder()) {
                                    val method = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                                    method.isAccessible = true
                                    
                                    // 1. Cleanup zombies
                                    val cleanupProc = method.invoke(null, arrayOf("sh", "-c", "pkill -f mirror_service"), null, null) as Process
                                    cleanupProc.waitFor()
                                    Thread.sleep(200)

                                    // 2. Unfold state
                                    val proc = method.invoke(null, arrayOf("sh", "-c", "cmd device_state state 4"), null, null) as Process
                                    proc.waitFor()
                                    Thread.sleep(500)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            activity.runOnUiThread {
                                (activity as? MainActivity)?.launchCoverScreenActivity("SILENT_MIRRORING")
                            }
                        }
                    }
                ) {
                    Text("Có", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMainConfirmDialog = false
                }) {
                    Text("Không", color = Color.Black)
                }
            }
        )
    }

    accessibilityEnabled = isAccessibilityServiceEnabled(activity)

    // Premium light-themed, scrollable and compact layout
    val scrollState = androidx.compose.foundation.rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7)) // iOS Light Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Push layout down a bit
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header Row: Title & Status Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mirror Screen",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Mở màn chính ở bên ngoài",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray
                    )
                }
                
                // Status Pill in Top-Right
                val isReady = if (controlMode == "shizuku") {
                    shizukuAvailable && hasShizukuPermission
                } else {
                    accessibilityEnabled
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isReady) Color(0x2234C759) else Color(0x22FF3B30),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isReady) Color(0xFF34C759) else Color(0xFFFF3B30),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isReady) "Sẵn sàng" else "Chưa bật",
                            color = if (isReady) Color(0xFF34C759) else Color(0xFFFF8282),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Premium Control Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White // Pure white card
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFE5E5EA) // Light border
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CHẾ ĐỘ ĐIỀU KHIỂN",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.DarkGray,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (controlMode == "accessibility"),
                            onClick = { controlMode = "accessibility" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color.Black, // Monochrome
                                unselectedColor = Color(0xFFC7C7CC)
                            )
                        )
                        Text(
                            text = "Trợ năng",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clickable { controlMode = "accessibility" }
                        )
                        
                        Spacer(modifier = Modifier.width(32.dp))
                        
                        RadioButton(
                            selected = (controlMode == "shizuku"),
                            onClick = { controlMode = "shizuku" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color.Black, // Monochrome
                                unselectedColor = Color(0xFFC7C7CC)
                            )
                        )
                        Text(
                            text = "Shizuku",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clickable { controlMode = "shizuku" }
                        )
                    }

                    // Dynamic warning and settings triggers
                    if (controlMode == "shizuku" && (!shizukuAvailable || !hasShizukuPermission)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE5E5EA))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (shizukuAvailable) "Shizuku chưa cấp quyền" else "Shizuku chưa hoạt động",
                                    color = Color(0xFFFF453A),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = if (shizukuAvailable) "Vui lòng cho phép ứng dụng truy cập Shizuku" else "Hãy khởi động Shizuku trên điện thoại",
                                    color = Color(0xFF8E8E93),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (shizukuAvailable) {
                                Button(
                                    onClick = { try { Shizuku.requestPermission(0) } catch (e: Exception) {} },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Cấp quyền", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else if (controlMode == "accessibility" && !accessibilityEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE5E5EA))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dịch vụ trợ năng chưa bật",
                                    color = Color(0xFFFF453A),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = "Yêu cầu quyền trợ năng để phản hồi cử chỉ vuốt chạm",
                                    color = Color(0xFF8E8E93),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        activity.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(activity, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Bật trong Cài đặt", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons in a single horizontal row (3 buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Phản chiếu
                Button(
                    onClick = {
                        targetGoToHome = false
                        showConfirmDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black // Monochrome
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Phản chiếu",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Button 2: Màn Chính
                Button(
                    onClick = {
                        showMainConfirmDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black // Monochrome
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Màn Chính",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Button 3: Dừng
                Button(
                    onClick = onStopMirror,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White // Monochrome inverted
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Dừng",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black, // Black text
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1
                    )
                }
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
