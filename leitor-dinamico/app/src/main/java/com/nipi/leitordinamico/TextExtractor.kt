package com.nipi.leitordinamico

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.text.HtmlCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Transforma um arquivo de livro em texto corrido.
 *
 * Isto roda UMA VEZ, na importação — nunca durante a leitura. Ler um PDF de
 * 400 páginas leva segundos; fazer isso enquanto o dedo está pressionado
 * acabaria com a fluidez. Depois daqui, o app só lida com uma lista de palavras.
 */
object TextExtractor {

    /** Erro com mensagem já pronta para mostrar ao usuário. */
    class ExtractionException(message: String) : Exception(message)

    /**
     * @param onProgress recebe mensagens de andamento (chamado na thread de trabalho).
     * @return o texto do livro, ou lança [ExtractionException].
     */
    fun extract(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: (String) -> Unit
    ): String {
        // Copiamos para o cache primeiro: tanto o ZIP do EPUB quanto o PDF
        // precisam de acesso aleatório ao arquivo, que um InputStream não dá.
        onProgress(context.getString(R.string.reading_file))
        val local = copyToCache(context, uri)

        try {
            val text = when (fileName.substringAfterLast('.', "").lowercase()) {
                "epub" -> extractEpub(local, onProgress)
                "pdf" -> extractPdf(context, local, onProgress)
                else -> local.readText(Charsets.UTF_8)
            }
            return normalize(text)
        } finally {
            local.delete()
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val target = File.createTempFile("import", null, context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ExtractionException("não consegui abrir o arquivo")
        input.use { source ->
            target.outputStream().use { destination ->
                source.copyTo(destination)
            }
        }
        return target
    }

    /* ------------------------------------------------------------------ */
    /* EPUB                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Um EPUB é um ZIP. Dentro dele, `META-INF/container.xml` aponta para o
     * arquivo OPF, e o OPF traz o índice (quais arquivos existem) e a espinha
     * (em que ordem devem ser lidos). Seguir a espinha é o que garante que os
     * capítulos saiam na ordem certa.
     */
    private fun extractEpub(file: File, onProgress: (String) -> Unit): String {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
                ?: throw ExtractionException("não parece um EPUB válido")

            val opfEntry = zip.getEntry(opfPath)
                ?: throw ExtractionException("o índice do EPUB está faltando")

            val (manifest, spine) = zip.getInputStream(opfEntry).use { parseOpf(it) }
            val baseDir = opfPath.substringBeforeLast('/', "")

            val text = StringBuilder()
            spine.forEachIndexed { index, itemId ->
                onProgress("Capítulo ${index + 1} de ${spine.size}")
                val href = manifest[itemId] ?: return@forEachIndexed
                val entryPath = resolvePath(baseDir, href)
                val entry = zip.getEntry(entryPath) ?: return@forEachIndexed
                val html = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                text.append(htmlToText(html)).append('\n')
            }
            return text.toString()
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(container).use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.localName() == "rootfile") {
                    parser.getAttributeValue(null, "full-path")?.let { return it }
                }
                event = parser.next()
            }
        }
        return null
    }

    /** @return índice (id do item → caminho) e espinha (ids na ordem de leitura). */
    private fun parseOpf(stream: java.io.InputStream): Pair<Map<String, String>, List<String>> {
        val manifest = HashMap<String, String>()
        val spine = ArrayList<String>()

        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.localName()) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val type = parser.getAttributeValue(null, "media-type").orEmpty()
                        // Só interessa o que tem texto: capa, CSS e imagens ficam de fora.
                        if (id != null && href != null && type.contains("html")) {
                            manifest[id] = href
                        }
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
                }
            }
            event = parser.next()
        }
        return manifest to spine
    }

    /** Resolve um href relativo ao OPF, incluindo os `../` que alguns EPUBs usam. */
    private fun resolvePath(baseDir: String, href: String): String {
        val decoded = try {
            URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        } catch (e: Exception) {
            href.substringBefore('#')
        }

        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/'))
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    /**
     * XHTML → texto. O `head`, os scripts e o CSS saem antes, senão o conteúdo
     * de estilo vaza para o meio do texto do livro.
     */
    private fun htmlToText(html: String): String {
        val stripped = html
            .replace(Regex("(?is)<head\\b.*?</head>"), " ")
            .replace(Regex("(?is)<script\\b.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b.*?</style>"), " ")
        return HtmlCompat.fromHtml(stripped, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    }

    /** Com namespaces desligados, o nome da tag pode vir como `opf:item`. */
    private fun String?.localName(): String = this?.substringAfterLast(':').orEmpty()

    /* ------------------------------------------------------------------ */
    /* PDF                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Extrai página a página em vez de tudo de uma vez — assim dá para mostrar
     * o andamento, e o uso de memória fica estável em livros grandes.
     */
    private fun extractPdf(context: Context, file: File, onProgress: (String) -> Unit): String {
        PDFBoxResourceLoader.init(context.applicationContext)

        PDDocument.load(file).use { document ->
            val pages = document.numberOfPages
            val stripper = PDFTextStripper()
            val text = StringBuilder()

            for (page in 1..pages) {
                onProgress("Página $page de $pages")
                stripper.startPage = page
                stripper.endPage = page
                text.append(stripper.getText(document)).append('\n')
            }
            return text.toString()
        }
    }

    /* ------------------------------------------------------------------ */
    /* Limpeza                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Junta a hifenização de fim de linha (comum em PDF) e reduz o espaço em
     * branco. O texto final tem uma palavra por espaço — é isso que a tela de
     * leitura consome.
     */
    private fun normalize(raw: String): String {
        return raw
            // Espaço inquebrável e marca de ordem de bytes viram espaço comum.
            .replace('\u00A0', ' ')
            .replace('\uFEFF', ' ')
            // "conti-\nnuação" vira "continuação"
            .replace(Regex("(\\p{L})-\\s*\\n\\s*(\\p{L})"), "$1$2")
            .replace(Regex("[ \\t\\r]+"), " ")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }
}
