package com.vitekkor.s3replicationservice.configuration

import com.vitekkor.s3replicationservice.util.apiPathShouldBeFilteredByExt
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class FileSizeWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val routePath = exchange.request.path.toString()
        if (routePath.apiPathShouldBeFilteredByExt() && exchange.request.headers.contentLength >= 4294967296) { // 4gb TODO config
            exchange.response.statusCode = HttpStatus.PAYLOAD_TOO_LARGE
            return Mono.empty()
        }
        return chain.filter(exchange)
    }
}
