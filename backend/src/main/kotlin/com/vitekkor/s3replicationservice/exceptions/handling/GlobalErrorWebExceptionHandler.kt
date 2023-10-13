package com.vitekkor.s3replicationservice.exceptions.handling

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.services.s3.model.NoSuchKeyException


@RestControllerAdvice
internal class RestExceptionHandler {
    @ExceptionHandler(NoSuchKeyException::class)
    fun fileNotFound(ex: NoSuchKeyException): ResponseEntity<Any> {
        return ResponseEntity.notFound().build()
    }
}
