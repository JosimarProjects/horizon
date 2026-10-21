package br.com.josimar.horizon

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.josimar.horizon.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private var pendingUrl: String? = null
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
        readClipboard()?.let { setPending(it) }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val url = extractUrl(text)
            if (url != null) {
                setPending(url)
                justHandledIntent = true
            } else {
                toast(getString(R.string.url_invalid))
            }
        }
    }

    private fun readClipboard(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        return extractUrl(text)
    }

    private fun setPending(url: String) {
        pendingUrl = url
        b.status.text = url
    }

    private fun startDownload() {
        val url = pendingUrl ?: readClipboard()
        if (url == null) {
            toast(getString(R.string.url_missing))
            return
        }
        val quality = getSharedPreferences("settings", MODE_PRIVATE)
            .getString("quality", "best") ?: "best"
        DownloadService.start(this, url, quality)
        b.status.text = getString(R.string.preparing)
        b.progress.visibility = View.VISIBLE
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloads.progress.collect { p ->
                    p ?: return@collect
                    when {
                        p.error != null -> {
                            b.status.text = "Falha: ${p.error}"
                            b.progress.visibility = View.INVISIBLE
                        }
                        p.done -> {
                            b.status.text = getString(R.string.complete)
                            b.progress.setProgressCompat(100, true)
                            b.progress.visibility = View.INVISIBLE
                        }
                        else -> {
                            b.progress.visibility = View.VISIBLE
                            b.progress.setProgressCompat(p.percent.toInt(), true)
                            b.status.text = if (p.percent > 0f)
                                "${getString(R.string.preparing)} ${p.percent.toInt()}%"
                            else getString(R.string.preparing)
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

        fun extractUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            return URL_REGEX.find(text)?.value
        }
    }
}
