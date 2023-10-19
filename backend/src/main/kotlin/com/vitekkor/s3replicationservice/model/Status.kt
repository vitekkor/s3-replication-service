package com.vitekkor.s3replicationservice.model

import com.vitekkor.s3replicationservice.model.db.RequestMethod
import kotlinx.serialization.Serializable

@Serializable
data class Status(val fileName: String, val bucketAndStatus: List<BucketAndStatus>) {

    @Serializable
    data class BucketAndStatus(val bucketName: String, val status: FileStatus, val fileProperties: Map<String, String>)

    enum class FileStatus {
        EXISTS, REMOVED;

        companion object {
            fun getByRequestMethod(requestMethod: RequestMethod): FileStatus {
                return if (requestMethod == RequestMethod.PUT) {
                    EXISTS
                } else {
                    REMOVED
                }
            }
        }
    }
}
