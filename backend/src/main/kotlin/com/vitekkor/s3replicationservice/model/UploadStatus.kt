package com.vitekkor.s3replicationservice.model

import software.amazon.awssdk.services.s3.model.CompletedPart


data class UploadStatus(
    val contentType: String,
    val fileKey: String,
    var uploadId: String? = null,
    var partCounter: Int = 0,
    var buffered: Long = 0,
    var completedParts: MutableMap<Int, CompletedPart> = HashMap(),
) {
    fun addBuffered(buffered: Int) {
        this.buffered += buffered
    }

    val addedPartCounter: Int
        get() = ++partCounter
}
