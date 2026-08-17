package com.nipi.leitordinamico

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
// A versão do AppCompat respeita o tema escuro; a do android.app não.
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.concurrent.Executors

/** Lista dos livros importados. Ponto de entrada do app. */
class LibraryActivity : AppCompatActivity() {

    private lateinit var store: BookStore
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView

    private val adapter = BookAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val numbers: NumberFormat = NumberFormat.getIntegerInstance()

    /** O seletor devolve uma URI de conteúdo; o texto é extraído na hora. */
    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importBook(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        store = BookStore(this)
        listView = findViewById(R.id.books)
        emptyView = findViewById(R.id.empty)

        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        findViewById<MaterialButton>(R.id.button_add).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.button_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar da leitura, o progresso de cada livro mudou.
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
    }

    private fun refresh() {
        val books = store.all()
        adapter.submit(books)
        emptyView.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (books.isEmpty()) View.GONE else View.VISIBLE
    }

    /* ------------------------------------------------------------------ */
    /* Importação                                                          */
    /* ------------------------------------------------------------------ */

    private fun importBook(uri: Uri) {
        val fileName = displayNameOf(uri) ?: "Livro"
        val title = fileName.substringBeforeLast('.', fileName)

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val message = view.findViewById<TextView>(R.id.progress_message)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()

        worker.execute {
            try {
                val text = TextExtractor.extract(this, uri, fileName) { progress ->
                    handler.post { message.text = progress }
                }

                if (text.isBlank()) {
                    handler.post {
                        dialog.dismiss()
                        Toast.makeText(this, R.string.import_empty, Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }

                val book = store.create(title, text)
                handler.post {
                    dialog.dismiss()
                    refresh()
                    openBook(book)
                }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                handler.post {
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        getString(R.string.import_failed, reason),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? {
        contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun openBook(book: Book) {
        startActivity(
            Intent(this, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
        )
    }

    private fun confirmDelete(book: Book) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_book_title)
            .setMessage(getString(R.string.delete_book_message, book.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(book.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /* ------------------------------------------------------------------ */
    /* Lista                                                               */
    /* ------------------------------------------------------------------ */

    private inner class BookAdapter : RecyclerView.Adapter<BookHolder>() {

        private var items: List<Book> = emptyList()

        fun submit(books: List<Book>) {
            items = books
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_book, parent, false)
            return BookHolder(view)
        }

        override fun onBindViewHolder(holder: BookHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class BookHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val title: TextView = view.findViewById(R.id.book_title)
        private val subtitle: TextView = view.findViewById(R.id.book_subtitle)

        fun bind(book: Book) {
            title.text = book.title
            subtitle.text = getString(
                R.string.book_progress,
                book.percent,
                numbers.format(book.wordCount)
            )
            itemView.setOnClickListener { openBook(book) }
            itemView.setOnLongClickListener {
                confirmDelete(book)
                true
            }
        }
    }
}
