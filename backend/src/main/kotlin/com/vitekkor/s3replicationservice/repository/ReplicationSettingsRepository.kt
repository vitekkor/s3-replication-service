package com.vitekkor.s3replicationservice.repository

import com.vitekkor.s3replicationservice.model.db.ReplicationSettings
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import java.util.UUID

interface ReplicationSettingsRepository : ReactiveCassandraRepository<ReplicationSettings, UUID>
