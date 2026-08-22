package io.github.eonewg.gnome.data.local

import androidx.room.withTransaction

/**
 * Runs multi-row writes atomically. Kept as an interface so non-Android code
 * (SyncEngine's JVM tests) can run without a Room database.
 */
interface TransactionRunner {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

/** Production implementation backed by Room's withTransaction. */
class RoomTransactionRunner(
    private val database: GnomeDatabase,
) : TransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }
}
