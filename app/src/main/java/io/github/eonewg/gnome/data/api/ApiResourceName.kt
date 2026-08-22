package io.github.eonewg.gnome.data.api

/**
 * A canonical Memos API resource name such as `memos/abc` or `users/alice`.
 *
 * Parsing lives here so API repositories never need to make assumptions about
 * numeric identifiers or scatter path splitting throughout the codebase.
 */
internal class ApiResourceName private constructor(
    val collection: String,
    val identifier: String,
) {
    val value: String = "$collection/$identifier"

    companion object {
        fun parse(rawValue: String, expectedCollection: String): ApiResourceName {
            // Older local snapshots could append transport metadata after `|`.
            // Keep that migration handling centralized at the API boundary.
            val legacySeparator = rawValue.indexOf('|')
            val canonicalValue = if (legacySeparator >= 0) {
                rawValue.take(legacySeparator)
            } else {
                rawValue
            }
            val separator = canonicalValue.indexOf('/')
            require(separator > 0 && separator == canonicalValue.lastIndexOf('/')) {
                "Invalid Memos resource name: $rawValue"
            }

            val collection = canonicalValue.take(separator)
            val identifier = canonicalValue.substring(separator + 1)
            require(collection == expectedCollection && identifier.isNotBlank()) {
                "Expected $expectedCollection resource name, got: $rawValue"
            }
            return ApiResourceName(collection, identifier)
        }
    }
}
