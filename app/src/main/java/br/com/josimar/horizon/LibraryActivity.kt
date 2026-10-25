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
import androidx.recyclerview.widget.GridLayoutManager
import br.com.josimar.horizon.databinding.ActivityLibraryBinding
import com.google.android.material.chip.Chip
import java.io.File

class LibraryActivity : AppCompatActivity() {

    private lateinit var b: ActivityLibraryBinding
    private lateinit var adapter: VideoAdapter

    private var filterFolder: String? = null   // null=Todos, ""=Principal, nome=pasta
    private var sort: Downloads.Sort = Downloads.Sort.RECENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)

        sort = runCatching {
            Downloads.Sort.valueOf(
                getSharedPreferences("settings", MODE_PRIVATE).getString("lib_sort", "RECENT")!!
            )
        }.getOrDefault(Downloads.Sort.RECENT)

        adapter = VideoAdapter(emptyList(), onClick = ::onItemClick, onLongClick = ::onItemLong,
            onShare = ::share, onMore = ::itemMenu)
        b.list.layoutManager = GridLayoutManager(this, 2)
        b.list.adapter = adapter

        b.btnBack.setOnClickListener { finish() }
        b.btnSort.setOnClickListener { sortMenu() }
        b.btnSelect.setOnClickListener { toggleSelectionMode() }
        b.btnDeleteSel.setOnClickListener { deleteSelected() }
        b.btnMoveSel.setOnClickListener { moveSelected() }
    }

    override fun onResume() {
        super.onResume()
        buildFolderChips()
        refresh()
    }

    // ---- lista / filtro / ordenação ----

    private fun refresh() {
        val videos = Downloads.listVideos(this, filterFolder, sort)
        adapter.update(videos)
        b.empty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        updateSelCount()
    }

    private fun buildFolderChips() {
        b.folderBar.removeAllViews()
        val entries = buildList {
            add(Triple(getString(R.string.all), null as String?, Palette.ACCENT))  // Todos
            add(Triple(Downloads.ROOT_LABEL, "" as String?, Palette.ACCENT))       // Principal
            Downloads.folders(this@LibraryActivity).forEach {
                add(Triple(it, it as String?, Palette.colorFor(it)))
            }
        }
        val density = resources.displayMetrics.density
        entries.forEach { (label, value, color) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = filterFolder == value
                chipStrokeWidth = 2f * density
                chipStrokeColor = android.content.res.ColorStateList.valueOf(color)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (isChecked) color else android.graphics.Color.TRANSPARENT
                )
                setTextColor(if (isChecked) android.graphics.Color.BLACK else color)
                setOnClickListener {
                    filterFolder = value
                    refresh()
                    buildFolderChips()
                }
                if (!value.isNullOrBlank()) {
                    setOnLongClickListener { folderMenu(value); true }
                }
            }
            b.folderBar.addView(chip)
        }
    }

    /** Segurar um chip de pasta abre as opções de excluir a pasta. */
    private fun folderMenu(folder: String) {
        val count = Downloads.listVideos(this, folder).size
        if (count == 0) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.folder_delete_q, folder))
                .setPositiveButton(R.string.delete) { _, _ -> doDeleteFolder(folder, false) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(folder)
            .setItems(
                arrayOf(
                    getString(R.string.folder_delete_move, count),
                    getString(R.string.folder_delete_all, count)
                )
            ) { _, which ->
                when (which) {
                    0 -> doDeleteFolder(folder, true)
                    1 -> AlertDialog.Builder(this)
                        .setMessage(getString(R.string.folder_delete_all, count) + "?")
                        .setPositiveButton(R.string.delete) { _, _ -> doDeleteFolder(folder, false) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun doDeleteFolder(folder: String, moveToRoot: Boolean) {
        Downloads.deleteFolder(this, folder, moveToRoot)
        if (filterFolder == folder) filterFolder = null
        toast(getString(R.string.folder_deleted))
        buildFolderChips()
        refresh()
    }

    private fun sortMenu() {
        val options = Downloads.Sort.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_by)
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                sort = options[which]
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("lib_sort", sort.name).apply()
                refresh()
            }
            .show()
    }

    // ---- clique / seleção ----

    private fun onItemClick(file: File) {
        if (adapter.selectionMode) {
            adapter.toggle(file)
            updateSelCount()
        } else {
            play(file)
        }
    }

    private fun onItemLong(file: File) {
        if (!adapter.selectionMode) {
            adapter.setSelectionMode(true)
            b.bottomBar.visibility = View.VISIBLE
        }
        adapter.toggle(file)
        updateSelCount()
    }

    private fun toggleSelectionMode() {
        val on = !adapter.selectionMode
        adapter.setSelectionMode(on)
        b.bottomBar.visibility = if (on) View.VISIBLE else View.GONE
        updateSelCount()
    }

    private fun updateSelCount() {
        b.selCount.text = getString(R.string.n_selected, adapter.selected.size)
    }

    // ---- ações individuais ----

    private fun play(file: File) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, file.absolutePath)
        )
    }

    private fun itemMenu(file: File) {
        val actions = arrayOf(
            getString(R.string.rename),
            getString(R.string.category),
            getString(R.string.move),
            getString(R.string.share),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setTitle(file.nameWithoutExtension)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> renameDialog(file)
                    1 -> categoryDialog(file)
                    2 -> moveDialog(listOf(file))
                    3 -> share(file)
                    4 -> confirmDelete(listOf(file))
                }
            }
            .show()
    }

    private fun categoryDialog(file: File) {
        val cats = Catalog.categories(this)
        val options = buildList {
            add(getString(R.string.no_category)); addAll(cats); add("➕ " + getString(R.string.new_category))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.category)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> { Catalog.setCategory(this, file.absolutePath, null); toast(getString(R.string.category_set)) }
                    which == options.lastIndex -> {
                        val input = EditText(this).apply { hint = getString(R.string.category_name) }
                        AlertDialog.Builder(this).setTitle(R.string.new_category).setView(input)
                            .setPositiveButton(R.string.create) { _, _ ->
                                val n = input.text.toString().trim()
                                if (n.isNotBlank()) { Catalog.setCategory(this, file.absolutePath, n); toast(getString(R.string.category_set)) }
                            }
                            .setNegativeButton(android.R.string.cancel, null).show()
                    }
                    else -> { Catalog.setCategory(this, file.absolutePath, options[which]); toast(getString(R.string.category_set)) }
                }
            }
            .show()
    }

    private fun renameDialog(file: File) {
        val input = EditText(this).apply { setText(file.nameWithoutExtension) }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    if (Downloads.renameFile(file, newName) == null)
                        toast(getString(R.string.rename_fail))
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun share(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, getString(R.string.share)))
    }

    // ---- ações em lote ----

    private fun deleteSelected() = confirmDelete(adapter.selectedList())

    private fun confirmDelete(files: List<File>) {
        if (files.isEmpty()) return
        val msg = if (files.size == 1) "Excluir \"${files[0].name}\"?"
        else getString(R.string.delete_n, files.size)
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                files.forEach { it.delete() }
                exitSelection()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun moveSelected() {
        val files = adapter.selectedList()
        if (files.isEmpty()) { toast(getString(R.string.nothing_selected)); return }
        moveDialog(files)
    }

    private fun moveDialog(files: List<File>) {
        val folders = Downloads.folders(this)
        val options = buildList {
            add(Downloads.ROOT_LABEL)
            addAll(folders)
            add("➕ " + getString(R.string.new_folder))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.move_to)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> doMove(files, "")
                    which == options.lastIndex -> promptNewFolderThenMove(files)
                    else -> doMove(files, options[which])
                }
            }
            .show()
    }

    private fun promptNewFolderThenMove(files: List<File>) {
        val input = EditText(this).apply { hint = getString(R.string.folder_name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    Downloads.createFolder(this, name)
                    doMove(files, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doMove(files: List<File>, folder: String) {
        var ok = 0
        files.forEach { if (Downloads.moveToFolder(this, it, folder)) ok++ }
        toast(getString(R.string.moved_n, ok))
        exitSelection()
        buildFolderChips()
        refresh()
    }

    private fun exitSelection() {
        adapter.setSelectionMode(false)
        b.bottomBar.visibility = View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
