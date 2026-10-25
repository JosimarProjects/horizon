package br.com.josimar.horizon

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        setupCastEndListener()
    }

    /** Desliga o servidor de Cast quando a transmissão termina (mesmo fora do player). */
    private fun setupCastEndListener() {
        try {
            val ctx = this
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
                .sessionManager.addSessionManagerListener(
                    object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
                        override fun onSessionEnded(s: com.google.android.gms.cast.framework.CastSession, e: Int) = CastServerService.stop(ctx)
                        override fun onSessionStarting(s: com.google.android.gms.cast.framework.CastSession) {}
                        override fun onSessionStarted(s: com.google.android.gms.cast.framework.CastSession, id: String) {}
                        override fun onSessionStartFailed(s: com.google.android.gms.cast.framework.CastSession, e: Int) {}
                        override fun onSessionEnding(s: com.google.android.gms.cast.framework.CastSession) {}
                        override fun onSessionResuming(s: com.google.android.gms.cast.framework.CastSession, id: String) {}
                        override fun onSessionResumed(s: com.google.android.gms.cast.framework.CastSession, w: Boolean) {}
                        override fun onSessionResumeFailed(s: com.google.android.gms.cast.framework.CastSession, e: Int) {}
                        override fun onSessionSuspended(s: com.google.android.gms.cast.framework.CastSession, r: Int) {}
                    },
                    com.google.android.gms.cast.framework.CastSession::class.java
                )
        } catch (_: Exception) { /* Cast indisponível */ }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private val initialized = AtomicBoolean(false)

        /** Inicializa o yt-dlp/ffmpeg sob demanda, fora da main thread. */
        suspend fun ensureEngine(app: Application) = withContext(Dispatchers.IO) {
            if (!initialized.get()) {
                synchronized(initialized) {
                    if (!initialized.get()) {
                        YoutubeDL.getInstance().init(app)
                        FFmpeg.getInstance().init(app)
                        initialized.set(true)
                    }
                }
            }
            // Mantém o yt-dlp atualizado (YouTube & cia quebram extração com frequência).
            // Roda no máximo 1x por dia.
            maybeUpdateYtdlp(app)
        }

        private fun maybeUpdateYtdlp(app: Application) {
            val prefs = app.getSharedPreferences("engine", Context.MODE_PRIVATE)
            val last = prefs.getLong("ytdlp_update", 0L)
            val oneDay = 24 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - last < oneDay) return
            try {
                YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
                prefs.edit().putLong("ytdlp_update", System.currentTimeMillis()).apply()
            } catch (_: Exception) {
                // sem rede ou falha de update: segue com a versão embarcada
            }
        }
    }
}
