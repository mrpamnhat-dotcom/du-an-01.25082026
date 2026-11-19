package vn.autoview.usbcamera

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mikimn.droiduvc.compose.UvcCameraPreview
import com.mikimn.droiduvc.compose.UvcCameraState
import com.mikimn.droiduvc.compose.rememberIsStarted
import com.mikimn.droiduvc.compose.rememberUsbPermission
import com.mikimn.droiduvc.compose.rememberUvcCamera
import com.mikimn.droiduvc.compose.rememberUvcDevices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        setContent { MaterialTheme { CameraScreen() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
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
private fun CameraScreen() {
    val devices by rememberUvcDevices()
    val device = devices.firstOrNull()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (device == null) {
            Text("CHƯA KẾT NỐI CAMERA USB", color = Color.White)
        } else {
            CameraDevicePreview(device)
        }
    }
}

@Composable
private fun CameraDevicePreview(device: UsbDevice) {
    val permission = rememberUsbPermission(device)
    if (!permission.granted) {
        LaunchedEffect(device.deviceId) { permission.request() }
        Text("ĐANG CHỜ CẤP QUYỀN CAMERA USB", color = Color.White)
        return
    }
    if (!rememberIsStarted()) return
    when (val state = rememberUvcCamera(device)) {
        UvcCameraState.Opening ->
            Text("ĐANG MỞ CAMERA…", color = Color.White)
        is UvcCameraState.Failed ->
            Text("KHÔNG MỞ ĐƯỢC CAMERA\n${state.message}", color = Color.White)
        is UvcCameraState.Ready ->
            UvcCameraPreview(camera = state.camera, modifier = Modifier.fillMaxSize())
    }
}
