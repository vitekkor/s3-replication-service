package com.vitekkor.s3replicationservice.service

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.model.db.RequestError
import com.vitekkor.s3replicationservice.model.db.RequestLog
import com.vitekkor.s3replicationservice.model.db.RequestMethod
import com.vitekkor.s3replicationservice.repository.RequestErrorRepository
import com.vitekkor.s3replicationservice.repository.RequestLogRepository
import com.vitekkor.s3replicationservice.util.contentType
import mu.KotlinLogging.logger
import net.javacrumbs.shedlock.core.LockAssert
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.http.codec.multipart.FilePart
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
) {
    private val logger = logger {}
    fun upsert(filePart: FilePart, fileName: String, timestamp: Date): Flux<OperationResult> {
        return s3Clients.map { s3Client ->
            s3Client.uploadObject(filePart, fileName).flatMap {
                val requestLog = RequestLog(
                    method = RequestMethod.PUT,
                    fileName = fileName,
                    fileProperties = mapOf(CONTENT_TYPE_PROPERTY to filePart.contentType.toString()),
                    s3StorageName = s3Client.name,
                    timestamp = timestamp
                )
                requestLogRepository.save(requestLog).thenReturn(it)
            }.onErrorResume {
                logger.warn { "Uploading file $fileName to $s3Client was failed" }
                val requestError = RequestError(
                    method = RequestMethod.PUT,
                    fileName = fileName,
                    fileProperties = mapOf(CONTENT_TYPE_PROPERTY to filePart.contentType.toString()),
                    s3StorageName = s3Client.name,
                    timestamp = timestamp
                )
                requestErrorRepository.save(requestError).map { OperationResult(fileName, Result.FAILED) }
            }.map { OperationResult(fileName, Result.SUCCESSFUL) }
        }.toFlux().flatMap { it }
    }

    fun delete(fileName: String, timestamp: Date): Flux<OperationResult> {
        return s3Clients.map { s3Client ->
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
        }.toFlux().flatMap { it }
    }

    fun get(fileName: String): Flux<ByteBuffer> {
        return findActualLog(fileName)?.let { actualLog ->
            val client = s3Clients.find { it.name == actualLog.s3StorageName }
            client?.getByteBufferFluxObject(fileName)
        } ?: throw NoSuchKeyException.create("File $fileName not found", null)
    }

    @Scheduled(fixedDelayString = "\${s3.replicationJobDelay}")
    @SchedulerLock(name = "backgroundReplication", lockAtMostFor = "1m")
    fun backgroundReplication() {
        LockAssert.assertLocked()
        val timestamp = Date.from(Instant.now())
        val errors = checkNotNull(requestErrorRepository.findAll().collectList().block())
            .distinctBy { it.fileName + it.method + it.s3StorageName }
            .sortedBy { it.timestamp }
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
                        checkNotNull(error.fileProperties[CONTENT_TYPE_PROPERTY])
                    )
                        .then(requestLogRepository.save(requestLog))
                        .then(requestErrorRepository.delete(error))
                        .subscribe {
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
                    deleteErrorFile(consumer, error.fileName)
                        .then(requestLogRepository.save(requestLog))
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

    }

    private fun uploadErrorFile(
        producer: ReplicableS3Client,
        consumer: ReplicableS3Client,
        fileName: String,
        contentType: String,
    ): Mono<Unit> {
        val actualFile = producer.getByteBufferFluxObject(fileName)
        return consumer.uploadObjectFlux(actualFile, fileName, contentType).doOnError {
            logger.error(it) {
                "Transferring file $fileName from ${producer.name} to ${consumer.name} was failed"
            }
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
    }
}
