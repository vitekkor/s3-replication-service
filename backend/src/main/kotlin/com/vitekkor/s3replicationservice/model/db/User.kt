package com.vitekkor.s3replicationservice.model.db

import org.springframework.data.annotation.Id

data class User(
    @Id
    val login: String,
    val password: String,
    val roles: List<String> = emptyList(),
    val isActive: Boolean,
    val claims: List<String> = emptyList(),
)
