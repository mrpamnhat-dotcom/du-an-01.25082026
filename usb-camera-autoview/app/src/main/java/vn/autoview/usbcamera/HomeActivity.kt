package vn.autoview.usbcamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private data class LaunchableApp(val label: String, val packageName: String, val className: String)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent { AutoViewHome(
            onCamera = { startActivity(Intent(this, MainActivity::class.java)) },
            onApps = { loadLaunchableApps() },
            onLaunchApp = { app ->
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) startActivity(intent)
            },
            onSettings = { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        ) }
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, 0)
            .mapNotNull { ri ->
                val activity = ri.activityInfo ?: return@mapNotNull null
                if (activity.packageName == packageName) return@mapNotNull null
                LaunchableApp(
                    ri.loadLabel(packageManager).toString(),
                    activity.packageName,
                    activity.name
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun AutoViewHome(
    onCamera: () -> Unit,
    onApps: () -> List<LaunchableApp>,
    onLaunchApp: (LaunchableApp) -> Unit,
    onSettings: () -> Unit
) {
    var showApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF101820)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("USB CAMERA AUTOVIEW", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(if (showApps) "ỨNG DỤNG ĐÃ CÀI" else "MÀN HÌNH CHÍNH", color = Color(0xFF9EADB9), fontSize = 12.sp)
                }
                TextButton(onClick = onSettings) { Text("Cài đặt Home", color = Color(0xFFB9D8F2)) }
            }
            if (!showApps) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    HomeTile("MỞ CAMERA", "Xem camera USB và vạch căn lùi", Modifier.weight(1f), onCamera)
                    HomeTile("ỨNG DỤNG", "Danh sách ứng dụng trên điện thoại", Modifier.weight(1f)) {
                        apps = onApps()
                        showApps = true
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chọn ứng dụng để mở", color = Color.White, modifier = Modifier.weight(1f))
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
                                .clickable { onLaunchApp(app) }.padding(12.dp),
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
        modifier.fillMaxHeight().background(Color(0xFF243746), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color(0xFFB7C6D2), fontSize = 14.sp)
    }
}
