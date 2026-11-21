package vn.autoview.usbcamera

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val HOME_PREFS = "autoview_home"
private const val KEY_SELECTED_PACKAGE = "selected_app_package"
private const val KEY_SELECTED_LABEL = "selected_app_label"
private data class LaunchableApp(val label: String, val packageName: String)

class HomeActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_SETTINGS = "open_autoview_settings" }
    private val homeRoleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    private var pairLaunchInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        enterImmersiveMode()
        val prefs = getSharedPreferences(HOME_PREFS, MODE_PRIVATE)
        setContent {
            AutoViewHome(
                selectedLabel = prefs.getString(KEY_SELECTED_LABEL, null),
                initialShowApps = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false),
                onEnableFloating = { enableFloatingSettings() },
                onApps = { loadLaunchableApps() },
                onSelectApp = { app ->
                    prefs.edit().putString(KEY_SELECTED_PACKAGE, app.packageName)
                        .putString(KEY_SELECTED_LABEL, app.label).apply()
                },
                onStartPair = { startSavedPair() },
                onSetDefaultHome = { requestDefaultHome() }
            )
        }
        if (savedInstanceState == null) {
            Handler(Looper.getMainLooper()).postDelayed({ startSavedPair() }, 900)
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            runCatching { startService(Intent(this, FloatingSettingsService::class.java)) }
        }
    }

    private fun enableFloatingSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        runCatching { startService(Intent(this, FloatingSettingsService::class.java)) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private fun startSavedPair() {
        if (pairLaunchInProgress) return
        val prefs = getSharedPreferences(HOME_PREFS, MODE_PRIVATE)
        val packageName = prefs.getString(KEY_SELECTED_PACKAGE, null) ?: return
        val externalIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        pairLaunchInProgress = true
        try {
            // LAUNCH_ADJACENT asks Android to place the selected app in the
            // neighboring split-screen task. Support depends on device policy.
            // The launch guard is released after the camera launch attempt.
            externalIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )
            startActivity(externalIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val cameraIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                        )
                    }
                    startActivity(cameraIntent)
                } catch (_: Exception) {
                    // Allow retry from the Home screen.
                } finally {
                    Handler(Looper.getMainLooper()).postDelayed({ pairLaunchInProgress = false }, 1800)
                }
            }, 700)
        } catch (_: Exception) {
            pairLaunchInProgress = false
        }
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
                return
            }
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, 0)
            .mapNotNull { ri ->
                val activity = ri.activityInfo ?: return@mapNotNull null
                if (activity.packageName == packageName) return@mapNotNull null
                LaunchableApp(ri.loadLabel(packageManager).toString(), activity.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun AutoViewHome(
    selectedLabel: String?,
    initialShowApps: Boolean,
    onEnableFloating: () -> Unit,
    onApps: () -> List<LaunchableApp>,
    onSelectApp: (LaunchableApp) -> Unit,
    onStartPair: () -> Unit,
    onSetDefaultHome: () -> Unit
) {
    var showApps by remember { mutableStateOf(initialShowApps) }
    var apps by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    var selected by remember { mutableStateOf(selectedLabel) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF101820)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("USB CAMERA AUTOVIEW", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("MÀN HÌNH CHIA ĐÔI TỰ ĐỘNG", color = Color(0xFF9EADB9), fontSize = 12.sp)
                }
                TextButton(onClick = onEnableFloating) {
                    Text("BẬT NÚT CÀI ĐẶT NỔI", color = Color(0xFFB9D8F2))
                }
                TextButton(onClick = onSetDefaultHome) {
                    Text("ĐẶT LÀM HOME MẶC ĐỊNH", color = Color(0xFFB9D8F2))
                }
            }
            if (!showApps) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeTile(
                        "ỨNG DỤNG BÊN TRÁI",
                        selected ?: "Chưa chọn ứng dụng",
                        Modifier.weight(1f)
                    ) {
                        apps = onApps()
                        showApps = true
                    }
                    HomeTile(
                        "CAMERA USB BÊN PHẢI",
                        "Tự mở camera khi bắt đầu",
                        Modifier.weight(1f),
                        onStartPair
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { apps = onApps(); showApps = true }) { Text("CHỌN ỨNG DỤNG") }
                    Button(onClick = onStartPair, enabled = selected != null) { Text("BẮT ĐẦU CHIA ĐÔI") }
                }
                Text(
                    "Chọn ứng dụng một lần. AutoView sẽ ghi nhớ và thử mở lại cùng Camera USB mỗi lần khởi động.",
                    color = Color(0xFF9EADB9), fontSize = 12.sp
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chọn ứng dụng cố định bên trái", color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showApps = false }) { Text("Quay lại", color = Color.White) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(apps) { app ->
                        Box(
                            Modifier.fillMaxWidth().height(64.dp)
                                .background(Color(0xFF243746), RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectApp(app)
                                    selected = app.label
                                    showApps = false
                                }.padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(app.label, color = Color.White, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().background(Color(0xFF243746), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color(0xFFB7C6D2), fontSize = 14.sp)
    }
}
