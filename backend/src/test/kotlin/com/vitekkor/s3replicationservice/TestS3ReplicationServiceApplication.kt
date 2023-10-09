package com.vitekkor.s3replicationservice

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.with
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestS3ReplicationServiceApplication {

    @Bean
    @ServiceConnection
    fun cassandraContainer(): CassandraContainer<*> {
        return CassandraContainer(DockerImageName.parse("cassandra:5.0"))
    }

}

fun main(args: Array<String>) {
    fromApplication<S3ReplicationServiceApplication>().with(TestS3ReplicationServiceApplication::class).run(*args)
}
