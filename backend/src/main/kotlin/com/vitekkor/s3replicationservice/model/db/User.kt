package com.vitekkor.s3replicationservice.model.db

import org.springframework.data.annotation.Id

data class User(
    @Id
    val login: String,
    val password: String,
    val roles: Set<String> = emptySet(),
    val isActive: Boolean,
    val claims: Set<String> = emptySet(),
    val ips: Set<String> = emptySet(),
)
