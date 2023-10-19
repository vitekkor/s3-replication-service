package com.vitekkor.s3replicationservice.util

class ExtensionMatcher(private val extension: String) {
    fun matches(path: String): Boolean {
        return path.split(".").lastOrNull() == extension
    }
}
