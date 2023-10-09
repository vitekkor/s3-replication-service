package com.vitekkor.s3replicationservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class S3ReplicationServiceApplication

fun main(args: Array<String>) {
    runApplication<S3ReplicationServiceApplication>(*args)
}
