package com.vitekkor.s3replicationservice.configuration

import com.datastax.oss.driver.api.core.CqlSession
import com.vitekkor.s3replicationservice.configuration.properties.S3Properties
import com.vitekkor.s3replicationservice.service.ReplicableS3Client
import kotlinx.serialization.json.Json
import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.Duration


@Configuration
@EnableWebFlux
class ApplicationConfiguration: WebFluxConfigurer {
    @Bean
    fun s3Clients(s3Properties: S3Properties): List<ReplicableS3Client> {
        require(s3Properties.buckets.isNotEmpty()) { "Require at least one s3 bucket" }
        return s3Properties.buckets.map { properties ->
            val accessKey = properties.accessKey
            val secretKey = properties.secretKey
            val client = S3AsyncClient.builder().credentialsProvider {
                AwsBasicCredentials.create(accessKey, secretKey)
            }.asyncConfiguration {
                // it.advancedOption() todo may be use coroutine dispatcher
            }.region(Region.of(properties.region))
                .httpClient(sdkAsyncHttpClient())
                .endpointOverride(URI.create(properties.host)).build()
            val syncClient = S3Client.builder().credentialsProvider {
                AwsBasicCredentials.create(accessKey, secretKey)
            }.region(Region.of(properties.region)).endpointOverride(URI.create(properties.host)).build()
            ReplicableS3Client(client, syncClient, properties.bucket, "replica_${properties.host}_${properties.bucket}")
        }
    }

    private fun sdkAsyncHttpClient(): SdkAsyncHttpClient? {
        return NettyNioAsyncHttpClient.builder()
            .writeTimeout(Duration.ZERO)
            .maxConcurrency(64)
            .connectionAcquisitionTimeout(Duration.ofMillis(20000))
            .build()
    }

    @Bean
    fun lockProvider(cqlSession: CqlSession): CassandraLockProvider {
        return CassandraLockProvider(
            CassandraLockProvider.Configuration.builder()
                .withCqlSession(cqlSession)
                .withTableName("replication_lock")
                .build()
        )
    }

    @Bean
    fun messageConverter(): KotlinSerializationJsonHttpMessageConverter {
        return KotlinSerializationJsonHttpMessageConverter(Json {
            ignoreUnknownKeys = true
        })
    }

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
    }
}
