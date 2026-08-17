package com.nipi.leitordinamico

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.TextViewCompat
import java.text.NumberFormat
import java.util.concurrent.Executors

/**
 * A tela de leitura.
 *
 * Telefone deitado, fundo preto, uma palavra branca por vez. Segurar o lado
 * direito avança e o esquerdo volta, ambos na velocidade configurada. Dois
 * toques à esquerda voltam um punhado de palavras. A posição é salva sozinha.
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var store: BookStore

    private lateinit var root: View
    private lateinit var wordView: TextView
    private lateinit var speedView: TextView
    private lateinit var progressView: TextView
    private lateinit var hintView: TextView
    private lateinit var loadingView: ProgressBar
    private lateinit var progressBar: View

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val numbers: NumberFormat = NumberFormat.getIntegerInstance()

    private var bookId: String = ""
    private var words: List<String> = emptyList()
    private var index = 0

    /** +1 avançando, -1 voltando, 0 parado. */
    private var direction = 0

    private var lastLeftDownAt = 0L
    private var indexAtLeftDown = 0

    /** Avança (ou volta) uma palavra e agenda a próxima. */
    private val tick = object : Runnable {
        override fun run() {
            if (!stepOnce()) {
                stopHolding()
                return
            }
            handler.postDelayed(this, delayAfterCurrentWord())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        prefs = Prefs(this)
        store = BookStore(this)

        root = findViewById(R.id.reader_root)
        wordView = findViewById(R.id.word)
        speedView = findViewById(R.id.hud_speed)
        progressView = findViewById(R.id.hud_progress)
        hintView = findViewById(R.id.hint)
        loadingView = findViewById(R.id.loading)
        progressBar = findViewById(R.id.progress_bar)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goFullScreen()

        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        if (bookId.isEmpty()) {
            finish()
            return
        }

        // Palavras longas encolhem para caber; o tamanho escolhido é o teto.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            wordView, 20, prefs.wordSizeSp, 2, TypedValue.COMPLEX_UNIT_SP
        )

        loadBook()
    }

    private fun loadBook() {
        loadingView.visibility = View.VISIBLE
        worker.execute {
            val book = store.find(bookId)
            val loaded = store.words(bookId)
            handler.post {
                if (isFinishing || isDestroyed) return@post
                loadingView.visibility = View.GONE

                if (book == null || loaded.isEmpty()) {
                    Toast.makeText(this, R.string.import_empty, Toast.LENGTH_LONG).show()
                    finish()
                    return@post
                }

                words = loaded
                index = book.position.coerceIn(0, words.size - 1)
                attachTouchHandler()
                showHint()
                render()
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Toque                                                               */
    /* ------------------------------------------------------------------ */

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchHandler() {
        root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val onLeft = event.x < view.width / 2f
                    if (onLeft) handleLeftPress() else handleRightPress()
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    stopHolding()
                    savePosition()
                    true
                }

                else -> false
            }
        }
    }

    private fun handleLeftPress() {
        val now = SystemClock.elapsedRealtime()
        val isDoubleTap = now - lastLeftDownAt <= DOUBLE_TAP_WINDOW_MS

        if (isDoubleTap) {
            stopHolding()
            // Volta a partir de onde estávamos no primeiro toque, e não de onde
            // o primeiro toque já tinha andado — senão o salto perde palavras.
            index = (indexAtLeftDown - prefs.rewindWords).coerceAtLeast(0)
            lastLeftDownAt = 0L
            render()
            savePosition()
            return
        }

        lastLeftDownAt = now
        indexAtLeftDown = index
        startHolding(-1)
    }

    private fun handleRightPress() {
        lastLeftDownAt = 0L
        startHolding(1)
    }

    private fun startHolding(newDirection: Int) {
        handler.removeCallbacks(tick)
        direction = newDirection
        speedView.setTextColor(getColor(R.color.reader_word))
        hideHint()

        // A primeira palavra sai no toque, sem esperar o intervalo: sem isso a
        // leitura parece travada no começo de cada pressionada.
        if (stepOnce()) {
            handler.postDelayed(tick, delayAfterCurrentWord())
        }
    }

    private fun stopHolding() {
        handler.removeCallbacks(tick)
        direction = 0
        speedView.setTextColor(getColor(R.color.reader_hud))
    }

    /** @return false se chegou ao começo ou ao fim do livro. */
    private fun stepOnce(): Boolean {
        val next = index + direction
        if (next < 0 || next >= words.size) return false
        index = next
        render()
        return true
    }

    /**
     * Uma pausa extra na pontuação dá tempo de fechar a frase na cabeça. É o
     * ajuste que mais melhora a compreensão em velocidade alta.
     */
    private fun delayAfterCurrentWord(): Long {
        val base = prefs.intervalMs()
        if (!prefs.pauseOnPunctuation) return base

        val word = words.getOrNull(index) ?: return base
        return when (word.lastOrNull()) {
            '.', '!', '?', '…', ':', ';' -> (base * 2.0).toLong()
            ',', '—', '–' -> (base * 1.5).toLong()
            else -> base
        }
    }

    /* ------------------------------------------------------------------ */
    /* Tela                                                                */
    /* ------------------------------------------------------------------ */

    private fun render() {
        wordView.text = words.getOrNull(index).orEmpty()
        speedView.text = getString(R.string.wpm_short, prefs.wordsPerMinute)
        progressView.text = getString(
            R.string.words_progress,
            numbers.format(index + 1),
            numbers.format(words.size),
            percent()
        )

        val width = root.width
        if (width > 0) {
            val params = progressBar.layoutParams
            params.width = (width.toLong() * (index + 1) / words.size).toInt()
            progressBar.layoutParams = params
        }
    }

    private fun percent(): Int =
        if (words.isEmpty()) 0 else ((index + 1) * 100L / words.size).toInt()

    private fun showHint() {
        hintView.text = getString(R.string.reader_hint, prefs.rewindWords)
        hintView.alpha = 1f
        hintView.visibility = View.VISIBLE
        handler.postDelayed({ hideHint() }, HINT_DURATION_MS)
    }

    private fun hideHint() {
        if (hintView.visibility != View.VISIBLE) return
        hintView.animate().alpha(0f).setDuration(400).withEndAction {
            hintView.visibility = View.GONE
        }.start()
    }

    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /* ------------------------------------------------------------------ */
    /* Posição                                                             */
    /* ------------------------------------------------------------------ */

    private fun savePosition() {
        if (words.isEmpty()) return
        val current = index
        val id = bookId
        worker.execute { store.updatePosition(id, current) }
    }

    override fun onPause() {
        super.onPause()
        stopHolding()
        savePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"

        private const val DOUBLE_TAP_WINDOW_MS = 320L
        private const val HINT_DURATION_MS = 5_000L
    }
}
