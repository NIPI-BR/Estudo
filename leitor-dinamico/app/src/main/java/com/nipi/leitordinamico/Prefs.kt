package com.nipi.leitordinamico

import android.content.Context

/** Preferências de leitura. Valem para todos os livros. */
class Prefs(context: Context) {

    private val store = context.applicationContext
        .getSharedPreferences("configuracoes", Context.MODE_PRIVATE)

    /** Palavras por minuto enquanto o dedo está pressionado. */
    var wordsPerMinute: Int
        get() = store.getInt(KEY_WPM, 400)
        set(value) = store.edit().putInt(KEY_WPM, value.coerceIn(MIN_WPM, MAX_WPM)).apply()

    /** Quantas palavras o toque duplo à esquerda volta. */
    var rewindWords: Int
        get() = store.getInt(KEY_REWIND, 15)
        set(value) = store.edit().putInt(KEY_REWIND, value.coerceIn(5, 100)).apply()

    /** Tamanho máximo da palavra na tela, em sp. */
    var wordSizeSp: Int
        get() = store.getInt(KEY_FONT, 72)
        set(value) = store.edit().putInt(KEY_FONT, value.coerceIn(24, 140)).apply()

    /** Segurar um instante a mais na vírgula e no ponto. */
    var pauseOnPunctuation: Boolean
        get() = store.getBoolean(KEY_PUNCTUATION, true)
        set(value) = store.edit().putBoolean(KEY_PUNCTUATION, value).apply()

    /** Intervalo entre palavras, em milissegundos. */
    fun intervalMs(): Long = 60_000L / wordsPerMinute

    companion object {
        const val MIN_WPM = 60
        const val MAX_WPM = 1000

        private const val KEY_WPM = "ppm"
        private const val KEY_REWIND = "retrocesso"
        private const val KEY_FONT = "tamanho_fonte"
        private const val KEY_PUNCTUATION = "pausa_pontuacao"
    }
}
