package io.github.landwarderer.futon.desktop.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class DatabaseQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getMangaByUrl(
    url: String,
    source: String,
    mapper: (
      id: Long,
      url: String,
      title: String,
      source: String,
      coverUrl: String?,
      description: String?,
    ) -> T,
  ): Query<T> = GetMangaByUrlQuery(url, source) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4),
      cursor.getString(5)
    )
  }

  public fun getMangaByUrl(url: String, source: String): Query<Manga> = getMangaByUrl(url, source) {
      id, url_, title, source_, coverUrl, description ->
    Manga(
      id,
      url_,
      title,
      source_,
      coverUrl,
      description
    )
  }

  public fun <T : Any> getMangaById(id: Long, mapper: (
    id: Long,
    url: String,
    title: String,
    source: String,
    coverUrl: String?,
    description: String?,
  ) -> T): Query<T> = GetMangaByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4),
      cursor.getString(5)
    )
  }

  public fun getMangaById(id: Long): Query<Manga> = getMangaById(id) { id_, url, title, source,
      coverUrl, description ->
    Manga(
      id_,
      url,
      title,
      source,
      coverUrl,
      description
    )
  }

  public fun <T : Any> getChaptersForManga(mangaId: Long, mapper: (
    id: Long,
    mangaId: Long,
    url: String,
    name: String,
    number: Double,
    date: Long?,
  ) -> T): Query<T> = GetChaptersForMangaQuery(mangaId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getDouble(4)!!,
      cursor.getLong(5)
    )
  }

  public fun getChaptersForManga(mangaId: Long): Query<Chapter> = getChaptersForManga(mangaId) { id,
      mangaId_, url, name, number, date ->
    Chapter(
      id,
      mangaId_,
      url,
      name,
      number,
      date
    )
  }

  public fun isFavorite(mangaId: Long): Query<Long> = IsFavoriteQuery(mangaId) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> getFavorites(mapper: (
    id: Long,
    url: String,
    title: String,
    source: String,
    coverUrl: String?,
    description: String?,
  ) -> T): Query<T> = Query(1_230_643_776, arrayOf("manga", "favorite"), driver, "Database.sq",
      "getFavorites", """
  |SELECT manga.id, manga.url, manga.title, manga.source, manga.coverUrl, manga.description FROM manga
  |INNER JOIN favorite ON manga.id = favorite.mangaId
  |ORDER BY favorite.createdAt DESC
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4),
      cursor.getString(5)
    )
  }

  public fun getFavorites(): Query<Manga> = getFavorites { id, url, title, source, coverUrl,
      description ->
    Manga(
      id,
      url,
      title,
      source,
      coverUrl,
      description
    )
  }

  public fun <T : Any> getHistoryByMangaId(mangaId: Long, mapper: (
    mangaId: Long,
    chapterIndex: Long,
    pageIndex: Long,
    updatedAt: Long,
  ) -> T): Query<T> = GetHistoryByMangaIdQuery(mangaId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!
    )
  }

  public fun getHistoryByMangaId(mangaId: Long): Query<History> = getHistoryByMangaId(mangaId) {
      mangaId_, chapterIndex, pageIndex, updatedAt ->
    History(
      mangaId_,
      chapterIndex,
      pageIndex,
      updatedAt
    )
  }

  public fun <T : Any> getHistoryList(mapper: (
    id: Long,
    url: String,
    title: String,
    source: String,
    coverUrl: String?,
    description: String?,
    chapterIndex: Long,
    pageIndex: Long,
    updatedAt: Long,
  ) -> T): Query<T> = Query(-487_849_733, arrayOf("manga", "history"), driver, "Database.sq",
      "getHistoryList", """
  |SELECT manga.id, manga.url, manga.title, manga.source, manga.coverUrl, manga.description, history.chapterIndex, history.pageIndex, history.updatedAt
  |FROM manga
  |INNER JOIN history ON manga.id = history.mangaId
  |ORDER BY history.updatedAt DESC
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4),
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getHistoryList(): Query<GetHistoryList> = getHistoryList { id, url, title, source,
      coverUrl, description, chapterIndex, pageIndex, updatedAt ->
    GetHistoryList(
      id,
      url,
      title,
      source,
      coverUrl,
      description,
      chapterIndex,
      pageIndex,
      updatedAt
    )
  }

  public fun insertManga(
    url: String,
    title: String,
    source: String,
    coverUrl: String?,
    description: String?,
  ) {
    driver.execute(1_501_283_836, """
        |INSERT OR REPLACE INTO manga(url, title, source, coverUrl, description)
        |VALUES (?, ?, ?, ?, ?)
        """.trimMargin(), 5) {
          bindString(0, url)
          bindString(1, title)
          bindString(2, source)
          bindString(3, coverUrl)
          bindString(4, description)
        }
    notifyQueries(1_501_283_836) { emit ->
      emit("manga")
    }
  }

  public fun insertChapter(
    mangaId: Long,
    url: String,
    name: String,
    number: Double,
    date: Long?,
  ) {
    driver.execute(-471_659_371, """
        |INSERT OR REPLACE INTO chapter(mangaId, url, name, number, date)
        |VALUES (?, ?, ?, ?, ?)
        """.trimMargin(), 5) {
          bindLong(0, mangaId)
          bindString(1, url)
          bindString(2, name)
          bindDouble(3, number)
          bindLong(4, date)
        }
    notifyQueries(-471_659_371) { emit ->
      emit("chapter")
    }
  }

  public fun addFavorite(mangaId: Long?, createdAt: Long) {
    driver.execute(1_698_127_582, """
        |INSERT OR IGNORE INTO favorite(mangaId, createdAt)
        |VALUES (?, ?)
        """.trimMargin(), 2) {
          bindLong(0, mangaId)
          bindLong(1, createdAt)
        }
    notifyQueries(1_698_127_582) { emit ->
      emit("favorite")
    }
  }

  public fun removeFavorite(mangaId: Long) {
    driver.execute(-743_015_137, """DELETE FROM favorite WHERE mangaId = ?""", 1) {
          bindLong(0, mangaId)
        }
    notifyQueries(-743_015_137) { emit ->
      emit("favorite")
    }
  }

  public fun upsertHistory(
    mangaId: Long?,
    chapterIndex: Long,
    pageIndex: Long,
    updatedAt: Long,
  ) {
    driver.execute(748_555_686, """
        |INSERT OR REPLACE INTO history(mangaId, chapterIndex, pageIndex, updatedAt)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindLong(0, mangaId)
          bindLong(1, chapterIndex)
          bindLong(2, pageIndex)
          bindLong(3, updatedAt)
        }
    notifyQueries(748_555_686) { emit ->
      emit("history")
    }
  }

  private inner class GetMangaByUrlQuery<out T : Any>(
    public val url: String,
    public val source: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("manga", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("manga", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_970_598_021,
        """SELECT manga.id, manga.url, manga.title, manga.source, manga.coverUrl, manga.description FROM manga WHERE url = ? AND source = ?""",
        mapper, 2) {
      bindString(0, url)
      bindString(1, source)
    }

    override fun toString(): String = "Database.sq:getMangaByUrl"
  }

  private inner class GetMangaByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("manga", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("manga", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_587_588_721,
        """SELECT manga.id, manga.url, manga.title, manga.source, manga.coverUrl, manga.description FROM manga WHERE id = ?""",
        mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Database.sq:getMangaById"
  }

  private inner class GetChaptersForMangaQuery<out T : Any>(
    public val mangaId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chapter", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chapter", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-663_721_912,
        """SELECT chapter.id, chapter.mangaId, chapter.url, chapter.name, chapter.number, chapter.date FROM chapter WHERE mangaId = ? ORDER BY number DESC""",
        mapper, 1) {
      bindLong(0, mangaId)
    }

    override fun toString(): String = "Database.sq:getChaptersForManga"
  }

  private inner class IsFavoriteQuery<out T : Any>(
    public val mangaId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("favorite", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("favorite", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(725_472_069, """SELECT count(*) FROM favorite WHERE mangaId = ?""",
        mapper, 1) {
      bindLong(0, mangaId)
    }

    override fun toString(): String = "Database.sq:isFavorite"
  }

  private inner class GetHistoryByMangaIdQuery<out T : Any>(
    public val mangaId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("history", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("history", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_079_350_107,
        """SELECT history.mangaId, history.chapterIndex, history.pageIndex, history.updatedAt FROM history WHERE mangaId = ?""",
        mapper, 1) {
      bindLong(0, mangaId)
    }

    override fun toString(): String = "Database.sq:getHistoryByMangaId"
  }
}
