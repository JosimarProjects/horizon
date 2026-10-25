package br.com.josimar.horizon

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.josimar.horizon.databinding.ActivityStreamBinding
import br.com.josimar.horizon.databinding.RowSectionBinding
import coil.decode.VideoFrameDecoder
import coil.load
import java.io.File

/** Tela de streaming (estilo vitrine) dos vídeos baixados localmente. */
class StreamActivity : AppCompatActivity() {

    private lateinit var b: ActivityStreamBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnManage.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        b.rows.removeAllViews()
        val all = Downloads.listVideos(this, null, Downloads.Sort.RECENT)

        if (all.isEmpty()) {
            b.empty.visibility = View.VISIBLE
            b.heroTitle.text = ""
            b.heroImage.setImageDrawable(null)
            b.btnHeroPlay.visibility = View.GONE
            return
        }
        b.empty.visibility = View.GONE
        b.btnHeroPlay.visibility = View.VISIBLE

        // HERO = mais recente
        val hero = all.first()
        b.heroTitle.text = hero.nameWithoutExtension
        b.heroImage.load(hero) {
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            crossfade(true)
        }
        b.btnHeroPlay.setOnClickListener { play(hero) }

        // Continuar assistindo / Histórico (em ordem de reprodução)
        val watched = Resume.history(this).map { File(it) }.filter { it.exists() }
        val continuar = watched.filter { Resume.fraction(this, it.absolutePath) in 0.02f..0.95f }
        addRow(getString(R.string.row_continue), Palette.ACCENT, continuar)
        addRow(getString(R.string.row_history), Palette.ACCENT, watched)

        // Recentes
        addRow(getString(R.string.row_recent), Palette.ACCENT, all.take(20))

        // Uma fileira por pasta (categoria), com a cor da pasta
        Downloads.folders(this).forEach { folder ->
            addRow(folder, Palette.colorFor(folder), Downloads.listVideos(this, folder, Downloads.Sort.RECENT))
        }
    }

    private fun addRow(label: String, color: Int, files: List<File>) {
        if (files.isEmpty()) return
        val rb = RowSectionBinding.inflate(layoutInflater, b.rows, false)
        rb.rowLabel.text = label
        rb.rowLabel.setTextColor(color)
        rb.rowList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rb.rowList.adapter = StreamRowAdapter(files) { play(it) }
        b.rows.addView(rb.root)
    }

    private fun play(file: File) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, file.absolutePath)
        )
    }
}
