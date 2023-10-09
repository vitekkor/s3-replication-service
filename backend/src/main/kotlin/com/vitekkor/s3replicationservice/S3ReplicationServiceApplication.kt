package com.vitekkor.s3replicationservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
class S3ReplicationServiceApplication

fun main(args: Array<String>) {
    runApplication<S3ReplicationServiceApplication>(*args)
}
