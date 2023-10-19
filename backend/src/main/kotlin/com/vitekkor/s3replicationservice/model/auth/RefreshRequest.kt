package com.vitekkor.s3replicationservice.model.auth

import kotlinx.serialization.Serializable


@Serializable
data class RefreshRequest(val refreshToken: String)
