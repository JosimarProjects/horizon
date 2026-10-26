package br.com.josimar.horizon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.josimar.horizon.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }

        b.storage.text = getString(
            R.string.storage_info,
            Downloads.humanSize(Downloads.usedBytes(this)),
            Downloads.humanSize(Downloads.freeBytes(this))
        )

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        when (prefs.getString("quality", "best")) {
            "1080" -> b.q1080.isChecked = true
            "720" -> b.q720.isChecked = true
            "audio" -> b.qAudio.isChecked = true
            else -> b.qBest.isChecked = true
        }

        b.qualityGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                b.q1080.id -> "1080"
                b.q720.id -> "720"
                b.qAudio.id -> "audio"
                else -> "best"
            }
            prefs.edit().putString("quality", value).apply()
        }
    }
}
