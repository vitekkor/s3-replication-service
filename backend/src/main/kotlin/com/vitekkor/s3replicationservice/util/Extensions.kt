package com.vitekkor.s3replicationservice.util

import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart

val FilePart.contentType
    get() = headers().contentType ?: MediaType.APPLICATION_OCTET_STREAM
