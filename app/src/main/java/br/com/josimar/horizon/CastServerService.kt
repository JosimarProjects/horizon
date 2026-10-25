package br.com.josimar.horizon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File

/**
 * Mantém o servidor HTTP local vivo enquanto transmite para a TV, mesmo com
 * o player fechado — assim dá pra mexer no celular com a TV tocando.
 */
class CastServerService : Service() {

    private var server: LocalMediaServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = intent?.getStringExtra(EXTRA_FILE)
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID,
            NotificationCompat.Builder(this, App.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Transmitindo para a TV")
                .setOngoing(true)
                .build(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        try { server?.stop() } catch (_: Exception) {}
        server = try {
            LocalMediaServer(file).apply { start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        } catch (_: Exception) { null }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE = "file"
        private const val NOTIF_ID = 77

        fun start(ctx: Context, path: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                ctx, Intent(ctx, CastServerService::class.java).putExtra(EXTRA_FILE, path)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CastServerService::class.java))
        }
    }
}
