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
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private var pendingUrls: List<String> = emptyList()
    private var justHandledIntent = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnHowTo.setOnClickListener { startActivity(Intent(this, HowToActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnVideos.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        b.btnDownload.setOnClickListener { startDownload() }
        b.btnFolder.setOnClickListener { pickFolder() }
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
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val quality = prefs.getString("quality", "best") ?: "best"
        val folder = prefs.getString("folder", "") ?: ""
        DownloadService.start(this, ArrayList(urls), quality, folder)
        b.status.text = getString(R.string.preparing)
        b.progress.visibility = View.VISIBLE
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

    /** Escolher a pasta de destino dos downloads (ou criar uma nova). */
    private fun pickFolder() {
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
                    which == 0 -> setFolder("")
                    which == options.lastIndex -> promptNewFolder()
                    else -> setFolder(options[which])
                }
            }
            .show()
    }

    private fun promptNewFolder() {
        val input = EditText(this).apply { hint = getString(R.string.folder_name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    Downloads.createFolder(this, name)
                    setFolder(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
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
