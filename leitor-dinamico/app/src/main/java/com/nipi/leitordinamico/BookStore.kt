package com.nipi.leitordinamico

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Um livro já importado: só metadados. O texto mora em arquivo separado. */
data class Book(
    val id: String,
    val title: String,
    val wordCount: Int,
    val position: Int,
    val addedAt: Long
) {
    /** Progresso de 0 a 100. */
    val percent: Int
        get() = if (wordCount <= 0) 0 else (position * 100L / wordCount).toInt().coerceIn(0, 100)
}

/**
 * Guarda a biblioteca.
 *
 * Metadados ficam num JSON pequeno nas preferências; o texto de cada livro fica
 * num arquivo próprio. A posição de leitura é apenas o número da palavra atual —
 * é por isso que salvar onde você parou é instantâneo e nunca falha.
 */
class BookStore(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.getSharedPreferences("biblioteca", Context.MODE_PRIVATE)

    private val booksDir: File
        get() = File(appContext.filesDir, "livros").apply { mkdirs() }

    fun textFile(id: String): File = File(booksDir, "$id.txt")

    fun all(): List<Book> {
        val raw = store.getString(KEY_BOOKS, null) ?: return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }

        val books = ArrayList<Book>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            books.add(
                Book(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    wordCount = item.optInt("wordCount"),
                    position = item.optInt("position"),
                    addedAt = item.optLong("addedAt")
                )
            )
        }
        return books.sortedByDescending { it.addedAt }
    }

    fun find(id: String): Book? = all().firstOrNull { it.id == id }

    /** Insere um livro novo, ou substitui o que tiver o mesmo id. */
    fun save(book: Book) {
        val updated = all().filterNot { it.id == book.id } + book
        write(updated)
    }

    /** Chamado enquanto se lê, então precisa ser barato. */
    fun updatePosition(id: String, position: Int) {
        val books = all()
        val book = books.firstOrNull { it.id == id } ?: return
        if (book.position == position) return
        write(books.map { if (it.id == id) it.copy(position = position) else it })
    }

    fun delete(id: String) {
        textFile(id).delete()
        write(all().filterNot { it.id == id })
    }

    /** Cria o livro a partir do texto extraído. @return o livro salvo. */
    fun create(title: String, text: String): Book {
        val id = "livro_" + System.currentTimeMillis()
        textFile(id).writeText(text)
        val book = Book(
            id = id,
            title = title,
            wordCount = countWords(text),
            position = 0,
            addedAt = System.currentTimeMillis()
        )
        save(book)
        return book
    }

    /** Lê o livro e devolve as palavras na ordem. Rode fora da thread principal. */
    fun words(id: String): List<String> {
        val file = textFile(id)
        if (!file.exists()) return emptyList()
        return file.readText().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun write(books: List<Book>) {
        val array = JSONArray()
        for (book in books) {
            array.put(
                JSONObject()
                    .put("id", book.id)
                    .put("title", book.title)
                    .put("wordCount", book.wordCount)
                    .put("position", book.position)
                    .put("addedAt", book.addedAt)
            )
        }
        store.edit().putString(KEY_BOOKS, array.toString()).apply()
    }

    private companion object {
        const val KEY_BOOKS = "livros"
    }
}
