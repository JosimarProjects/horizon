package br.com.josimar.horizon

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import br.com.josimar.horizon.databinding.ActivityPlayerBinding
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var path: String? = null
    private var speed = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        path = intent.getStringExtra(EXTRA_PATH)
        b.btnShare.setOnClickListener { share() }
        b.btnSpeed.setOnClickListener { speedMenu() }
    }

    private fun speedMenu() {
        val labels = SPEEDS.map { if (it == 1f) "1x (normal)" else "${trim(it)}x" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setItems(labels) { _, which ->
                speed = SPEEDS[which]
                player?.setPlaybackSpeed(speed)
                b.btnSpeed.text = "${trim(speed)}x"
            }
            .show()
    }

    private fun trim(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString()

    override fun onStart() {
        super.onStart()
        val p = path ?: run { finish(); return }
        Resume.markPlayed(this, p)
        val player = ExoPlayer.Builder(this).build()
        b.player.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(p))))
        player.prepare()
        player.setPlaybackSpeed(speed)
        val resumeAt = Resume.position(this, p)
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.playWhenReady = true
        this.player = player
    }

    override fun onStop() {
        super.onStop()
        val p = path
        player?.let {
            if (p != null && it.duration > 0) {
                Resume.save(this, p, it.currentPosition, it.duration)
            }
        }
        player?.release()
        player = null
    }

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
