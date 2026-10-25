package br.com.josimar.horizon

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import br.com.josimar.horizon.databinding.ActivityPlayerBinding
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var path: String? = null
    private var playlist: ArrayList<String>? = null
    private var startIndex = 0
    private var speed = 1f

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var casting = false
    private val castListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) { castSession = s; casting = true; castLoad() }
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { castSession = s; casting = true; castLoad() }
        override fun onSessionEnded(s: CastSession, error: Int) { castSession = null; casting = false; CastServerService.stop(this@PlayerActivity) }
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        path = intent.getStringExtra(EXTRA_PATH)
        playlist = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
        startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        b.btnBack.setOnClickListener { finish() }
        b.btnShare.setOnClickListener { share() }
        b.btnSpeed.setOnClickListener { speedMenu() }
        // Enviar para TV (Cast) — só aparece se o Cast estiver disponível
        try {
            castContext = CastContext.getSharedInstance(this)
            CastButtonFactory.setUpMediaRouteButton(applicationContext, b.btnCast)
            b.btnCast.visibility = View.VISIBLE
        } catch (_: Exception) {
            b.btnCast.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.let { sm ->
            sm.addSessionManagerListener(castListener, CastSession::class.java)
            sm.currentCastSession?.let {
                if (it.isConnected) { castSession = it; casting = true; castLoad() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        castContext?.sessionManager?.removeSessionManagerListener(castListener, CastSession::class.java)
    }

    /** Carrega o arquivo atual na TV; o servidor roda num serviço (sobrevive ao fechar). */
    private fun castLoad() {
        val p = currentPath() ?: return
        val ip = lanIp() ?: return
        val f = File(p)
        CastServerService.start(this, p)
        val mime = when (f.extension.lowercase()) {
            "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; else -> "video/mp4"
        }
        val url = "http://$ip:8099/" + Uri.encode(f.name)
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, f.nameWithoutExtension)
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(meta)
            .build()
        val pos = controller?.currentPosition ?: 0L
        val req = MediaLoadRequestData.Builder()
            .setMediaInfo(info).setAutoplay(true).setCurrentTime(pos).build()
        castSession?.remoteMediaClient?.load(req)
        // vídeo e áudio só na TV: para o player local totalmente
        controller?.stop()
    }

    private fun lanIp(): String? {
        try {
            for (intf in java.net.NetworkInterface.getNetworkInterfaces()) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /** Caminho do item em reprodução (vale para playlist). */
    private fun currentPath(): String? = controller?.currentMediaItem?.mediaId ?: path

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try { future.get() } catch (_: Exception) { return@addListener }
            controller = c
            b.player.player = c
            c.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.let { Resume.markPlayed(this@PlayerActivity, it) }
                }
            })
            try { attach(c) } catch (_: Exception) {}
            // se já está transmitindo, o local não toca (só a TV)
            if (casting) { c.stop(); castLoad() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attach(c: MediaController) {
        c.setPlaybackSpeed(speed)
        val pl = playlist
        if (pl != null && pl.isNotEmpty()) {
            // já está tocando esta lista? só reanexa
            if (c.mediaItemCount == pl.size && c.currentMediaItem?.mediaId in pl) return
            val items = pl.map {
                MediaItem.Builder().setUri(Uri.fromFile(File(it))).setMediaId(it).build()
            }
            val idx = startIndex.coerceIn(0, pl.size - 1)
            Resume.markPlayed(this, pl[idx])
            c.setMediaItems(items, idx, Resume.position(this, pl[idx]))
            c.prepare()
            c.playWhenReady = !casting
            return
        }
        val p = path ?: return
        if (c.currentMediaItem?.mediaId == p && c.mediaItemCount > 0) return
        Resume.markPlayed(this, p)
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(p)))
            .setMediaId(p)
            .build()
        c.setMediaItem(item)
        c.prepare()
        val resumeAt = Resume.position(this, p)
        if (resumeAt > 0) c.seekTo(resumeAt)
        c.playWhenReady = !casting
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPlaying()
    }

    private fun enterPipIfPlaying() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val c = controller ?: return
        if (!c.isPlaying) return
        val w = c.videoSize.width
        val h = c.videoSize.height
        val ratio = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(ratio).build()
            )
        } catch (_: Exception) {}
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        val vis = if (isInPip) View.GONE else View.VISIBLE
        b.btnBack.visibility = vis
        b.topControls.visibility = vis
        b.player.useController = !isInPip
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        // em PiP o player segue; só solta de fato quando não está em PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        if (isFinishing) {
            // saiu com "Voltar": para de tocar (o background é só ao minimizar/bloquear)
            controller?.stop()
            stopService(Intent(this, PlaybackService::class.java))
        }
        b.player.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    private fun saveProgress() {
        val p = currentPath() ?: return
        controller?.let {
            if (it.duration > 0) Resume.save(this, p, it.currentPosition, it.duration)
        }
    }

    private fun speedMenu() {
        val labels = SPEEDS.map { if (it == 1f) "1x (normal)" else "${trim(it)}x" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setItems(labels) { _, which ->
                speed = SPEEDS[which]
                controller?.setPlaybackSpeed(speed)
                b.btnSpeed.text = "${trim(speed)}x"
            }
            .show()
    }

    private fun trim(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString()

    private fun share() {
        val p = currentPath() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(p))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share)))
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_START_INDEX = "start_index"
        private val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

        fun playlist(ctx: android.content.Context, paths: List<String>, startIndex: Int = 0) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(paths))
                    .putExtra(EXTRA_START_INDEX, startIndex)
            )
        }
    }
}
