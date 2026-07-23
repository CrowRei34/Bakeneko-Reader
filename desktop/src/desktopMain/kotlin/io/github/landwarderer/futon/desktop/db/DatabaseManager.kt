package io.github.landwarderer.futon.desktop.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

object DatabaseManager {
    lateinit var database: FutonDatabase
        private set

    fun init() {
        // Create an in-memory database or a file-based one
        val dbFile = File(System.getProperty("user.home"), ".futon/futon.db")
        dbFile.parentFile.mkdirs()
        
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${dbFile.absolutePath}")
        
        // Check if we need to create the schema (basic check)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            FutonDatabase.Schema.create(driver)
        } else {
            // Wait, SQLDelight needs us to create schema if tables don't exist.
            // Using a simple try-catch for demo purposes
            try {
                FutonDatabase.Schema.create(driver)
            } catch (e: Exception) {
                // Ignore if tables already exist
            }
        }
        
        database = FutonDatabase(driver)
    }
}
