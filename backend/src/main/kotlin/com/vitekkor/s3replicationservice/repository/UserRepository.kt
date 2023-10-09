package com.vitekkor.s3replicationservice.repository

import com.vitekkor.s3replicationservice.model.db.User
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository

interface UserRepository : ReactiveCassandraRepository<User, String> {

}
