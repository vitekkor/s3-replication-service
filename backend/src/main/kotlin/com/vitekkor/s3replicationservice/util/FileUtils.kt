package com.vitekkor.s3replicationservice.util

import mu.KotlinLogging
import software.amazon.awssdk.core.SdkResponse
import java.nio.ByteBuffer
import kotlin.jvm.optionals.getOrNull


object FileUtils {
    private val LOGGER = KotlinLogging.logger {}

    fun byteBufferListToByteBuffer(buffers: List<ByteBuffer>): ByteBuffer {
        LOGGER.info("Creating ByteBuffer from {} chunks", buffers.size)
        var partSize = 0
        for (b in buffers) {
            partSize += b.remaining()
        }
        val partData: ByteBuffer = ByteBuffer.allocate(partSize)
        buffers.forEach { buffer -> partData.put(buffer) }

        // Reset read pointer to first byte
        partData.rewind()
        LOGGER.info("PartData: capacity={}", partData.capacity())
        return partData
    }

    fun checkSdkResponse(sdkResponse: SdkResponse) {
        if (sdkResponse.sdkHttpResponse() == null || !sdkResponse.sdkHttpResponse().isSuccessful) {
            val statusCode = sdkResponse.sdkHttpResponse().statusText().getOrNull()
            val statusText = sdkResponse.sdkHttpResponse().statusText().getOrNull()
            throw RuntimeException("$statusCode - $statusText")
        }
    }
}
