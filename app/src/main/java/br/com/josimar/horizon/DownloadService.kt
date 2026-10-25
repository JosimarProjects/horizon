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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Baixa vídeos com yt-dlp (mesmo motor do original), em foreground.
 * Aceita vários links de uma vez (fila) e playlists (salvas em subpasta).
 */
class DownloadService : Service() {

    private data class Item(val url: String, val quality: String, val folder: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val quality = intent?.getStringExtra(EXTRA_QUALITY) ?: "best"
        val folder = intent?.getStringExtra(EXTRA_FOLDER) ?: ""
        val urls = intent?.getStringArrayListExtra(EXTRA_URLS)
            ?: intent?.getStringExtra(EXTRA_URL)?.let { arrayListOf(it) }
            ?: arrayListOf()
        val clean = urls.filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        clean.forEach { queue.add(Item(it, quality, folder)); totalEnqueued.incrementAndGet() }

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification("Preparando…", 0, true),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (workerRunning.compareAndSet(false, true)) {
            scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        try {
            App.ensureEngine(application)
            while (true) {
                var item = queue.poll()
                while (item != null) {
                    downloadOne(item)
                    completed.incrementAndGet()
                    item = queue.poll()
                }
                // fecha a janela de corrida: só encerra se, após liberar a flag,
                // ninguém tiver enfileirado algo novo.
                workerRunning.set(false)
                if (queue.isEmpty()) break
                if (!workerRunning.compareAndSet(false, true)) break
            }
        } finally {
            completed.set(0)
            totalEnqueued.set(0)
            workerRunning.set(false)
            stopSelfSafely()
        }
    }

    private fun downloadOne(item: Item) {
        val url = item.url
        val idx = completed.get() + 1
        val total = totalEnqueued.get()
        val prefix = if (total > 1) "($idx/$total) " else ""
        Downloads.progress.value = DownloadProgress(url = url, index = idx, total = total, line = "Preparando…")
        try {
            val dir = Downloads.folderDir(this, item.folder)
            val before = dir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()
            val playlist = isPlaylist(url)

            val request = YoutubeDLRequest(url).apply {
                addOption("--no-mtime")
                addOption("--no-warnings")
                addOption("--restrict-filenames")
                if (playlist) {
                    addOption("--yes-playlist")
                    addOption("-o", "${dir.absolutePath}/%(playlist_title).60s/%(playlist_index)03d-%(title).60s.%(ext)s")
                } else {
                    addOption("--no-playlist")
                    addOption("-o", "${dir.absolutePath}/%(title).70s.%(ext)s")
                }
                when (item.quality) {
                    "audio" -> { addOption("-x"); addOption("--audio-format", "mp3") }
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

            YoutubeDL.getInstance().execute(request, "horizon-$idx") { progress, _, line ->
                val pct = if (progress < 0f) 0f else progress
                Downloads.progress.value = DownloadProgress(url = url, percent = pct, line = line, index = idx, total = total)
                updateNotification("${prefix}Baixando… ${pct.toInt()}%", pct.toInt(), false)
            }

            val after = dir.walkTopDown().filter { it.isFile }.toList()
            val newFile: File? = after.filter { it.absolutePath !in before }
                .maxByOrNull { it.lastModified() }
                ?: after.maxByOrNull { it.lastModified() }

            Downloads.progress.value = DownloadProgress(
                url = url, percent = 100f, done = true, file = newFile?.absolutePath, index = idx, total = total
            )
            val doneMsg = if (total > 1) "$idx de $total concluído" else "Download concluído"
            updateNotification(doneMsg, 100, false)
        } catch (e: Exception) {
            Downloads.progress.value = DownloadProgress(
                url = url, error = e.message ?: "Falha no download", index = idx, total = total
            )
            updateNotification("Falha: ${e.message}", 0, false)
        }
    }

    private fun isPlaylist(url: String): Boolean {
        val u = url.lowercase()
        return "list=" in u || "/playlist" in u || "/sets/" in u || "/album/" in u
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
            .setOngoing(!text.contains("concluído") && !text.startsWith("Falha"))
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
        const val EXTRA_URLS = "urls"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_FOLDER = "folder"
        private const val NOTIF_ID = 42

        private val queue = ConcurrentLinkedQueue<Item>()
        private val workerRunning = AtomicBoolean(false)
        private val totalEnqueued = AtomicInteger(0)
        private val completed = AtomicInteger(0)

        fun start(ctx: Context, url: String, quality: String, folder: String = "") =
            start(ctx, arrayListOf(url), quality, folder)

        fun start(ctx: Context, urls: ArrayList<String>, quality: String, folder: String = "") {
            val i = Intent(ctx, DownloadService::class.java)
                .putStringArrayListExtra(EXTRA_URLS, urls)
                .putExtra(EXTRA_QUALITY, quality)
                .putExtra(EXTRA_FOLDER, folder)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
