package com.vitekkor.s3replicationservice.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding


@ConfigurationProperties("jwt")
data class JwtProperties @ConstructorBinding constructor(val secretKey: String, val validityInMs: Long)
