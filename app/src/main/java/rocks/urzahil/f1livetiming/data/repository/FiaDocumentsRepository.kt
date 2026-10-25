package rocks.urzahil.f1livetiming.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.model.FiaDocument
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Lists FIA decision documents through the configured backend and downloads their PDFs into
 * [cacheDir]. Published documents never change, so a downloaded PDF is reused as long as it stays cached.
 */
class FiaDocumentsRepository(private val cacheDir: File) {

    private companion object {
        // Keeps the PDF cache bounded; the least recently opened files are deleted first.
        const val MAX_CACHED_PDFS = 30
    }

    suspend fun fetchDocuments(baseUrl: String, year: Int, round: Int): Result<List<FiaDocument>> =
        runCatching {
            NetworkClient.createLiveService(baseUrl).getFiaDocuments(year, round).documents.orEmpty()
                .filter { !it.fileUrl.isNullOrBlank() }
                .sortedByDescending { it.publishedAt.orEmpty() }
        }

    /** Returns the local copy of the PDF at [url], downloading it first when it is not cached yet. */
    suspend fun downloadPdf(url: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(cacheDir, "fia-docs").apply { mkdirs() }
            val target = File(dir, "${sha1(url)}.pdf")
            if (target.length() > 0) {
                target.setLastModified(System.currentTimeMillis())
                return@runCatching target
            }

            val tmp = File(dir, "${target.name}.part")
            NetworkClient.okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            // A captcha or error page served with status 200 would otherwise reach the PDF renderer.
            if (!tmp.hasPdfHeader()) {
                tmp.delete()
                throw IOException("Not a PDF document")
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Unable to store the PDF")
            }
            prune(dir)
            target
        }
    }

    private fun File.hasPdfHeader(): Boolean {
        val header = ByteArray(5)
        val read = inputStream().use { it.read(header) }
        return read == header.size && String(header, Charsets.US_ASCII) == "%PDF-"
    }

    private fun prune(dir: File) {
        val pdfs = dir.listFiles { file -> file.name.endsWith(".pdf") }.orEmpty()
        pdfs.sortedByDescending { it.lastModified() }.drop(MAX_CACHED_PDFS).forEach { it.delete() }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
