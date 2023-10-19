package com.vitekkor.s3replicationservice.simulation

import com.justai.nluproxy.config.Configuration
import com.justai.nluproxy.factory.ComplexModelRequestFactory
import com.justai.nluproxy.factory.NerRequestFactory
import com.justai.nluproxy.factory.PredictRequestFactroy
import com.justai.nluproxy.factory.ProcessTextRequestFactory
import com.justai.nluproxy.model.AnalyzeRequest.AnalyzeRequestFactory
import com.justai.nluproxy.util.Jackson
import com.justai.nluproxy.util.Util
import com.vitekkor.s3replicationservice.config.Configuration
import io.gatling.javaapi.core.ChainBuilder
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.CoreDsl
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.holdFor
import io.gatling.javaapi.core.CoreDsl.reachRps
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.ScenarioBuilder
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import io.gatling.javaapi.http.HttpProtocolBuilder

class S3ReplicationSimulation : Simulation() {
    private val httpProtocol = createHttpProtocol()

    private val feeder = generateSequence {
        mapOf(
            "requestId" to Util.getRequestId(),
            "randomString" to Util.getRandomString()
        )
    }.iterator()

    private val scenario = createScenario()

    private fun createHttpProtocol(): HttpProtocolBuilder {
        val baseUrl = Configuration.baseUrl.removeSuffix("/")
        return http.baseUrl(baseUrl)
    }

    private fun createScenario(): ScenarioBuilder {
        val ner = post("ner", "/ner") { _, randomString -> NerRequestFactory.new(randomString) }
        val predict = post("predict", "/classifiers/caila/predictnbest/caila") { _, randomString ->
            PredictRequestFactroy.new(randomString)
        }

        return if (Configuration.complexModelEnabled) {
            val saveData = post("saveData", "/complexModel/saveData") { requestId, _ ->
                ComplexModelRequestFactory.new(requestId)
            }
            val processText = post("processText", "/process-text", checkerBadReq()) { _, randomString ->
                ProcessTextRequestFactory.new(randomString)
            }
            scenario("load-test-with-complex-model").feed(feeder).exec(saveData, ner, processText, predict)
        } else {
            val processText = post("processText", "/process-text") { _, randomString ->
                ProcessTextRequestFactory.new(randomString)
            }
            scenario("load-test").feed(feeder).exec(ner, processText, predict)
        }
    }

    private fun post(
        name: String,
        path: String,
        checker: CheckBuilder = checkerOk(),
        body: (requestId: String, randomString: String) -> Any
    ): ChainBuilder {
        lateinit var requestId: String
        lateinit var randomString: String
        return exec(http(name).post {
            requestId = it.get("requestId")
            randomString = it.get("randomString")
            path
        }.header("Z-requestid", "\${requestId}").body(
            CoreDsl.StringBody { Jackson.stringify(body(requestId, randomString)) }
        ).check(checker))
    }

    init {
        setUp(
            scenario.injectOpen(constantUsersPerSec(Configuration.usersPerSecond).during(Configuration.duration))
                .throttle(
                    reachRps(Configuration.rps.reach).during(Configuration.rps.rump),
                    holdFor(Configuration.rps.duration)
                )
        ).protocols(httpProtocol)
    }

    private fun checkerOk() = status().`is`(200)

    private fun checkerBadReq() = status().`is`(400)
}
