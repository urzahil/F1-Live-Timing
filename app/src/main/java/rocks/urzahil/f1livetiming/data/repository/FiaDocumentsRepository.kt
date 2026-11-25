package rocks.urzahil.f1livetiming.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.api.awaitResponse
import rocks.urzahil.f1livetiming.data.api.cancellableResult
import rocks.urzahil.f1livetiming.data.model.FiaDocument
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Lists FIA decision documents through the configured backend and downloads their PDFs into
 * [cacheDir]. Published documents never change, so a downloaded PDF is reused as long as it stays cached.
 */
class FiaDocumentsRepository(private val cacheDir: File) {

    private companion object {
        // Keeps the PDF cache bounded; the least recently opened files are deleted first.
        const val MAX_CACHED_PDFS = 30

        // Decision documents are a few hundred KB; this only stops a misbehaving backend from filling the disk.
        const val STALE_PART_MILLIS = 60L * 60 * 1000

        const val MAX_PDF_BYTES = 25L * 1024 * 1024
    }

    suspend fun fetchDocuments(baseUrl: String, year: Int, round: Int): Result<List<FiaDocument>> =
        cancellableResult {
            NetworkClient.createLiveService(baseUrl).getFiaDocuments(year, round).documents.orEmpty()
                // The URLs come from a user-configurable backend: keep only web links.
                .filter { isWebUrl(it.fileUrl) }
                .sortedByDescending { it.publishedAt.orEmpty() }
        }

    /**
     * Returns the local copy of the PDF at [url], downloading it first when it is not cached yet. The
     * download is cancellable and capped at [MAX_PDF_BYTES], so a rogue endpoint cannot fill the storage.
     */
    suspend fun downloadPdf(url: String): Result<File> = withContext(Dispatchers.IO) {
        cancellableResult {
            val httpUrl = url.trim().toHttpUrlOrNull()
                ?: throw IOException("Unsupported document address")
            val dir = File(cacheDir, "fia-docs").apply { mkdirs() }
            val target = File(dir, "${sha1(url)}.pdf")
            if (target.length() > 0) {
                target.setLastModified(System.currentTimeMillis())
                return@cancellableResult target
            }

            // A unique temp file per download: re-opening a document while an earlier download is still
            // winding down must not make two writers share, rename and delete the same file.
            val tmp = File.createTempFile("${target.name}.", ".part", dir)
            try {
                NetworkClient.okHttpClient.newCall(Request.Builder().url(httpUrl).build()).awaitResponse().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val declared = response.body.contentLength()
                    if (declared > MAX_PDF_BYTES) throw IOException("Document too large")
                    tmp.outputStream().use { out -> response.body.byteStream().copyLimited(out, MAX_PDF_BYTES) }
                }
                // A captcha or error page served with status 200 would otherwise reach the PDF renderer.
                if (!tmp.hasPdfHeader()) throw IOException("Not a PDF document")
                // Atomic on the same file system; another download of the same document may have won already.
                if (!tmp.renameTo(target) && target.length() <= 0) throw IOException("Unable to store the PDF")
            } finally {
                tmp.delete()
            }
            prune(dir)
            target
        }
    }

    private suspend fun InputStream.copyLimited(out: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            // Lets a cancelled download stop between reads instead of draining the whole body.
            currentCoroutineContext().ensureActive()
            val read = read(buffer)
            if (read < 0) return
            total += read
            if (total > limit) throw IOException("Document too large")
            out.write(buffer, 0, read)
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
        // Temp files left behind by a process that was killed mid-download.
        val staleBefore = System.currentTimeMillis() - STALE_PART_MILLIS
        dir.listFiles { file -> file.name.endsWith(".part") }.orEmpty()
            .filter { it.lastModified() < staleBefore }.forEach { it.delete() }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** True for an absolute http(s) address; anything else (file:, content:, intent:, javascript:) is rejected. */
internal fun isWebUrl(url: String?): Boolean {
    val scheme = url?.trim()?.toHttpUrlOrNull()?.scheme
    return scheme == "https" || scheme == "http"
}
