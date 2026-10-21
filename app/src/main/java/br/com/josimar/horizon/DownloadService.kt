package br.com.josimar.horizon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Baixa um vídeo com yt-dlp (mesmo motor do original), em foreground. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processId = "horizon"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val quality = intent?.getStringExtra(EXTRA_QUALITY) ?: "best"
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification("Preparando seu vídeo…", 0, true),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        Downloads.progress.value = DownloadProgress(url = url, line = "Preparando…")

        scope.launch {
            try {
                App.ensureEngine(application)
                val dir = Downloads.outputDir(this@DownloadService)
                val before = dir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", "${dir.absolutePath}/%(title).70s.%(ext)s")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--no-warnings")
                    addOption("--restrict-filenames")
                    when (quality) {
                        "audio" -> {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                        }
                        "720" -> {
                            addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/b")
                            addOption("--merge-output-format", "mp4")
                        }
                        "1080" -> {
                            addOption("-f", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/b")
                            addOption("--merge-output-format", "mp4")
                        }
                        else -> {
                            addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b")
                            addOption("--merge-output-format", "mp4")
                        }
                    }
                }

                YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    val pct = if (progress < 0f) 0f else progress
                    Downloads.progress.value = DownloadProgress(
                        url = url, percent = pct, line = line
                    )
                    updateNotification("Baixando… ${pct.toInt()}%", pct.toInt(), false)
                }

                val after = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                val newFile: File? = after.firstOrNull { it.name !in before } ?: after.firstOrNull()

                Downloads.progress.value = DownloadProgress(
                    url = url, percent = 100f, done = true, file = newFile?.absolutePath
                )
                updateNotification("Download concluído", 100, false)
            } catch (e: Exception) {
                Downloads.progress.value = DownloadProgress(
                    url = url, error = e.message ?: "Falha no download"
                )
                updateNotification("Falha: ${e.message}", 0, false)
            } finally {
                stopSelfSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun buildNotification(text: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(!text.startsWith("Download concluído") && !text.startsWith("Falha"))
            .setProgress(100, pct, indeterminate)
            .build()

    private fun updateNotification(text: String, pct: Int, indeterminate: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, pct, indeterminate))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_QUALITY = "quality"
        private const val NOTIF_ID = 42

        fun start(ctx: Context, url: String, quality: String) {
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_QUALITY, quality)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
