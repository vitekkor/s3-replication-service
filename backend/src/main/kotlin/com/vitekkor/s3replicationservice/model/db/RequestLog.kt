package com.vitekkor.s3replicationservice.model.db

import com.vitekkor.s3replicationservice.util.DateSerializer
import com.vitekkor.s3replicationservice.util.UUIDSerializer
import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.Table
import java.util.Date
import java.util.UUID

@Serializable
@Table("request_log")
data class RequestLog(
    @Id
    @CassandraType(type = CassandraType.Name.UUID)
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @CassandraType(type = CassandraType.Name.TEXT)
    val method: RequestMethod,
    val fileName: String,
    val fileProperties: Map<String, String> = emptyMap(),
    val s3StorageName: String,
    @CassandraType(type = CassandraType.Name.TIMESTAMP)
    @Serializable(with = DateSerializer::class)
    val timestamp: Date,
)
