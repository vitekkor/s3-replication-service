package com.vitekkor.s3replicationservice.configuration

import com.vitekkor.s3replicationservice.configuration.properties.S3Properties
import com.vitekkor.s3replicationservice.service.ReplicableS3Client
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Configuration
class ApplicationConfiguration {
    @Bean
    fun s3Clients(s3Properties: S3Properties): List<ReplicableS3Client> {
        require(s3Properties.buckets.isNotEmpty()) { "Require at least one s3 bucket" }
        return s3Properties.buckets.map {
            val accessKey = it.accessKey
            val secretKey = it.secretKey
            val client = S3AsyncClient.builder().credentialsProvider {
                AwsBasicCredentials.create(accessKey, secretKey)
            }.asyncConfiguration {
                // it.advancedOption() todo may be use coroutine dispatcher
            }.region(Region.of(it.region))
                .endpointOverride(URI.create(it.host)).build()
            ReplicableS3Client(client, it.bucket)
        }
    }

    @Bean
    fun s3ExecutorService(s3Properties: S3Properties): ExecutorService {
        val threadNo = AtomicInteger()
        val name = "s3-client"
        val executor = Executors.newScheduledThreadPool(s3Properties.threads) { runnable ->
            val t = Thread(runnable, name + "-" + threadNo.incrementAndGet())
            t.isDaemon = true
            t
        }
        return executor
    }
}
