package io.github.landwarderer.futon.desktop.repo

import io.github.landwarderer.futon.desktop.db.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaListFilter

class MangaRepository(private val context: MangaLoaderContext) {
    
    suspend fun fetchCatalog(source: MangaParserSource, page: Int = 1, query: String = ""): List<Manga> = withContext(Dispatchers.IO) {
        val parser = context.newParserInstance(source)
        val order = parser.availableSortOrders.firstOrNull() ?: SortOrder.UPDATED
        val filter = if (query.isNotBlank()) MangaListFilter(query = query) else MangaListFilter.EMPTY
        return@withContext parser.getList(0, order, filter)
    }
    
    suspend fun getMangaDetails(source: MangaParserSource, manga: Manga): Manga = withContext(Dispatchers.IO) {
        val parser = context.newParserInstance(source)
        return@withContext parser.getDetails(manga)
    }
    
    suspend fun getPages(chapter: org.koitharu.kotatsu.parsers.model.MangaChapter, source: MangaParserSource): List<org.koitharu.kotatsu.parsers.model.MangaPage> = withContext(Dispatchers.IO) {
        val parser = context.newParserInstance(source)
        return@withContext parser.getPages(chapter)
    }
    
    suspend fun getPageUrl(page: org.koitharu.kotatsu.parsers.model.MangaPage, source: MangaParserSource): String = withContext(Dispatchers.IO) {
        val parser = context.newParserInstance(source)
        return@withContext parser.getPageUrl(page)
    }
    
    suspend fun getHeaders(source: MangaParserSource): Map<String, String> = withContext(Dispatchers.IO) {
        val parser = context.newParserInstance(source)
        val headers = parser.getRequestHeaders()
        return@withContext headers.map { it.first to it.second }.toMap()
    }
    
    // DB Methods
    suspend fun toggleFavorite(manga: Manga, source: MangaParserSource, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val db = DatabaseManager.database.databaseQueries
        
        // Find or insert manga
        var dbManga = db.getMangaByUrl(manga.url, source.name).executeAsOneOrNull()
        if (dbManga == null) {
            db.insertManga(
                url = manga.url,
                title = manga.title,
                source = source.name,
                coverUrl = manga.coverUrl,
                description = manga.description
            )
            dbManga = db.getMangaByUrl(manga.url, source.name).executeAsOne()
        }
        
        if (isFavorite) {
            db.addFavorite(dbManga.id, System.currentTimeMillis())
        } else {
            db.removeFavorite(dbManga.id)
        }
    }
    
    suspend fun isFavorite(mangaUrl: String, source: MangaParserSource): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseManager.database.databaseQueries
        val dbManga = db.getMangaByUrl(mangaUrl, source.name).executeAsOneOrNull()
        if (dbManga != null) {
            return@withContext db.isFavorite(dbManga.id).executeAsOne() > 0
        }
        return@withContext false
    }
    
    suspend fun getFavorites(): List<Pair<Manga, MangaParserSource>> = withContext(Dispatchers.IO) {
        val db = DatabaseManager.database.databaseQueries
        val dbFavorites = db.getFavorites().executeAsList()
        
        return@withContext dbFavorites.map { dbManga ->
            val sourceEnum = MangaParserSource.values().find { it.name == dbManga.source } ?: MangaParserSource.MANGADEX
            val manga = Manga(
                id = 0L,
                title = dbManga.title,
                altTitle = null,
                url = dbManga.url,
                publicUrl = dbManga.url,
                rating = 0f,
                isNsfw = false,
                coverUrl = dbManga.coverUrl,
                tags = emptySet(),
                state = null,
                author = null,
                description = dbManga.description,
                source = sourceEnum
            )
            Pair(manga, sourceEnum)
        }
    }
    
    suspend fun upsertHistory(manga: Manga, source: MangaParserSource, chapterIndex: Int, pageIndex: Int) = withContext(Dispatchers.IO) {
        val db = DatabaseManager.database.databaseQueries
        
        var dbManga = db.getMangaByUrl(manga.url, source.name).executeAsOneOrNull()
        if (dbManga == null) {
            db.insertManga(
                url = manga.url,
                title = manga.title,
                source = source.name,
                coverUrl = manga.coverUrl,
                description = manga.description
            )
            dbManga = db.getMangaByUrl(manga.url, source.name).executeAsOne()
        }
        
        db.upsertHistory(
            mangaId = dbManga.id,
            chapterIndex = chapterIndex.toLong(),
            pageIndex = pageIndex.toLong(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    suspend fun getHistoryList(): List<Pair<Manga, Pair<Int, Int>>> = withContext(Dispatchers.IO) {
        val db = DatabaseManager.database.databaseQueries
        val dbHistory = db.getHistoryList().executeAsList()
        
        return@withContext dbHistory.map { row ->
            val sourceEnum = MangaParserSource.values().find { it.name == row.source } ?: MangaParserSource.MANGADEX
            val manga = Manga(
                id = 0L,
                title = row.title,
                altTitle = null,
                url = row.url,
                publicUrl = row.url,
                rating = 0f,
                isNsfw = false,
                coverUrl = row.coverUrl,
                tags = emptySet(),
                state = null,
                author = null,
                description = row.description,
                source = sourceEnum
            )
            Pair(manga, Pair(row.chapterIndex.toInt(), row.pageIndex.toInt()))
        }
    }
}
