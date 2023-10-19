package com.vitekkor.s3replicationservice.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val login: String,
    val password: String? = null,
    val roles: Set<String>,
    val isActive: Boolean,
    val scopes: Set<String>,
    val files: Set<String>,
    val ips: Set<String>,
)
