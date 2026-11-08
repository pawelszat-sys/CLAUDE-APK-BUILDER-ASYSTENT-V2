package app.asystent.mieszkania

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.ui.AppRoot
import app.asystent.mieszkania.ui.AsystentTheme

class MainActivity : ComponentActivity() {
    private val vm by lazy { Hub.get(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AsystentTheme {
                val kiosk by vm.kiosk.collectAsStateWithLifecycle()
                val claps by vm.clapsOn.collectAsStateWithLifecycle()
                LaunchedEffect(kiosk, claps) { applyWindow(kiosk, claps) }
                AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        vm.onForeground(granted)
        vm.ensureServer()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    /** Ekran nie gaśnie w trybie kiosku i podczas nasłuchu klaśnięć. */
    private fun applyWindow(kiosk: Boolean, listening: Boolean) {
        if (kiosk || listening) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (kiosk) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
