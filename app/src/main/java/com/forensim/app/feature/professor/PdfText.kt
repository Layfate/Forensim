package com.forensim.app.feature.professor

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Text pulled out of a PDF on the phone, ready to send to the AI quiz generator. */
data class PdfText(val text: String, val pages: Int, val pagesRead: Int, val truncated: Boolean)

class PdfTextException(message: String) : Exception(message)

/**
 * Extracts selectable text from [uri] on a background thread. Only the first [maxPages] pages and
 * [maxChars] characters are kept, which is plenty for a quiz and keeps the AI request small. The
 * file is copied to the cache and parsed with temp-file buffering so large PDFs don't exhaust memory.
 */
suspend fun extractPdfText(context: Context, uri: Uri, maxPages: Int = 60, maxChars: Int = 60_000): PdfText =
    withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val temp = File.createTempFile("quiz-source", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: throw PdfTextException("Couldn't open that file. Choose it again.")
            val document = try {
                PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw PdfTextException("That PDF is password-protected. Remove the password and try again.")
            } catch (e: java.io.IOException) {
                throw PdfTextException("That file couldn't be read as a PDF.")
            }
            document.use { doc ->
                val pages = doc.numberOfPages
                val last = minOf(pages, maxPages)
                val raw = PDFTextStripper().apply { startPage = 1; endPage = last }.getText(doc)
                val text = raw.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
                if (text.length < 200) {
                    throw PdfTextException("That PDF has almost no selectable text (scanned pages can't be read). Use a text PDF or a topic instead.")
                }
                PdfText(text.take(maxChars), pages, last, truncated = pages > last || text.length > maxChars)
            }
        } finally {
            temp.delete()
        }
    }
