package app.asystent.mieszkania.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.asystent.mieszkania.Hub
import app.asystent.mieszkania.R
import fi.iki.elonen.NanoHTTPD

/** Usługa pierwszoplanowa: serwer działa także wtedy, gdy ekran aplikacji jest zamknięty. */
class ServerService : Service() {
    private var server: WebServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Serwer WWW", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Asystent Mieszkania")
            .setContentText("Serwer WWW działa")
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification(), type)
        val hub = Hub.get(application)
        if (server == null) {
            try {
                val port = hub.settings.serverPort.trim().toIntOrNull() ?: 8080
                val page = assets.open("web/index.html").bufferedReader().use { it.readText() }
                val s = WebServer(hub, page, port)
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                hub.onServerStarted(port)
            } catch (e: Exception) {
                hub.onServerStopped(e.message ?: "nie udało się uruchomić")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        Hub.get(application).onServerStopped()
        super.onDestroy()
    }

    companion object {
        private const val CH = "server"
    }
}
