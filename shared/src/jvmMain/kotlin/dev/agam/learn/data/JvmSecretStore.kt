package dev.agam.learn.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/** In-memory secret store intended for JVM tests and local tooling. */
class InMemorySecretStore : SecretStore {
    private var value: String? = null

    override suspend fun get(): String? = value

    override suspend fun set(value: String) {
        this.value = value
    }

    override suspend fun delete() {
        value = null
    }
}

/** Creates a JVM SQLite driver and initializes the SQLDelight schema. */
actual class LearnDatabaseDriverFactory actual constructor(context: Any?) {
    actual fun createDriver(databaseName: String): SqlDriver {
        val url = if (databaseName == ":memory:") {
            "jdbc:sqlite::memory:"
        } else {
            "jdbc:sqlite:$databaseName"
        }
        return JdbcSqliteDriver(url).also { LearnDatabase.Schema.create(it) }
    }
}

actual fun createSecretStore(database: LearnDatabase): SecretStore = InMemorySecretStore()
