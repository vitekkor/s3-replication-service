package com.vitekkor.s3replicationservice.exceptions.handling

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.LockedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.services.s3.model.NoSuchKeyException


@RestControllerAdvice
internal class RestExceptionHandler {
    @ExceptionHandler(NoSuchKeyException::class)
    fun fileNotFound(ex: NoSuchKeyException): ResponseEntity<Any> {
        return ResponseEntity.notFound().build()
    }

    @ExceptionHandler(LockedException::class)
    fun accountIsLocked(ex: LockedException): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.LOCKED).build()
    }

    @ExceptionHandler(ApiError::class)
    fun handleApiError(ex: ApiError): ResponseEntity<Any> {
        return ResponseEntity.status(ex.code).body(ex.message)
    }
}
