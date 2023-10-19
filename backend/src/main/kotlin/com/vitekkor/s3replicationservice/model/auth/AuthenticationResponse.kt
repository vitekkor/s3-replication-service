package com.vitekkor.s3replicationservice.model.auth

import kotlinx.serialization.Serializable


@Serializable
data class AuthenticationResponse(
    val type: String? = "Bearer",
    val accessToken: String?,
    val refreshToken: String?,
    val error: String? = null,
) {
    constructor(error: String?) : this(null, null, null, error)
}
