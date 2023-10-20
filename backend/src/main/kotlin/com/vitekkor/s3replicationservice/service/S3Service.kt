package com.vitekkor.s3replicationservice.service

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.model.db.ReplicationSettings
import com.vitekkor.s3replicationservice.model.db.RequestError
import com.vitekkor.s3replicationservice.model.db.RequestLog
import com.vitekkor.s3replicationservice.model.db.RequestMethod
import com.vitekkor.s3replicationservice.repository.ReplicationSettingsRepository
import com.vitekkor.s3replicationservice.repository.RequestErrorRepository
import com.vitekkor.s3replicationservice.repository.RequestLogRepository
import com.vitekkor.s3replicationservice.util.orDefault
import mu.KotlinLogging.logger
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.extra.math.max
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date

@Service
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "1m")
class S3Service(
    private val s3Clients: List<ReplicableS3Client>,
    private val requestErrorRepository: RequestErrorRepository,
    private val requestLogRepository: RequestLogRepository,
    private val replicationSettingsRepository: ReplicationSettingsRepository,
) {
    private val logger = logger {}

    fun newUpsert(
        body: Flux<ByteBuffer>,
        fileName: String,
        contentType: String,
        length: Long,
        timestamp: Date,
    ): Flux<OperationResult> {
        return replicationSettingsRepository.findAll().singleOrEmpty().orDefault(replicationSettingsRepository)
            .flatMapMany {
                if (it.enabled) {
                    s3Clients
                } else {
                    listOf(s3Clients.first())
                }.toFlux()
            }.flatMap {
                it.uploadObject(body, fileName, contentType, length).flatMap { result ->
                    if (result.isSuccessful) {
                        val requestLog = RequestLog(
                            method = RequestMethod.PUT, fileName = fileName, fileProperties = mapOf(
                                CONTENT_TYPE_PROPERTY to contentType, CONTENT_LENGTH_PROPERTY to length.toString()
                            ), s3StorageName = it.name, timestamp = timestamp
                        )
                        requestLogRepository.save(requestLog).thenReturn(result)
                    } else {
                        val requestError = RequestError(
                            method = RequestMethod.PUT, fileName = fileName, fileProperties = mapOf(
                                CONTENT_TYPE_PROPERTY to contentType, CONTENT_LENGTH_PROPERTY to length.toString()
                            ), s3StorageName = it.name, timestamp = timestamp
                        )
                        requestErrorRepository.save(requestError).thenReturn(result)
                    }
                }
            }
    }

    fun delete(fileName: String, timestamp: Date): Flux<OperationResult> {
        return replicationSettingsRepository.findAll().singleOrEmpty().orDefault(replicationSettingsRepository)
            .flatMapMany {
                if (it.enabled) {
                    s3Clients
                } else {
                    listOf(s3Clients.first())
                }.toFlux()
            }.flatMap { s3Client ->
                s3Client.deleteObject(fileName).flatMap {
                    val requestLog = RequestLog(
                        method = RequestMethod.DELETE,
                        fileName = fileName,
                        s3StorageName = s3Client.name,
                        timestamp = timestamp
                    )
                    requestLogRepository.save(requestLog).thenReturn(it)
                }.onErrorResume {
                    logger.warn { "Deleting file $fileName to $s3Client was failed" }
                    val requestError = RequestError(
                        method = RequestMethod.DELETE,
                        fileName = fileName,
                        s3StorageName = s3Client.name,
                        timestamp = timestamp
                    )
                    requestErrorRepository.save(requestError).map { OperationResult(fileName, Result.FAILED) }
                }
            }
    }

    fun get(fileName: String): Flux<ByteBuffer> {
        return findActualLog(fileName)?.let { actualLog ->
            val client = s3Clients.find { it.name == actualLog.s3StorageName }
            client?.getByteBufferFluxObject(fileName, actualLog.fileProperties[CONTENT_TYPE_PROPERTY])
        } ?: throw NoSuchKeyException.builder().message("File $fileName not found").build()
    }

    fun getFileStatuses(): List<RequestLog> {
        return checkNotNull(requestLogRepository.findAll().collectList().block()).sortedByDescending { it.timestamp }
            .distinctBy { it.fileName + it.method + it.s3StorageName }.groupBy { it.fileName + it.s3StorageName }
            .mapNotNull { (_, value) ->
                value.maxByOrNull { it.timestamp }
            }
    }

    @Scheduled(fixedDelayString = "\${s3.replicationJobDelay}")
    @SchedulerLock(name = "backgroundReplication", lockAtMostFor = "1m")
    fun backgroundReplication() {
        logger.info { "Start background replication" }
        LockAssert.assertLocked()
        val settings = replicationSettingsRepository.findAll().singleOrEmpty().block()
        if (settings?.enabled == false) {
            logger.info { "Replication is disabled" }
            return
        }
        val timestamp = Date.from(Instant.now())
        val errors = checkNotNull(
            requestErrorRepository.findAll().collectList().block()
        ).distinctBy { it.fileName + it.method + it.s3StorageName }.sortedBy { it.timestamp }
        if (errors.isNotEmpty()) {
            replicationSettingsRepository.findAll().singleOrEmpty().orDefault(replicationSettingsRepository).map {
                it.copy(status = ReplicationSettings.ReplicationStatus.BACKGROUND_WORK).let { copy ->
                    replicationSettingsRepository.save(copy)
                }
            }.block()
        }
        for (error in errors) {
            val actualLog = findActualLog(error.fileName)
            if (actualLog != null) {
                if (actualLog.s3StorageName == error.s3StorageName) {
                    requestErrorRepository.delete(error).subscribe()
                    continue
                }

                val producer = s3Clients.find { it.name == actualLog.s3StorageName }
                if (producer == null) {
                    logger.warn { "Couldn't find producer for file ${error.fileName}" }
                    continue
                }
                val consumer = s3Clients.find { it.name == error.s3StorageName }
                if (consumer == null) {
                    logger.warn { "Couldn't find consumer for file ${error.fileName}" }
                    continue
                }

                if (actualLog.method == RequestMethod.PUT) {
                    val requestLog = RequestLog(
                        method = RequestMethod.PUT,
                        fileName = error.fileName,
                        fileProperties = error.fileProperties,
                        s3StorageName = consumer.name,
                        timestamp = timestamp
                    )
                    uploadErrorFile(
                        producer,
                        consumer,
                        error.fileName,
                        checkNotNull(error.fileProperties[CONTENT_TYPE_PROPERTY]),
                        checkNotNull(error.fileProperties[CONTENT_LENGTH_PROPERTY]).toLong()
                    ).then(requestLogRepository.save(requestLog)).then(requestErrorRepository.delete(error)).subscribe {
                            logger.info { "Successfully transfer file ${error.fileName} from ${producer.name} to ${consumer.name}" }
                        }
                } else {
                    val requestLog = RequestLog(
                        method = RequestMethod.DELETE,
                        fileName = error.fileName,
                        fileProperties = error.fileProperties,
                        s3StorageName = consumer.name,
                        timestamp = timestamp
                    )
                    deleteErrorFile(consumer, error.fileName).then(requestLogRepository.save(requestLog))
                        .then(requestErrorRepository.delete(error)).subscribe {
                            logger.info {
                                "Successfully transfer file ${error.fileName} from ${actualLog.s3StorageName} to ${error.s3StorageName}"
                            }
                        }
                }
                continue
            }

            requestErrorRepository.delete(error).subscribe()
        }
        if (errors.isNotEmpty()) {
            replicationSettingsRepository.findAll().singleOrEmpty().orDefault(replicationSettingsRepository).map {
                it.copy(status = ReplicationSettings.ReplicationStatus.ACTIVE).let { copy ->
                    replicationSettingsRepository.save(copy)
                }
            }.block()
        }
    }

    private fun uploadErrorFile(
        producer: ReplicableS3Client,
        consumer: ReplicableS3Client,
        fileName: String,
        contentType: String,
        contentLength: Long,
    ): Mono<OperationResult> {
        val actualFile = producer.getByteBufferFluxObject(fileName, contentType)
        return consumer.uploadObject(actualFile, fileName, contentType, contentLength).map {
            if (!it.isSuccessful) {
                logger.error {
                    "Transferring file $fileName from ${producer.name} to ${consumer.name} was failed"
                }
            }
            it
        }
    }

    private fun deleteErrorFile(
        consumer: ReplicableS3Client,
        fileName: String,
    ): Mono<OperationResult> {
        return consumer.deleteObject(fileName).doOnError {
            logger.error(it) { "Deleting file $fileName from ${consumer.name} was failed" }
        }
    }

    private fun findActualLog(fileName: String): RequestLog? {
        return requestLogRepository.findAllByFileName(fileName)
            .max { requestLog, requestLog2 -> requestLog.timestamp.compareTo(requestLog2.timestamp) }.block()
    }

    companion object {
        private const val CONTENT_TYPE_PROPERTY = "contentType"
        private const val CONTENT_LENGTH_PROPERTY = "contentLength"
    }
}
