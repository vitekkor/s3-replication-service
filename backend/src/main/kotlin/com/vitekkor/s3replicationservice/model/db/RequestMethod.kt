package com.vitekkor.s3replicationservice.model.db

import kotlinx.serialization.Serializable

@Serializable
enum class RequestMethod {
    PUT, DELETE;
}
