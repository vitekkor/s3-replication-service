package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.service.S3Service
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers


@RestController
@RequestMapping("/api")
class S3Controller(private val s3Service: S3Service) {

    @PostMapping("/upload")
    fun uploadFile(
        @RequestPart("file") filePartMono: Mono<FilePart>,
        @RequestHeader(HttpHeaders.CONTENT_LENGTH) contentLength: Long,
    ): Mono<Void> {
        return filePartMono.publishOn(Schedulers.boundedElastic()).flatMap { filePart ->
            s3Service.upsert(filePart, "test.txt", contentLength)
            Mono.empty()
        }
    }
}
