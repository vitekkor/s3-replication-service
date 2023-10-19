package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.model.Status
import com.vitekkor.s3replicationservice.model.Status.FileStatus.Companion.getByRequestMethod
import com.vitekkor.s3replicationservice.service.S3Service
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date


@RestController
@RequestMapping("/api")
class S3Controller(
    private val s3Service: S3Service,
) {

    @DeleteMapping("/delete/{fileName}")
    fun deleteFile(
        @PathVariable fileName: String,
    ): Mono<ResponseEntity<OperationResult>> {
        val deleteResult = OperationResult(fileName, Result.SUCCESSFUL)
        val timestamp = Date.from(Instant.now())
        return s3Service.delete(fileName, timestamp).reduce(deleteResult) { t, u ->
            t.otherResults += u
            t
        }.map {
            OperationResult(it.fileName, it.getFinalResult())
        }.map {
            val statusCode = if (!it.isSuccessful) 500 else 200
            ResponseEntity.status(statusCode).body(it)
        }
    }

    @GetMapping("/get/{fileName}")
    fun getFile(@PathVariable fileName: String): Flux<ByteBuffer> {
        return s3Service.get(fileName)
    }

    @GetMapping("/statuses")
    fun getFileStatuses(): List<Status> {
        return s3Service.getFileStatuses().groupBy { it.fileName }.mapNotNull { (key, value) ->
            Status(key, value.map {
                Status.BucketAndStatus(it.s3StorageName, getByRequestMethod(it.method), it.fileProperties)
            })
        }
    }

    @PostMapping("/upsert/{fileName}")
    fun uploadHandler(
        @RequestHeader headers: HttpHeaders,
        @RequestBody body: Flux<ByteBuffer>,
        @PathVariable fileName: String,
    ): Mono<ResponseEntity<OperationResult>> {
        val length: Long = headers.contentLength
        val contentType = (headers.contentType ?: MediaType.APPLICATION_OCTET_STREAM).toString()
        val timestamp = Date.from(Instant.now())
        val upsertResult = OperationResult(fileName, Result.SUCCESSFUL)
        val sharedBody = body.share()
        return s3Service.newUpsert(sharedBody, fileName, contentType, length, timestamp).reduce(upsertResult) { t, u ->
            t.otherResults += u
            t
        }.map {
            OperationResult(it.fileName, it.getFinalResult())
        }.map {
            val statusCode = if (!it.isSuccessful) 500 else 200
            ResponseEntity.status(statusCode).body(it)
        }
    }
}
