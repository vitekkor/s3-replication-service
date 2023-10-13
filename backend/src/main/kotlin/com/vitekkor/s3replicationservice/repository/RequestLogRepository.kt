package com.vitekkor.s3replicationservice.repository

import com.vitekkor.s3replicationservice.model.db.RequestLog
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import java.util.UUID

interface RequestLogRepository : ReactiveCassandraRepository<RequestLog, UUID> {
    fun findAllByFileName(fileName: String): Flux<RequestLog>
}
