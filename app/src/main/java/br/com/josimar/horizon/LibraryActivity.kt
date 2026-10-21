package br.com.josimar.horizon

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import br.com.josimar.horizon.databinding.ActivityLibraryBinding
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var b: ActivityLibraryBinding
    private lateinit var adapter: VideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = VideoAdapter(emptyList(), onClick = ::play, onLongClick = ::itemMenu)
        b.list.layoutManager = GridLayoutManager(this, 2)
        b.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val videos = Downloads.listVideos(this)
        adapter.update(videos)
        b.empty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun play(file: File) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_PATH, file.absolutePath))
    }

    private fun itemMenu(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(arrayOf("Compartilhar", "Excluir")) { _, which ->
                when (which) {
                    0 -> share(file)
                    1 -> { file.delete(); refresh() }
                }
            }
            .show()
    }

    private fun share(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Compartilhar"))
    }
}
