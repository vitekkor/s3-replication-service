package com.vitekkor.s3replicationservice.service

import com.vitekkor.s3replicationservice.model.UploadStatus
import com.vitekkor.s3replicationservice.util.FileUtils
import mu.KotlinLogging.logger
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import software.amazon.awssdk.core.BytesWrapper
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
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import java.nio.ByteBuffer


class ReplicableS3Client(
    private val s3AsyncClient: S3AsyncClient,
    private val bucket: String,
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

    fun deleteObject(objectKey: String): Mono<Void> {
        return Mono.just(
            DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build()
        ).map(s3AsyncClient::deleteObject)
            .flatMap { Mono.fromFuture(it) }
            .then()
    }


    fun getByteObject(key: String): Mono<ByteArray> {
        return Mono.just(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .map { it -> s3AsyncClient.getObject(it, AsyncResponseTransformer.toBytes()) }
            .flatMap { Mono.fromFuture(it) }
            .map(BytesWrapper::asByteArray)
    }


    fun uploadObject(filePart: FilePart): Mono<Unit> {
        val filename: String = filePart.filename().removePrefix("/")
        val metadata: Map<String, String> = mapOf("filename" to filename)
        // get media type
        val mediaType: MediaType = filePart.headers().contentType ?: MediaType.APPLICATION_OCTET_STREAM
        val s3AsyncClientMultipartUpload = s3AsyncClient.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .contentType(mediaType.toString())
                .key(filename)
                .metadata(metadata)
                .bucket(bucket)
                .build()
        )
        val uploadStatus = UploadStatus(requireNotNull(filePart.headers().contentType).toString(), filename)
        return Mono.fromFuture(s3AsyncClientMultipartUpload).flatMapMany { response ->
            FileUtils.checkSdkResponse(response)
            uploadStatus.uploadId = response.uploadId()
            logger.info("Upload object with ID={}", response.uploadId())
            filePart.content()
        }.bufferUntil { dataBuffer ->
            // Collect incoming values into multiple List buffers that will be emitted by the resulting Flux each time the given predicate returns true.
            uploadStatus.addBuffered(dataBuffer.readableByteCount())
            if (uploadStatus.buffered >= 5242880) { // 5mb
                logger.info(
                    "BufferUntil - returning true, bufferedBytes={}, partCounter={}, uploadId={}",
                    uploadStatus.buffered, uploadStatus.partCounter, uploadStatus.uploadId
                )

                // reset buffer
                uploadStatus.buffered = 0
                return@bufferUntil true
            }
            false
        }.map { FileUtils.dataBufferToByteBuffer(it) } // upload part
            .flatMap { byteBuffer -> uploadPartObject(uploadStatus, byteBuffer) }
            .onBackpressureBuffer()
            .reduce(uploadStatus) { status, completedPart ->
                logger.info("Completed: PartNumber={}, etag={}", completedPart.partNumber(), completedPart.eTag())
                status.completedParts[completedPart.partNumber()] = completedPart
                status
            }.flatMap { uploadStatus1 -> completeMultipartUpload(uploadStatus) }
            .map { response ->
                FileUtils.checkSdkResponse(response)
                logger.info("upload result: {}", response.toString())
            }.doOnError {
                println(it)
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
            AsyncRequestBody.fromPublisher(Mono.just(buffer))
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
}
