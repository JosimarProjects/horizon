package br.com.josimar.horizon

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.josimar.horizon.databinding.ActivityStreamBinding
import br.com.josimar.horizon.databinding.RowSectionBinding
import coil.decode.VideoFrameDecoder
import coil.load
import com.google.android.material.chip.Chip
import java.io.File

/** Vitrine dos vídeos locais: filtro por categoria/pasta, seleção e capas. */
class StreamActivity : AppCompatActivity() {

    private lateinit var b: ActivityStreamBinding

    private var filterFolder: String? = null
    private var filterCategory: String? = null

    private var selectionMode = false
    private val selected = linkedSetOf<File>()
    private val rowAdapters = mutableListOf<RecyclerView.Adapter<*>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnManage.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        b.btnCatSel.setOnClickListener { categoryPicker(selected.toList()) }
        b.btnMoveSel.setOnClickListener { folderPicker(selected.toList()) }
        b.btnShareSel.setOnClickListener { shareMany(selected.toList()) }
        b.btnDeleteSel.setOnClickListener { confirmDelete(selected.toList()) }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    // ---------- construção ----------

    private fun build() {
        buildChips()
        b.rows.removeAllViews()
        rowAdapters.clear()

        val set: List<File>
        val heroFile: File?
        when {
            filterCategory != null -> {
                set = Catalog.inCategory(this, filterCategory!!)
                heroFile = Catalog.cover(this, "cat:$filterCategory") ?: set.firstOrNull()
            }
            filterFolder != null -> {
                set = Downloads.listVideos(this, filterFolder, Downloads.Sort.RECENT)
                heroFile = Catalog.cover(this, "folder:$filterFolder") ?: set.firstOrNull()
            }
            else -> {
                set = Downloads.listVideos(this, null, Downloads.Sort.RECENT)
                heroFile = set.firstOrNull()
            }
        }

        if (set.isEmpty() || heroFile == null) {
            b.empty.visibility = View.VISIBLE
            b.heroBox.visibility = View.GONE
            return
        }
        b.empty.visibility = View.GONE
        b.heroBox.visibility = View.VISIBLE

        b.heroTitle.text = when {
            filterCategory != null -> filterCategory
            filterFolder != null -> filterFolder
            else -> heroFile.nameWithoutExtension
        }
        b.heroImage.load(heroFile) {
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            crossfade(true)
        }
        b.btnHeroPlay.setOnClickListener { play(heroFile) }

        if (filterCategory != null) {
            addRow(filterCategory!!, Palette.colorFor(filterCategory), set)
        } else if (filterFolder != null) {
            addRow(filterFolder!!, Palette.colorFor(filterFolder), set)
        } else {
            val watched = Resume.history(this).map { File(it) }.filter { it.exists() }
            addRow(getString(R.string.row_continue), Palette.ACCENT,
                watched.filter { Resume.fraction(this, it.absolutePath) in 0.02f..0.95f })
            addRow(getString(R.string.row_history), Palette.ACCENT, watched)
            addRow(getString(R.string.row_recent), Palette.ACCENT, set.take(20))
            Catalog.categories(this).forEach { cat ->
                addRow(cat, Palette.colorFor(cat), Catalog.inCategory(this, cat))
            }
            Downloads.folders(this).forEach { folder ->
                addRow(folder, Palette.colorFor(folder),
                    Downloads.listVideos(this, folder, Downloads.Sort.RECENT))
            }
        }
        updateBottomBar()
    }

