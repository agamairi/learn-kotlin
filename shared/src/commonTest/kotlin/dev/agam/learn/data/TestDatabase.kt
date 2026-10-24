package dev.agam.learn.data

import app.cash.sqldelight.db.SqlDriver

expect fun createInMemoryTestDriver(): SqlDriver
