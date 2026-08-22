package io.github.eonewg.gnome.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One row per (memo, tag) pair of the local tag index, derived from memo
 * content by [io.github.eonewg.gnome.core.tag.MemosTagParser]. `memoId` is the
 * local memo identifier (the `memos.identifier` primary key), and the stored
 * tag set already includes implied slash ancestors (`#a/b` also indexes `a`).
 * Rows are rebuilt in the same transaction as every memo content write and
 * dropped on soft/hard delete; archive visibility is filtered at query time
 * by joining `memos`.
 */
@Entity(
    tableName = "memo_tags",
    primaryKeys = ["accountKey", "memoId", "tag"],
    indices = [
        Index(value = ["accountKey", "tag"])
    ]
)
data class MemoTagEntity(
    val accountKey: String,
    val memoId: String,
    val tag: String,
)
