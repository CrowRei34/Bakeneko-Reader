package io.github.landwarderer.futon.desktop.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.github.landwarderer.futon.desktop.db.desktop.newInstance
import io.github.landwarderer.futon.desktop.db.desktop.schema
import kotlin.Unit

public interface FutonDatabase : Transacter {
  public val databaseQueries: DatabaseQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = FutonDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): FutonDatabase =
        FutonDatabase::class.newInstance(driver)
  }
}
