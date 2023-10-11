package com.vitekkor.s3replicationservice.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("s3")
data class S3Properties(
    val buckets: List<BucketProperties>,
    val threads: Int,
)

data class BucketProperties(
    val host: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val region: String,
)
