package vn.autoview.usbcamera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikimn.droiduvc.compose.UvcCameraPreview
import com.mikimn.droiduvc.compose.UvcCameraState
import com.mikimn.droiduvc.compose.rememberIsStarted
import com.mikimn.droiduvc.compose.rememberUsbPermission
import com.mikimn.droiduvc.compose.rememberUvcCamera
import com.mikimn.droiduvc.compose.rememberUvcDevices
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min

private const val PREFS = "autoview_settings"
private const val KEY_MIRROR = "mirror"
private const val KEY_GUIDE = "guide_config"
private const val KEY_FORCE_LANDSCAPE = "force_landscape"
private const val KEY_PREV_ACCEL = "previous_accelerometer_rotation"
private const val KEY_PREV_ROTATION = "previous_user_rotation"
private const val KEY_HAS_PREVIOUS = "has_previous_rotation"

private val GuideGreen = Color(0xFF22D66B)
private val GuideYellow = Color(0xFFFFD52A)
private val GuideRed = Color(0xFFFF4545)

data class GuideConfig(
    val leftTopX: Float = 0.37f,
    val leftTopY: Float = 0.29f,
    val rightTopX: Float = 0.63f,
    val rightTopY: Float = 0.29f,
    val leftBottomX: Float = 0.12f,
    val leftBottomY: Float = 0.91f,
    val rightBottomX: Float = 0.88f,
    val rightBottomY: Float = 0.91f,
    val firstDivider: Float = 0.31f,
    val secondDivider: Float = 0.66f,
    val topTickLength: Float = 0.045f,
    val dividerTickLength: Float = 0.065f
) {
    fun encode(): String = listOf(
        leftTopX, leftTopY, rightTopX, rightTopY,
        leftBottomX, leftBottomY, rightBottomX, rightBottomY,
        firstDivider, secondDivider, topTickLength, dividerTickLength
    ).joinToString(",")

    companion object {
        fun decode(value: String?): GuideConfig {
            val v = value?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return GuideConfig()
            if (v.size != 12) return GuideConfig()
            return GuideConfig(
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8].coerceIn(0.12f, 0.55f), v[9].coerceIn(0.45f, 0.88f),
                v[10].coerceIn(0.015f, 0.12f), v[11].coerceIn(0.02f, 0.16f)
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        setContent { MaterialTheme { CameraApp() } }
    }

    override fun onResume() {
        super.onResume()
        applyGlobalLandscapeIfEnabled()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun applyGlobalLandscapeIfEnabled() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FORCE_LANDSCAPE, false) && Settings.System.canWrite(this)) {
            runCatching {
                if (!prefs.getBoolean(KEY_HAS_PREVIOUS, false)) {
                    prefs.edit()
                        .putInt(KEY_PREV_ACCEL, Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1))
                        .putInt(KEY_PREV_ROTATION, Settings.System.getInt(contentResolver, Settings.System.USER_ROTATION, 0))
                        .putBoolean(KEY_HAS_PREVIOUS, true)
                        .apply()
                }
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, 1)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}

@Composable
private fun CameraApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var mirror by remember { mutableStateOf(prefs.getBoolean(KEY_MIRROR, false)) }
    var guide by remember { mutableStateOf(GuideConfig.decode(prefs.getString(KEY_GUIDE, null))) }
    var forceLandscape by remember { mutableStateOf(prefs.getBoolean(KEY_FORCE_LANDSCAPE, false)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var editingGuide by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(mirror, guide, forceLandscape) {
        prefs.edit()
            .putBoolean(KEY_MIRROR, mirror)
            .putString(KEY_GUIDE, guide.encode())
            .putBoolean(KEY_FORCE_LANDSCAPE, forceLandscape)
            .apply()
    }
    LaunchedEffect(controlsVisible, settingsOpen) {
        if (controlsVisible && !settingsOpen) {
            delay(3500)
            controlsVisible = false
        }
    }

    if (settingsOpen) {
        SettingsScreen(
            mirror = mirror,
            onMirrorChange = { mirror = it },
            forceLandscape = forceLandscape,
            onForceLandscapeChange = { enabled ->
                forceLandscape = enabled
                if (enabled && !Settings.System.canWrite(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else if (Settings.System.canWrite(context)) {
                    runCatching {
                        if (enabled) {
                            if (!prefs.getBoolean(KEY_HAS_PREVIOUS, false)) {
                                prefs.edit()
                                    .putInt(KEY_PREV_ACCEL, Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1))
                                    .putInt(KEY_PREV_ROTATION, Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0))
                                    .putBoolean(KEY_HAS_PREVIOUS, true)
                                    .apply()
                            }
                            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, 1)
                        } else if (prefs.getBoolean(KEY_HAS_PREVIOUS, false)) {
                            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, prefs.getInt(KEY_PREV_ACCEL, 1))
                            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, prefs.getInt(KEY_PREV_ROTATION, 0))
                            prefs.edit().putBoolean(KEY_HAS_PREVIOUS, false).apply()
                        }
                    }
                }
            },
            guide = guide,
            onGuideChange = { guide = it },
            onClose = { settingsOpen = false }
        )
    } else {
        CameraScreen(
            mirror = mirror,
            guide = guide,
            editingGuide = editingGuide,
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            onOpenSettings = { settingsOpen = true; controlsVisible = true },
            onToggleGuideEdit = { editingGuide = !editingGuide; controlsVisible = true },
            onGuideChange = { guide = it }
        )
    }
}

