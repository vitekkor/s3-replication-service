package com.vitekkor.s3replicationservice.controller.system

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
class SystemController {

    @Value("classpath:version.json")
    private lateinit var versionResource: Resource

    @GetMapping(value = ["/healthCheck"])
    fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity("Ok", HttpStatus.OK)
    }

    @GetMapping(value = ["/version"], produces = ["application/json"])
    fun version(): String {
        return StreamUtils.copyToString(versionResource.inputStream, StandardCharsets.UTF_8)
    }
}
