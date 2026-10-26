package br.com.josimar.horizon

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.josimar.horizon.databinding.ActivityHomeBinding
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private var pendingUrls: List<String> = emptyList()
    private var justHandledIntent = false
    private val enqueued = HashSet<String>()   // links já colocados na fila nesta sessão

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnHowTo.setOnClickListener { startActivity(Intent(this, HowToActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnStream.setOnClickListener { startActivity(Intent(this, StreamActivity::class.java)) }
        b.btnVideos.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        b.btnDownload.setOnClickListener { startDownload() }
        b.btnFolder.setOnClickListener { pickFolder() }
        b.btnFolder.setOnLongClickListener { renameCurrentFolder(); true }
        updateFolderButton()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)
        observeProgress()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A área de transferência só pode ser lida quando a janela TEM foco
        // (Android 10+). onResume é cedo demais e o acesso é negado.
        if (!hasFocus) return
        // Acabou de chegar por "Compartilhar" (SEND): esse link vence desta vez.
        if (justHandledIntent) {
            justHandledIntent = false
            return
        }
        // Caso contrário, sempre relê a área de transferência — assim um link
        // recém-copiado é reconhecido mesmo com o app já aberto.
        val urls = readClipboard()
        if (urls.isNotEmpty()) setPending(urls)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val urls = extractUrls(intent.getStringExtra(Intent.EXTRA_TEXT))
            if (urls.isNotEmpty()) {
                setPending(urls)
                justHandledIntent = true
            } else {
                toast(getString(R.string.url_invalid))
            }
        }
    }

    private fun readClipboard(): List<String> {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        return extractUrls(text)
    }

    private fun setPending(urls: List<String>) {
        pendingUrls = urls
        b.status.text = when {
            urls.size > 1 -> getString(R.string.n_links, urls.size)
            else -> urls.first()
        }
    }

    private fun startDownload() {
        val urls = pendingUrls.ifEmpty { readClipboard() }
        if (urls.isEmpty()) {
            toast(getString(R.string.url_missing))
            return
        }
        // não re-enfileira o mesmo link (evita duplicar ao tocar de novo)
        val novos = urls.filter { it !in enqueued }
        if (novos.isEmpty()) {
            toast(getString(R.string.already_queued))
            return
        }
        // analisa o tamanho e deixa escolher a compressão
        b.status.text = getString(R.string.analyzing)
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                try {
                    App.ensureEngine(application)
                    YoutubeDL.getInstance().getInfo(novos.first())
                } catch (_: Exception) { null }
            }
            downloadDialog(novos, info)
        }
    }

    /** Diálogo de download: renomear, escolher pasta e qualidade/compressão. */
    private fun downloadDialog(urls: List<String>, info: VideoInfo?) {
        val d = info?.duration ?: 0
        fun est(bps: Long): String = if (d > 0) "~" + human(bps * d / 8) else "?"
        val orig = when {
            (info?.fileSizeApproximate ?: 0) > 0 -> human(info!!.fileSizeApproximate)
            (info?.fileSize ?: 0) > 0 -> human(info!!.fileSize)
            d > 0 -> est(2_800_000)
            else -> "?"
        }

        val v = layoutInflater.inflate(R.layout.dialog_download, null)
        val nameEt = v.findViewById<android.widget.EditText>(R.id.name)
        val folderBtn = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.folderBtn)
        val qg = v.findViewById<android.widget.RadioGroup>(R.id.quality)

        // nome: só renomeia quando é 1 link
        val single = urls.size == 1
        if (single) nameEt.setText(info?.title ?: "")
        else { nameEt.setText(""); nameEt.hint = "(${urls.size} vídeos)"; nameEt.isEnabled = false }

        var chosenFolder = currentFolder()
        fun lbl() { folderBtn.text = getString(R.string.folder_label, chosenFolder.ifBlank { getString(R.string.folder_main) }) }
        lbl()
        folderBtn.setOnClickListener { chooseFolder(chosenFolder) { chosenFolder = it; lbl() } }

        v.findViewById<android.widget.RadioButton>(R.id.qOriginal).text = "Original ($orig)"
        v.findViewById<android.widget.RadioButton>(R.id.q720).text = "720p (${est(1_600_000)})"
        v.findViewById<android.widget.RadioButton>(R.id.q480).text = "480p (${est(900_000)})"
        v.findViewById<android.widget.RadioButton>(R.id.qCompress).text = "Comprimir forte — 480p (${est(550_000)})"
        v.findViewById<android.widget.RadioButton>(R.id.qAudio).text = "Só áudio MP3 (${est(128_000)})"
        qg.check(R.id.qOriginal)

        AlertDialog.Builder(this)
            .setTitle(R.string.download)
            .setView(v)
            .setPositiveButton(R.string.download_action) { _, _ ->
                val mode = when (qg.checkedRadioButtonId) {
                    R.id.q720 -> "720"; R.id.q480 -> "480"
                    R.id.qCompress -> "compress"; R.id.qAudio -> "audio"
                    else -> "best"
                }
                val name = if (single) nameEt.text.toString().trim() else ""
                enqueue(urls, mode, chosenFolder, name)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> b.status.text = getString(R.string.idle) }
            .setOnCancelListener { b.status.text = getString(R.string.idle) }
            .show()
    }

    private fun enqueue(urls: List<String>, mode: String, folder: String, name: String) {
        DownloadService.start(this, ArrayList(urls), mode, folder, name)
        enqueued.addAll(urls)
        pendingUrls = emptyList()
        b.progress.visibility = View.VISIBLE
        toast(resources.getQuantityString(R.plurals.added_to_queue, urls.size, urls.size))
    }

    private fun human(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    private fun currentFolder() =
        getSharedPreferences("settings", MODE_PRIVATE).getString("folder", "") ?: ""

    private fun updateFolderButton() {
        val f = currentFolder()
        b.btnFolder.text = if (f.isBlank()) getString(R.string.folder_main) else f
    }

    private fun setFolder(name: String) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("folder", name).apply()
        updateFolderButton()
    }

    private fun renameCurrentFolder() {
        val cur = currentFolder()
        if (cur.isBlank()) {
            toast(getString(R.string.rename_root_blocked))
            return
        }
        val input = EditText(this).apply { setText(cur) }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val novo = input.text.toString().trim()
                when {
                    novo.isBlank() -> toast(getString(R.string.rename_fail))
                    // pasta ainda não existe no disco (destino virtual): só troca o nome
                    !Downloads.folders(this).contains(cur) -> setFolder(novo)
                    Downloads.renameFolder(this, cur, novo) -> setFolder(novo)
                    else -> toast(getString(R.string.rename_fail))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Escolher a pasta de destino padrão (botão da Home). */
    private fun pickFolder() = chooseFolder(currentFolder()) { setFolder(it) }

    /** Seletor de pasta reutilizável: Principal + pastas + criar nova. */
    private fun chooseFolder(current: String, onChosen: (String) -> Unit) {
        val folders = Downloads.folders(this)
        val options = buildList {
            add(Downloads.ROOT_LABEL)   // "" (raiz)
            addAll(folders)
            add("➕ " + getString(R.string.new_folder))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_to)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> onChosen("")
                    which == options.lastIndex -> {
                        val input = EditText(this).apply { hint = getString(R.string.folder_name) }
                        AlertDialog.Builder(this).setTitle(R.string.new_folder).setView(input)
                            .setPositiveButton(R.string.create) { _, _ ->
                                val name = input.text.toString().trim()
                                if (name.isNotBlank()) { Downloads.createFolder(this, name); onChosen(name) }
                            }
                            .setNegativeButton(android.R.string.cancel, null).show()
                    }
                    else -> onChosen(options[which])
                }
            }
            .show()
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloads.progress.collect { p ->
                    p ?: return@collect
                    val prefix = if (p.total > 1) "(${p.index}/${p.total}) " else ""
                    when {
                        p.error != null -> {
                            b.status.text = "Falha: ${p.error}"
                            b.progress.visibility = View.INVISIBLE
                        }
                        p.done -> {
                            b.progress.setProgressCompat(100, true)
                            if (p.total > 1 && p.index < p.total) {
                                b.status.text = getString(R.string.batch_item_done, p.index, p.total)
                            } else {
                                b.status.text = if (p.total > 1)
                                    getString(R.string.batch_all_done, p.total)
                                else getString(R.string.complete)
                                b.progress.visibility = View.INVISIBLE
                            }
                        }
                        else -> {
                            b.progress.visibility = View.VISIBLE
                            b.progress.setProgressCompat(p.percent.toInt(), true)
                            b.status.text = if (p.percent > 0f)
                                "$prefix${getString(R.string.preparing)} ${p.percent.toInt()}%"
                            else "$prefix${getString(R.string.preparing)}"
                        }
                    }
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private val URL_REGEX =
            Regex("(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")

        /** Primeiro link do texto (compatibilidade). */
        fun extractUrl(text: String?): String? = extractUrls(text).firstOrNull()

        /** Todos os links do texto, sem repetir. */
        fun extractUrls(text: String?): List<String> {
            if (text.isNullOrBlank()) return emptyList()
            return URL_REGEX.findAll(text).map { it.value }.distinct().toList()
        }
    }
}
