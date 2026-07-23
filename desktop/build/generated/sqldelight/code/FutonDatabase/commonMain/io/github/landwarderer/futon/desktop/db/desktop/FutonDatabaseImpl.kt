package io.github.landwarderer.futon.desktop.db.desktop

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.github.landwarderer.futon.desktop.db.DatabaseQueries
import io.github.landwarderer.futon.desktop.db.FutonDatabase
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<FutonDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = FutonDatabaseImpl.Schema

internal fun KClass<FutonDatabase>.newInstance(driver: SqlDriver): FutonDatabase =
    FutonDatabaseImpl(driver)

private class FutonDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), FutonDatabase {
  override val databaseQueries: DatabaseQueries = DatabaseQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE manga (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    url TEXT NOT NULL UNIQUE,
          |    title TEXT NOT NULL,
          |    source TEXT NOT NULL,
          |    coverUrl TEXT,
          |    description TEXT
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE chapter (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    mangaId INTEGER NOT NULL,
          |    url TEXT NOT NULL UNIQUE,
          |    name TEXT NOT NULL,
          |    number REAL NOT NULL,
          |    date INTEGER,
          |    FOREIGN KEY(mangaId) REFERENCES manga(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE favorite (
          |    mangaId INTEGER NOT NULL PRIMARY KEY,
          |    createdAt INTEGER NOT NULL,
          |    FOREIGN KEY(mangaId) REFERENCES manga(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE history (
          |    mangaId INTEGER NOT NULL PRIMARY KEY,
          |    chapterIndex INTEGER NOT NULL,
          |    pageIndex INTEGER NOT NULL,
          |    updatedAt INTEGER NOT NULL,
          |    FOREIGN KEY(mangaId) REFERENCES manga(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
