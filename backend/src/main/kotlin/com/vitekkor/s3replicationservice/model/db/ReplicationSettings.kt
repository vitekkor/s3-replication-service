package com.vitekkor.s3replicationservice.model.db

import com.vitekkor.s3replicationservice.util.UUIDSerializer
import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id
import org.springframework.data.cassandra.core.mapping.CassandraType
import org.springframework.data.cassandra.core.mapping.Table
import java.util.UUID

@Serializable
@Table("replication_settings")
data class ReplicationSettings(
    @Id
    @CassandraType(type = CassandraType.Name.UUID)
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val enabled: Boolean,
    val status: ReplicationStatus,
) {
    enum class ReplicationStatus {
        DISABLED, ACTIVE, BACKGROUND_WORK;
    }
}
