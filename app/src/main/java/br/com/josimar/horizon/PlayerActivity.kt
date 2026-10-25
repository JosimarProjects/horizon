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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        path = intent.getStringExtra(EXTRA_PATH)
        b.btnShare.setOnClickListener { share() }
    }

    override fun onStart() {
        super.onStart()
        val p = path ?: run { finish(); return }
        val player = ExoPlayer.Builder(this).build()
        b.player.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(p))))
        player.prepare()
        player.playWhenReady = true
        this.player = player
    }

    override fun onStop() {
        super.onStop()
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
    }
}