@Composable
private fun SettingsScreen(
    mirror: Boolean,
    onMirrorChange: (Boolean) -> Unit,
    forceLandscape: Boolean,
    onForceLandscapeChange: (Boolean) -> Unit,
    guide: GuideConfig,
    onGuideChange: (GuideConfig) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101318)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CÀI ĐẶT CAMERA", color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose) { Text("Đóng") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lật ảnh ngang", color = Color.White)
                Text("Phản chiếu hình ảnh trái – phải", color = Color.LightGray, fontSize = 12.sp)
            }
            Switch(checked = mirror, onCheckedChange = onMirrorChange)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Xoay ngang toàn điện thoại", color = Color.White)
                Text("Cần cấp quyền sửa đổi cài đặt hệ thống Android", color = Color.LightGray, fontSize = 12.sp)
            }
            Switch(checked = forceLandscape, onCheckedChange = onForceLandscapeChange)
        }
        Text("VẠCH CĂN LÙI", color = Color.White, fontSize = 18.sp)
        Text("Chỉnh bốn góc và hai mức chia. Hai vạch ngang trái/phải luôn nằm cùng cao độ; không có vạch đỏ nối ngang ở cuối.", color = Color.LightGray, fontSize = 13.sp)
        GuideEditor(guide = guide, onGuideChange = onGuideChange)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onGuideChange(GuideConfig()) }) { Text("Mặc định") }
        }
    }
}

@Composable
private fun GuideEditor(guide: GuideConfig, onGuideChange: (GuideConfig) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF202A33), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        GuideOverlay(
            guide = guide,
            editing = true,
            modifier = Modifier.fillMaxSize(),
            onGuideChange = onGuideChange
        )
    }
}

