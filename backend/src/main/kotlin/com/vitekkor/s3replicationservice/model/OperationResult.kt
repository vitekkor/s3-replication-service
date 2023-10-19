package com.vitekkor.s3replicationservice.model

import kotlinx.serialization.Serializable

@Serializable
data class OperationResult(
    val fileName: String,
    val result: Result,
) {
    val otherResults: MutableList<OperationResult> = mutableListOf()
    val isSuccessful
        get() = result == Result.SUCCESSFUL

    fun getFinalResult() = if (otherResults.any { it.isSuccessful }) Result.SUCCESSFUL else Result.FAILED
}

enum class Result {
    SUCCESSFUL, FAILED;
}
