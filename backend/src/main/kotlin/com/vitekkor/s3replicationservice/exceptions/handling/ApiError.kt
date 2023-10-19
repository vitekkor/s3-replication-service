package com.vitekkor.s3replicationservice.exceptions.handling

sealed class ApiError(val code: Int, override val message: String) : Exception(message)

class UserCreationError(code: Int, message: String) : ApiError(code, message)
