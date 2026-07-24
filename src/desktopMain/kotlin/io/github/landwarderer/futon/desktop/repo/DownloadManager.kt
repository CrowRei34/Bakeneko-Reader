package io.github.landwarderer.futon.desktop.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.koitharu.kotatsu.parsers.model.MangaChapter

data class DownloadProgress(
    val mangaUrl: String,
    val chapterUrl: String,
    val totalPages: Int,
    val downloadedPages: Int,
    val isComplete: Boolean = false,
    val isError: Boolean = false
)

object DownloadManager {
    private val baseDir = File(System.getProperty("user.home"), ".futon/downloads")
    
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    private fun getChapterDir(source: MangaParserSource, mangaUrl: String, chapterUrl: String): File {
        val sourceName = source.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val mangaHash = mangaUrl.hashCode().toString(16)
        val chapterHash = chapterUrl.hashCode().toString(16)
        return File(baseDir, "$sourceName/$mangaHash/$chapterHash")
    }

    fun isChapterDownloaded(source: MangaParserSource, mangaUrl: String, chapterUrl: String): Boolean {
        val dir = getChapterDir(source, mangaUrl, chapterUrl)
        if (!dir.exists() || !dir.isDirectory) return false
        val metaFile = File(dir, "completed.txt")
        return metaFile.exists()
    }

    fun hasAnyDownloads(source: MangaParserSource, mangaUrl: String): Boolean {
        val sourceName = source.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val mangaHash = mangaUrl.hashCode().toString(16)
        val dir = File(baseDir, "$sourceName/$mangaHash")
        if (!dir.exists() || !dir.isDirectory) return false
        return dir.listFiles()?.any { File(it, "completed.txt").exists() } == true
    }

    suspend fun downloadChapter(
        repo: MangaRepository,
        source: MangaParserSource,
        mangaUrl: String,
        chapter: MangaChapter
    ) {
        val chapterUrl = chapter.url
        val dir = getChapterDir(source, mangaUrl, chapterUrl)
        val downloadKey = "$mangaUrl|${chapterUrl}"
        
        if (isChapterDownloaded(source, mangaUrl, chapterUrl)) return
        
        if (_activeDownloads.value.containsKey(downloadKey)) return // Already downloading

        withContext(Dispatchers.IO) {
            try {
                dir.mkdirs()
                
                // Set initial progress state
                _activeDownloads.value = _activeDownloads.value + (downloadKey to DownloadProgress(
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    totalPages = 0,
                    downloadedPages = 0
                ))

                val pages = repo.getPages(chapter, source)
                val headers = repo.getHeaders(source)
                
                _activeDownloads.value = _activeDownloads.value + (downloadKey to DownloadProgress(
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    totalPages = pages.size,
                    downloadedPages = 0
                ))

                var downloadedCount = 0
                for ((index, page) in pages.withIndex()) {
                    val imageUrl = repo.getPageUrl(page, source)
                    val encodedUrl = imageUrl.replace(" ", "%20")
                    val file = File(dir, String.format("%04d.jpg", index))
                    
                    if (!file.exists()) {
                        downloadImage(encodedUrl, headers, file)
                    }
                    downloadedCount++
                    _activeDownloads.value = _activeDownloads.value + (downloadKey to DownloadProgress(
                        mangaUrl = mangaUrl,
                        chapterUrl = chapterUrl,
                        totalPages = pages.size,
                        downloadedPages = downloadedCount
                    ))
                }

                // Mark as completed
                File(dir, "completed.txt").writeText(System.currentTimeMillis().toString())
                _activeDownloads.value = _activeDownloads.value + (downloadKey to DownloadProgress(
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    totalPages = pages.size,
                    downloadedPages = pages.size,
                    isComplete = true
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                _activeDownloads.value = _activeDownloads.value + (downloadKey to DownloadProgress(
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    totalPages = 0,
                    downloadedPages = 0,
                    isError = true
                ))
            }
        }
    }
    
    private fun downloadImage(url: String, headers: Map<String, String>, dest: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.setRequestProperty("Referer", url)
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun getDownloadedPages(source: MangaParserSource, mangaUrl: String, chapterUrl: String): List<File> {
        val dir = getChapterDir(source, mangaUrl, chapterUrl)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.extension.lowercase() in listOf("jpg", "png", "jpeg", "webp", "gif") }
            ?.sortedBy { it.name } ?: emptyList()
    }
}
