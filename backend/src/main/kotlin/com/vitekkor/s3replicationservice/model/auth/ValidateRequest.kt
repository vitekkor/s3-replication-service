package com.vitekkor.s3replicationservice.model.auth

import kotlinx.serialization.Serializable


@Serializable
data class ValidateRequest(val token: String, val type: TokenType) {
    enum class TokenType {
        REFRESH, ACCESS
    }
}
