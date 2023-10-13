package com.vitekkor.s3replicationservice.repository

import com.vitekkor.s3replicationservice.model.db.RequestError
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import java.util.UUID


interface RequestErrorRepository : ReactiveCassandraRepository<RequestError, UUID>
