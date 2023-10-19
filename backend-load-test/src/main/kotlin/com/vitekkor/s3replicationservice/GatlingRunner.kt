package com.vitekkor.s3replicationservice

import com.vitekkor.s3replicationservice.config.addConfigSource
import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

object GatlingRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = args.indexOf("--config")
        if (config != -1) {
            require(config + 1 != args.size)
            addConfigSource(args[config + 1])
        }
        val props = GatlingPropertiesBuilder()
            .simulationClass(NluProxySimulation::class.qualifiedName)
            .build()
        Gatling.fromMap(props)
    }
}
