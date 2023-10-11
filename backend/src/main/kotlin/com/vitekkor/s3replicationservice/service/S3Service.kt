package com.vitekkor.s3replicationservice.service

import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutorService

@Service
class S3Service(
    private val s3Clients: List<ReplicableS3Client>,
    private val s3ExecutorService: ExecutorService,
) {
    fun upsert(filePart: FilePart, fileName: String, contentLength: Long) {
        s3Clients.map { s3Client ->
            s3Client.uploadObject(filePart).subscribe()
        }
    }
}