    private fun buildChips() {
        b.catBar.removeAllViews()
        val density = resources.displayMetrics.density
        data class C(val label: String, val color: Int, val onSel: () -> Unit,
                     val selected: Boolean, val onLong: (() -> Unit)?)

        val chips = buildList {
            add(C(getString(R.string.all), Palette.ACCENT,
                { filterFolder = null; filterCategory = null }, filterFolder == null && filterCategory == null, null))
            Catalog.categories(this@StreamActivity).forEach { cat ->
                add(C(cat, Palette.colorFor(cat),
                    { filterCategory = cat; filterFolder = null }, filterCategory == cat,
                    { categoryChipMenu(cat) }))
            }
            Downloads.folders(this@StreamActivity).forEach { f ->
                add(C(f, Palette.colorFor(f),
                    { filterFolder = f; filterCategory = null }, filterFolder == f,
                    { folderChipMenu(f) }))
            }
        }
        chips.forEach { c ->
            val chip = Chip(this).apply {
                text = c.label
                isCheckable = true
                isChecked = c.selected
                chipStrokeWidth = 2f * density
                chipStrokeColor = android.content.res.ColorStateList.valueOf(c.color)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (c.selected) c.color else android.graphics.Color.TRANSPARENT
                )
                setTextColor(if (c.selected) android.graphics.Color.BLACK else c.color)
                setOnClickListener { c.onSel(); build() }
                c.onLong?.let { lm -> setOnLongClickListener { lm(); true } }
            }
            b.catBar.addView(chip)
        }
    }

    private fun addRow(label: String, color: Int, files: List<File>) {
        if (files.isEmpty()) return
        val rb = RowSectionBinding.inflate(layoutInflater, b.rows, false)
        rb.rowLabel.text = label
        rb.rowLabel.setTextColor(color)
        rb.rowList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = StreamRowAdapter(
            files,
            onClick = { f -> if (selectionMode) toggle(f) else play(f) },
            onLongClick = { f -> enterSelection(f) },
            selecting = { selectionMode },
            isSelected = { it in selected }
        )
        rb.rowList.adapter = adapter
        rowAdapters.add(adapter)
        b.rows.addView(rb.root)
    }

    // ---------- seleção ----------

    private fun enterSelection(file: File) {
        selectionMode = true
        selected.add(file)
        b.bottomBar.visibility = View.VISIBLE
        notifyRows()
        updateBottomBar()
    }

    private fun toggle(file: File) {
        if (!selected.add(file)) selected.remove(file)
        if (selected.isEmpty()) exitSelection() else { notifyRows(); updateBottomBar() }
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        b.bottomBar.visibility = View.GONE
        notifyRows()
    }

    private fun notifyRows() = rowAdapters.forEach { it.notifyDataSetChanged() }

    private fun updateBottomBar() {
        b.selCount.text = getString(R.string.n_selected, selected.size)
    }

    // ---------- ações ----------

    private fun play(file: File) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, file.absolutePath)
        )
    }

    private fun confirmDelete(files: List<File>) {
        if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(if (files.size == 1) "Excluir \"${files[0].name}\"?"
            else getString(R.string.delete_n, files.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                files.forEach { it.delete() }
                exitSelection(); build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareMany(files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
        })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share)))
    }

    private fun folderPicker(files: List<File>) {
        if (files.isEmpty()) return
        val folders = Downloads.folders(this)
        val options = buildList {
            add(Downloads.ROOT_LABEL); addAll(folders); add("➕ " + getString(R.string.new_folder))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.move_to)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> moveAll(files, "")
                    which == options.lastIndex -> promptNewFolder { moveAll(files, it) }
                    else -> moveAll(files, options[which])
                }
            }
            .show()
    }

    private fun moveAll(files: List<File>, folder: String) {
        files.forEach { Downloads.moveToFolder(this, it, folder) }
        toast(getString(R.string.moved_n, files.size))
        exitSelection(); build()
    }

    private fun categoryPicker(files: List<File>) {
        if (files.isEmpty()) return
        val cats = Catalog.categories(this)
        val options = buildList {
            add(getString(R.string.no_category)); addAll(cats); add("➕ " + getString(R.string.new_category))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.category)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> setCategoryAll(files, null)
                    which == options.lastIndex -> promptNewCategory { setCategoryAll(files, it) }
                    else -> setCategoryAll(files, options[which])
                }
            }
            .show()
    }

    private fun setCategoryAll(files: List<File>, cat: String?) {
        files.forEach { Catalog.setCategory(this, it.absolutePath, cat) }
        toast(getString(R.string.category_set))
        exitSelection(); build()
    }

    // ---------- menus dos chips ----------

    private fun categoryChipMenu(cat: String) {
        AlertDialog.Builder(this)
            .setTitle(cat)
            .setItems(arrayOf(getString(R.string.set_cover), getString(R.string.remove_category))) { _, which ->
                when (which) {
                    0 -> coverPicker("cat:$cat", Catalog.inCategory(this, cat))
                    1 -> {
                        Catalog.inCategory(this, cat).forEach { Catalog.setCategory(this, it.absolutePath, null) }
                        if (filterCategory == cat) filterCategory = null
                        build()
                    }
                }
            }
            .show()
    }

    private fun folderChipMenu(folder: String) {
        AlertDialog.Builder(this)
            .setTitle(folder)
            .setItems(arrayOf(getString(R.string.set_cover), getString(R.string.delete_folder))) { _, which ->
                when (which) {
                    0 -> coverPicker("folder:$folder", Downloads.listVideos(this, folder, Downloads.Sort.RECENT))
                    1 -> {
                        Downloads.deleteFolder(this, folder, true)
                        if (filterFolder == folder) filterFolder = null
                        toast(getString(R.string.folder_deleted)); build()
                    }
                }
            }
            .show()
    }

    private fun coverPicker(key: String, files: List<File>) {
        if (files.isEmpty()) { toast(getString(R.string.empty_library)); return }
        val names = files.map { it.nameWithoutExtension }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.set_cover)
            .setItems(names) { _, which ->
                Catalog.setCover(this, key, files[which].absolutePath)
                toast(getString(R.string.cover_set)); build()
            }
            .show()
    }

    // ---------- diálogos de nome ----------

    private fun promptNewFolder(onCreated: (String) -> Unit) {
        val input = EditText(this).apply { hint = getString(R.string.folder_name) }
        AlertDialog.Builder(this).setTitle(R.string.new_folder).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotBlank()) { Downloads.createFolder(this, n); onCreated(n) }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun promptNewCategory(onCreated: (String) -> Unit) {
        val input = EditText(this).apply { hint = getString(R.string.category_name) }
        AlertDialog.Builder(this).setTitle(R.string.new_category).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotBlank()) onCreated(n)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onBackPressed() {
        if (selectionMode) exitSelection() else super.onBackPressed()
    }
}
