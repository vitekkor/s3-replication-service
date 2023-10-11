package com.vitekkor.s3replicationservice.util

import io.jsonwebtoken.io.AbstractDeserializer
import io.jsonwebtoken.io.AbstractSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import java.io.Reader

object AnySerializer : KSerializer<Any?> {
    private fun Any?.toJsonPrimitive(): JsonPrimitive {
        return when (this) {
            null -> JsonNull
            is JsonPrimitive -> this
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            else -> error("Couldn't find serializer for ${this::class}")
        }
    }

    private fun JsonPrimitive.toAnyValue(): Any? {
        val content = this.content
        if (this.isString) {
            // add custom string convert
            return content
        }
        if (content.equals("null", ignoreCase = true)) {
            return null
        }
        if (content.equals("true", ignoreCase = true)) {
            return true
        }
        if (content.equals("false", ignoreCase = true)) {
            return false
        }
        val intValue = content.toIntOrNull()
        if (intValue != null) {
            return intValue
        }
        val longValue = content.toLongOrNull()
        if (longValue != null) {
            return longValue
        }
        val doubleValue = content.toDoubleOrNull()
        if (doubleValue != null) {
            return doubleValue
        }
        error("Couldn't find serializer for ${this::class}")
    }

    private val delegateSerializer = JsonPrimitive.serializer()
    override val descriptor = delegateSerializer.descriptor
    override fun serialize(encoder: Encoder, value: Any?) {
        encoder.encodeSerializableValue(delegateSerializer, value.toJsonPrimitive())
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonPrimitive = decoder.decodeSerializableValue(delegateSerializer)
        return jsonPrimitive.toAnyValue()
    }

}

object JWTKotlinxSerializer : AbstractSerializer<Map<String, *>>() {
    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    override fun doSerialize(t: Map<String, *>, out: OutputStream) {
        Json.encodeToStream(t as Map<String, @Serializable(with = AnySerializer::class) Any>, out)
    }


}

object JWTKotlinxDeserializer : AbstractDeserializer<Map<String, *>>() {
    override fun doDeserialize(reader: Reader): Map<String, *> {
        return Json.decodeFromString<Map<String, @Serializable(with = AnySerializer::class) Any>>(
            reader.readText()
        )
    }
}
