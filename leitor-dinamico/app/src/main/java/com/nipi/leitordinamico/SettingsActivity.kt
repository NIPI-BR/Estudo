package com.nipi.leitordinamico

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/** Velocidade, retrocesso e tamanho da palavra. Salva a cada mudança. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        setupSlider(
            sliderId = R.id.wpm_slider,
            labelId = R.id.wpm_value,
            stringId = R.string.wpm_value,
            current = prefs.wordsPerMinute,
            from = 60,
            step = 10
        ) { prefs.wordsPerMinute = it }

        setupSlider(
            sliderId = R.id.rewind_slider,
            labelId = R.id.rewind_value,
            stringId = R.string.rewind_value,
            current = prefs.rewindWords,
            from = 5,
            step = 5
        ) { prefs.rewindWords = it }

        setupSlider(
            sliderId = R.id.font_slider,
            labelId = R.id.font_value,
            stringId = R.string.font_value,
            current = prefs.wordSizeSp,
            from = 24,
            step = 4
        ) { prefs.wordSizeSp = it }

        findViewById<MaterialSwitch>(R.id.punctuation_switch).apply {
            isChecked = prefs.pauseOnPunctuation
            setOnCheckedChangeListener { _, checked -> prefs.pauseOnPunctuation = checked }
        }
    }

    private fun setupSlider(
        sliderId: Int,
        labelId: Int,
        stringId: Int,
        current: Int,
        from: Int,
        step: Int,
        onChange: (Int) -> Unit
    ) {
        val slider = findViewById<Slider>(sliderId)
        val label = findViewById<TextView>(labelId)

        // O Slider recusa valores fora da grade de passos, então alinhamos antes.
        val snapped = snapToStep(current, from, step, slider.valueFrom, slider.valueTo)
        slider.value = snapped.toFloat()
        label.text = getString(stringId, snapped)

        slider.addOnChangeListener { _, value, _ ->
            val chosen = value.toInt()
            label.text = getString(stringId, chosen)
            onChange(chosen)
        }
    }

    private fun snapToStep(value: Int, from: Int, step: Int, min: Float, max: Float): Int {
        val clamped = value.coerceIn(min.toInt(), max.toInt())
        return from + ((clamped - from) / step) * step
    }
}
