package com.vitekkor.s3replicationservice.repository

import com.vitekkor.s3replicationservice.model.db.User
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface UserRepository : ReactiveCassandraRepository<User, String>
