package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.repository.RequestLogRepository
import com.vitekkor.s3replicationservice.service.S3Service
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date


@RestController
@RequestMapping("/api")
class S3Controller(
    private val s3Service: S3Service,
    private val requestLogRepository: RequestLogRepository,
) {

    @PostMapping("/upsert/{fileName}")
    fun uploadFile(
        @RequestPart("file") filePartMono: Mono<FilePart>,
        @PathVariable fileName: String,
    ): Mono<ResponseEntity<OperationResult>> {
        val upsertResult = OperationResult(fileName, Result.SUCCESSFUL)
        val timestamp = Date.from(Instant.now())
        return filePartMono.publishOn(Schedulers.boundedElastic()).flatMap { filePart ->
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
}
