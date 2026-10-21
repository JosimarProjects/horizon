package br.com.josimar.horizon

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.josimar.horizon.databinding.ActivityHowtoBinding

class HowToActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityHowtoBinding.inflate(layoutInflater)
        setContentView(b.root)
    }
}
