package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.model.db.ReplicationSettings
import com.vitekkor.s3replicationservice.repository.ReplicationSettingsRepository
import com.vitekkor.s3replicationservice.util.orDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/replication")
class ReplicationController(private val replicationSettingsRepository: ReplicationSettingsRepository) {
    @GetMapping("/settings")
    fun getSettings(): Mono<ReplicationSettings> {
        return replicationSettingsRepository.findAll().singleOrEmpty().orDefault(replicationSettingsRepository)
    }

    @GetMapping("/disable")
    fun disable(): Mono<ReplicationSettings> {
        return replicationSettingsRepository.findAll().singleOrEmpty()
            .orDefault(replicationSettingsRepository)
            .flatMap {
                replicationSettingsRepository.save(
                    it.copy(
                        enabled = false,
                        status = ReplicationSettings.ReplicationStatus.DISABLED
                    )
                )
            }
    }

    @GetMapping("/enable")
    fun enable(): Mono<ReplicationSettings> {
        return replicationSettingsRepository.findAll().singleOrEmpty()
            .orDefault(replicationSettingsRepository)
            .flatMap {
                replicationSettingsRepository.save(
                    it.copy(
                        enabled = true,
                        status = ReplicationSettings.ReplicationStatus.ACTIVE
                    )
                )
            }
    }
}
