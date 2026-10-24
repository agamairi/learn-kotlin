package dev.agam.learn.data

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform database-driver factory.
 *
 * Android callers pass an Android context as [context]. JVM callers
 * may leave it null; the JVM implementation uses SQLite and creates a fresh in-memory
 * database when the name is `:memory:`.
 */
expect class LearnDatabaseDriverFactory(context: Any? = null) {
    /** Creates a driver and initializes the SQLDelight schema when required. */
    fun createDriver(databaseName: String = "learn.db"): SqlDriver
}
