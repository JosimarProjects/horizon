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
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var path: String? = null
    private var speed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        path = intent.getStringExtra(EXTRA_PATH)
        b.btnBack.setOnClickListener { finish() }
        b.btnShare.setOnClickListener { share() }
        b.btnSpeed.setOnClickListener { speedMenu() }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try { future.get() } catch (_: Exception) { return@addListener }
            controller = c
            b.player.player = c
            try { attach(c) } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attach(c: MediaController) {
        val p = path ?: return
        c.setPlaybackSpeed(speed)
        if (c.currentMediaItem?.mediaId == p && c.mediaItemCount > 0) {
            // já é o vídeo em reprodução (voltou pra ele) — só reanexa
            return
        }
        Resume.markPlayed(this, p)
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(p)))
            .setMediaId(p)
            .build()
        c.setMediaItem(item)
        c.prepare()
        val resumeAt = Resume.position(this, p)
        if (resumeAt > 0) c.seekTo(resumeAt)
        c.playWhenReady = true
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
        b.player.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    private fun saveProgress() {
        val p = path ?: return
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
        val p = path ?: return
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
        private val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
    }
}
