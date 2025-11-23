package dev.toliner.spector

import io.kotest.core.Tag

/**
 * Tag for integration tests that perform full classpath indexing.
 *
 * These tests are typically slower and should be excluded from fast test runs.
 */
object Integration : Tag()

/**
 * Tag for slow-running tests that may take significant time to complete.
 */
object Slow : Tag()
