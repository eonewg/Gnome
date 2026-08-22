package io.github.eonewg.gnome.data.model

/**
 * One tag of the local tag index with the number of live (non-deleted,
 * non-archived) memos that carry it. Queries return these ordered by
 * usage count descending, then tag ascending.
 */
data class TagUsage(
    val tag: String,
    val usageCount: Int,
)