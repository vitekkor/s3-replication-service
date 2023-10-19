package com.vitekkor.s3replicationservice.config

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.yaml.YamlParser
import java.io.File
import java.time.Duration

private val yamlParser = YamlParser()
private val ConfigurationLoader = ConfigLoader.builder().apply {
    yamlParser.defaultFileExtensions().forEach {
        addParser(it, yamlParser)
    }
}

data class Config(
    val baseUrl: String,
    val assetsPath: String,
    val usersPerSecond: Double,
    val duration: Duration = Duration.ofSeconds(10),
    val rps: Rps,
    val complexModelEnabled: Boolean = false,
)

data class Rps(
    val reach: Int = 50,
    val rump: Duration = Duration.ofSeconds(10),
    val duration: Duration = Duration.ofSeconds(10),
)

val Configuration by lazy {
    ConfigurationLoader
        .addSource(PropertySource.resource("/application-local.yml", optional = true))
        .addSource(PropertySource.resource("/application.yml", optional = false))
        .build()
        .loadConfigOrThrow<Config>()
}

fun addConfigSource(path: String) = ConfigurationLoader.addSource(PropertySource.file(File(path)))