@Composable
private fun CameraScreen(
    mirror: Boolean,
    guide: GuideConfig,
    editingGuide: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleGuideEdit: () -> Unit,
    onGuideChange: (GuideConfig) -> Unit
) {
    val devices by rememberUvcDevices()
    val device = devices.firstOrNull()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (device == null) {
            Text("CHƯA KẾT NỐI CAMERA USB", color = Color.White)
        } else {
            CameraDevicePreview(device, mirror, guide, editingGuide, onGuideChange)
        }

        if (controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onToggleGuideEdit) {
                    Text(if (editingGuide) "Xong chỉnh vạch" else "Chỉnh vạch")
                }
                Button(onClick = onOpenSettings) { Text("Cài đặt") }
                Button(onClick = onToggleControls) { Text("Ẩn") }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) onToggleControls()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CameraDevicePreview(
    device: android.hardware.usb.UsbDevice,
    mirror: Boolean,
    guide: GuideConfig,
    editingGuide: Boolean,
    onGuideChange: (GuideConfig) -> Unit
) {
    val permission = rememberUsbPermission(device)
    if (!permission.granted) {
        LaunchedEffect(device.deviceId) { permission.request() }
        Text("ĐANG CHỜ CẤP QUYỀN CAMERA USB", color = Color.White)
        return
    }
    if (!rememberIsStarted()) return
    when (val state = rememberUvcCamera(device)) {
        UvcCameraState.Opening -> Text("ĐANG MỞ CAMERA…", color = Color.White)
        is UvcCameraState.Failed -> Text("KHÔNG MỞ ĐƯỢC CAMERA\n${state.message}", color = Color.White)
        is UvcCameraState.Ready -> {
            val bestMode = state.modes.maxByOrNull { it.width.toLong() * it.height.toLong() }
            val ratio = bestMode?.let { it.width.toFloat() / it.height.toFloat() } ?: (16f / 9f)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val previewWidth = min(maxWidth.value, maxHeight.value * ratio).dp
                val previewHeight = (previewWidth.value / ratio).dp
                Box(Modifier.width(previewWidth).height(previewHeight)) {
                    UvcCameraPreview(
                        camera = state.camera,
                        modifier = Modifier.fillMaxSize().then(
                            if (mirror) Modifier.graphicsLayerCompatMirror() else Modifier
                        ),
                        requestedMode = bestMode
                    )
                    GuideOverlay(
                        guide = guide,
                        editing = editingGuide,
                        modifier = Modifier.fillMaxSize(),
                        onGuideChange = onGuideChange
                    )
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerCompatMirror(): Modifier =
    this.then(Modifier.graphicsLayer(scaleX = -1f, scaleY = 1f))

@Composable
private fun GuideOverlay(
    guide: GuideConfig,
    editing: Boolean,
    modifier: Modifier = Modifier,
    onGuideChange: (GuideConfig) -> Unit = {}
) {
    val latestGuide = rememberUpdatedState(guide)
    Canvas(
        modifier = modifier.pointerInput(editing) {
            if (editing) {
                var activeHandle = -1
                detectDragGestures(
                    onDragStart = { start ->
                        val handles = guideHandles(latestGuide.value, size.width.toFloat(), size.height.toFloat())
                        val nearest = handles.indices.minByOrNull { i ->
                            hypot(handles[i].x - start.x, handles[i].y - start.y)
                        }
                        activeHandle = nearest?.takeIf {
                            hypot(handles[it].x - start.x, handles[it].y - start.y) <= 80f
                        } ?: -1
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (activeHandle >= 0) {
                            val dx = drag.x / size.width
                            val dy = drag.y / size.height
                            onGuideChange(updateGuideHandle(latestGuide.value, activeHandle, dx, dy))
                        }
                    },
                    onDragEnd = { activeHandle = -1 },
                    onDragCancel = { activeHandle = -1 }
                )
            }
        }
    ) {
        val w = size.width
        val h = size.height
        fun point(x: Float, y: Float) = Offset(x * w, y * h)
        val lt = point(guide.leftTopX, guide.leftTopY)
        val rt = point(guide.rightTopX, guide.rightTopY)
        val lb = point(guide.leftBottomX, guide.leftBottomY)
        val rb = point(guide.rightBottomX, guide.rightBottomY)
        fun at(a: Offset, b: Offset, t: Float) = Offset(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t
        )
        // A shared horizontal level is used for each color transition.
        // Intersections are recalculated on each rail at that exact Y, so
        // the colored segments and their paired horizontal ticks meet cleanly.
        fun xAtY(a: Offset, b: Offset, y: Float): Float {
            val dy = b.y - a.y
            val t = if (kotlin.math.abs(dy) < 0.001f) 0f else ((y - a.y) / dy).coerceIn(0f, 1f)
            return a.x + (b.x - a.x) * t
        }
        val topY = (lt.y + rt.y) / 2f
        val firstY = (at(lt, lb, guide.firstDivider).y + at(rt, rb, guide.firstDivider).y) / 2f
        val secondY = (at(lt, lb, guide.secondDivider).y + at(rt, rb, guide.secondDivider).y) / 2f
        val leftFirst = Offset(xAtY(lt, lb, firstY), firstY)
        val rightFirst = Offset(xAtY(rt, rb, firstY), firstY)
        val leftSecond = Offset(xAtY(lt, lb, secondY), secondY)
        val rightSecond = Offset(xAtY(rt, rb, secondY), secondY)

        drawLine(GuideGreen, lt, leftFirst, strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideYellow, leftFirst, leftSecond, strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideRed, leftSecond, lb, strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideGreen, rt, rightFirst, strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideYellow, rightFirst, rightSecond, strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideRed, rightSecond, rb, strokeWidth = 5f, cap = StrokeCap.Round)

        val leftTopAtY = xAtY(lt, lb, topY)
        val rightTopAtY = xAtY(rt, rb, topY)
        drawLine(GuideGreen, Offset(leftTopAtY, topY), Offset((leftTopAtY + guide.topTickLength * w).coerceAtMost(w), topY), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideGreen, Offset(rightTopAtY, topY), Offset((rightTopAtY - guide.topTickLength * w).coerceAtLeast(0f), topY), strokeWidth = 5f, cap = StrokeCap.Round)

        drawLine(GuideYellow, leftFirst, Offset((leftFirst.x + guide.dividerTickLength * w).coerceAtMost(w), firstY), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideYellow, rightFirst, Offset((rightFirst.x - guide.dividerTickLength * w).coerceAtLeast(0f), firstY), strokeWidth = 5f, cap = StrokeCap.Round)

        drawLine(GuideRed, leftSecond, Offset((leftSecond.x + guide.dividerTickLength * w).coerceAtMost(w), secondY), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(GuideRed, rightSecond, Offset((rightSecond.x - guide.dividerTickLength * w).coerceAtLeast(0f), secondY), strokeWidth = 5f, cap = StrokeCap.Round)

        if (editing) {
            val handles = guideHandles(guide, w, h)
            handles.forEachIndexed { index, p ->
                val color = if (index < 4) Color(0xFF1685FF) else Color.White
                drawCircle(color, radius = if (index < 4) 12f else 10f, center = p)
                drawCircle(Color.White, radius = 16f, center = p, style = Stroke(width = 3f))
            }
        }
    }
}

private fun guideHandles(g: GuideConfig, w: Float, h: Float): List<Offset> {
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    fun xAtY(a: Offset, b: Offset, y: Float): Float {
        val dy = b.y - a.y
        val t = if (kotlin.math.abs(dy) < 0.001f) 0f else ((y - a.y) / dy).coerceIn(0f, 1f)
        return a.x + (b.x - a.x) * t
    }
    val lt = p(g.leftTopX, g.leftTopY)
    val rt = p(g.rightTopX, g.rightTopY)
    val lb = p(g.leftBottomX, g.leftBottomY)
    val rb = p(g.rightBottomX, g.rightBottomY)
    val firstY = (lt.y + (lb.y - lt.y) * g.firstDivider + rt.y + (rb.y - rt.y) * g.firstDivider) / 2f
    val secondY = (lt.y + (lb.y - lt.y) * g.secondDivider + rt.y + (rb.y - rt.y) * g.secondDivider) / 2f
    // Each divider has two linked handles, one on each rail. Dragging either
    // handle moves the shared divider level and therefore both junctions.
    return listOf(
        lt, rt, lb, rb,
        Offset(xAtY(lt, lb, firstY), firstY),
        Offset(xAtY(rt, rb, firstY), firstY),
        Offset(xAtY(lt, lb, secondY), secondY),
        Offset(xAtY(rt, rb, secondY), secondY)
    )
}

private fun updateGuideHandle(g: GuideConfig, index: Int, dx: Float, dy: Float): GuideConfig {
    val clampX: (Float) -> Float = { it.coerceIn(0.02f, 0.98f) }
    val clampY: (Float) -> Float = { it.coerceIn(0.04f, 0.97f) }
    // Divider handles are linked in pairs. Either left or right handle adjusts
    // the same divider parameter, so the opposite junction follows immediately.
    val averageRailHeight = ((g.leftBottomY - g.leftTopY) + (g.rightBottomY - g.rightTopY)) / 2f
    val dividerDelta = if (kotlin.math.abs(averageRailHeight) < 0.001f) 0f else dy / averageRailHeight
    return when (index) {
        0 -> g.copy(leftTopX = clampX(g.leftTopX + dx), leftTopY = clampY(g.leftTopY + dy))
        1 -> g.copy(rightTopX = clampX(g.rightTopX + dx), rightTopY = clampY(g.rightTopY + dy))
        2 -> g.copy(leftBottomX = clampX(g.leftBottomX + dx), leftBottomY = clampY(g.leftBottomY + dy))
        3 -> g.copy(rightBottomX = clampX(g.rightBottomX + dx), rightBottomY = clampY(g.rightBottomY + dy))
        4, 5 -> g.copy(firstDivider = (g.firstDivider + dividerDelta).coerceIn(0.12f, g.secondDivider - 0.08f))
        6, 7 -> g.copy(secondDivider = (g.secondDivider + dividerDelta).coerceIn(g.firstDivider + 0.08f, 0.88f))
        else -> g
    }
}
