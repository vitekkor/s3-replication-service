package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.model.Status
import com.vitekkor.s3replicationservice.model.Status.FileStatus.Companion.getByRequestMethod
import com.vitekkor.s3replicationservice.service.ReplicableS3Client
import com.vitekkor.s3replicationservice.service.S3Service
import com.vitekkor.s3replicationservice.util.FileUtils
import kotlinx.serialization.Serializable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.function.Tuple2
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date
import java.util.UUID


@RestController
@RequestMapping("/api")
class S3Controller(
    private val s3Service: S3Service,
    private val s3Clients: List<ReplicableS3Client>,
) {

    @PostMapping("/upsert/{fileName}")
    fun uploadFile(
        @RequestPart("file") filePart: Mono<FilePart>,
        @PathVariable fileName: String,
    ): Mono<ResponseEntity<OperationResult>> {
        val upsertResult = OperationResult(fileName, Result.SUCCESSFUL)
        val timestamp = Date.from(Instant.now())
        return filePart.publishOn(Schedulers.boundedElastic()).flatMap { filePart ->
            s3Service.upsert(filePart, fileName, timestamp).reduce(upsertResult) { t, u ->
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

    @PostMapping("/upload42")
    fun uploadHandler(
        @RequestHeader headers: HttpHeaders,
        @RequestBody body: Flux<ByteBuffer>,
    ): Mono<ResponseEntity<UploadResult>> {
        val length: Long = headers.getContentLength()
        val fileKey = UUID.randomUUID().toString()
        val metadata: Map<String, String> = HashMap()
        val future = s3Clients.first().s3AsyncClient
            .putObject(
                PutObjectRequest.builder()
                    .bucket(s3Clients.first().bucket)
                    .contentLength(length)
                    .key(fileKey)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM.toString())
                    .metadata(metadata)
                    .build(),
                AsyncRequestBody.fromPublisher(body)
            )
        return Mono.fromFuture(future)
            .map { response ->
                FileUtils.checkSdkResponse(response)
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(UploadResult(HttpStatus.CREATED, arrayOf(fileKey)))
            }
    }

    @Serializable
    data class UploadResult(val status: HttpStatus, val files: Array<String>)
}

private operator fun <T1, T2> Tuple2<T1, T2>.component1(): T1 = t1
private operator fun <T1, T2> Tuple2<T1, T2>.component2(): T2 = t2
