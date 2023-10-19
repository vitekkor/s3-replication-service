package com.vitekkor.s3replicationservice.service

import com.vitekkor.s3replicationservice.model.OperationResult
import com.vitekkor.s3replicationservice.model.Result
import com.vitekkor.s3replicationservice.model.UploadStatus
import com.vitekkor.s3replicationservice.util.FileUtils
import mu.KotlinLogging.logger
import org.springframework.http.MediaType
import reactor.core.publisher.BufferOverflowStrategy
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import java.nio.ByteBuffer


class ReplicableS3Client(
    private val s3AsyncClient: S3AsyncClient,
    private val bucket: String,
    val name: String,
) {
    private val logger = logger {}
    fun getObjects(): Flux<S3Object> {
        return Flux.from(
            s3AsyncClient.listObjectsV2Paginator(
                ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .build()
            )
        ).flatMap { response -> Flux.fromIterable(response.contents()) }
    }

    fun deleteObject(objectKey: String): Mono<OperationResult> {
        return Mono.just(
            DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build()
        ).map(s3AsyncClient::deleteObject)
            .flatMap { Mono.fromFuture(it) }.flatMap { response ->
                FileUtils.checkSdkResponse(response)
                logger.info("upload result: {}", response.toString())
                Mono.just(OperationResult(objectKey, Result.SUCCESSFUL))
            }
    }


    fun getByteBufferFluxObject(key: String): Flux<ByteBuffer> {
        return Mono.just(GetObjectRequest.builder().bucket(bucket).key(key).build()).map {
            s3AsyncClient.getObject(it, AsyncResponseTransformer.toPublisher())
        }.flatMap { Mono.fromFuture(it) }.flatMapMany { Flux.from(it) }
    }

    fun uploadObject(
        file: Flux<ByteBuffer>,
        fileName: String,
        contentType: String,
        length: Long,
    ): Mono<OperationResult> {
        val metadata: Map<String, String> = HashMap()
        return Mono.fromFuture(
            s3AsyncClient.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .contentLength(length)
                    .key(fileName)
                    .contentType(contentType)
                    .metadata(metadata)
                    .build(),
                AsyncRequestBody.fromPublisher(file)
            )
        ).map { response ->
            FileUtils.checkSdkResponse(response)
            logger.info("upload result: {}", response.toString())
            OperationResult(fileName, Result.SUCCESSFUL)
        }.onErrorResume {
            logger.warn { "Uploading file $fileName to $name was failed" }
            OperationResult(fileName, Result.FAILED).toMono()
        }
    }

    fun uploadObjectFlux(file: Flux<ByteBuffer>, fileName: String, contentType: String): Mono<Unit> {
        val metadata: Map<String, String> = mapOf("filename" to fileName)
        val mediaType: MediaType = MediaType.valueOf(contentType)
        val s3AsyncClientMultipartUpload = s3AsyncClient.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .contentType(mediaType.toString())
                .key(fileName)
                .metadata(metadata)
                .bucket(bucket)
                .build()
        )
        val uploadStatus = UploadStatus(contentType, fileName)
        return Mono.fromFuture(s3AsyncClientMultipartUpload).flatMapMany { response ->
            FileUtils.checkSdkResponse(response)
            uploadStatus.uploadId = response.uploadId()
            logger.info("Upload object with ID={}", response.uploadId())
            file
        }.bufferUntil { byteBuffer ->
            // Collect incoming values into multiple List buffers that will be emitted by the resulting Flux each time the given predicate returns true.
            uploadStatus.addBuffered(byteBuffer.remaining())
            if (uploadStatus.buffered >= 5242880) { // 5mb TODO config
                logger.info(
                    "BufferUntil - returning true, bufferedBytes={}, partCounter={}, uploadId={}",
                    uploadStatus.buffered, uploadStatus.partCounter, uploadStatus.uploadId
                )

                // reset buffer
                uploadStatus.buffered = 0
                return@bufferUntil true
            }
            false
        }.map { FileUtils.byteBufferListToByteBuffer(it) } // upload part
            .flatMap { byteBuffer -> uploadPartObject(uploadStatus, byteBuffer) }
            .onBackpressureBuffer(5242880, BufferOverflowStrategy.DROP_OLDEST)
            .reduce(uploadStatus) { status, completedPart ->
                logger.info("Completed: PartNumber={}, etag={}", completedPart.partNumber(), completedPart.eTag())
                status.completedParts[completedPart.partNumber()] = completedPart
                status
            }.flatMap { uploadStatus1 -> completeMultipartUpload(uploadStatus) }.map { response ->
                FileUtils.checkSdkResponse(response)
                logger.info("upload result: {}", response.toString())
            }
    }

    /**
     * Uploads a part in a multipart upload.
     */
    private fun uploadPartObject(uploadStatus: UploadStatus, buffer: ByteBuffer): Mono<CompletedPart> {
        val partNumber: Int = uploadStatus.addedPartCounter
        logger.info("UploadPart - partNumber={}, contentLength={}", partNumber, buffer.capacity())
        val uploadPartResponseCompletableFuture = s3AsyncClient.uploadPart(
            UploadPartRequest.builder()
                .bucket(bucket)
                .key(uploadStatus.fileKey)
                .partNumber(partNumber)
                .uploadId(uploadStatus.uploadId)
                .contentLength(buffer.capacity().toLong())
                .build(),
            AsyncRequestBody.fromByteBuffer(buffer)
        )
        return Mono
            .fromFuture(uploadPartResponseCompletableFuture)
            .map { uploadPartResult ->
                FileUtils.checkSdkResponse(uploadPartResult)
                logger.info("UploadPart - complete: part={}, etag={}", partNumber, uploadPartResult.eTag())
                CompletedPart.builder()
                    .eTag(uploadPartResult.eTag())
                    .partNumber(partNumber)
                    .build()
            }
    }

    /**
     * This method is called when a part finishes uploading. It's primary function is to verify the ETag of the part
     * we just uploaded.
     */
    private fun completeMultipartUpload(uploadStatus: UploadStatus): Mono<CompleteMultipartUploadResponse> {
        logger.info(
            "CompleteUpload - fileKey={}, completedParts.size={}",
            uploadStatus.fileKey, uploadStatus.completedParts.size
        )
        val multipartUpload: CompletedMultipartUpload = CompletedMultipartUpload.builder()
            .parts(uploadStatus.completedParts.values)
            .build()
        return Mono.fromFuture(
            s3AsyncClient.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .uploadId(uploadStatus.uploadId)
                    .multipartUpload(multipartUpload)
                    .key(uploadStatus.fileKey)
                    .build()
            )
        )
    }

    override fun toString(): String = name
}
