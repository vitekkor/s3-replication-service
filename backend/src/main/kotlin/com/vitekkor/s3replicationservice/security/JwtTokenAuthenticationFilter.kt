package com.vitekkor.s3replicationservice.security

import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers


@Component
class JwtTokenAuthenticationFilter(@Lazy private val tokenProvider: JwtTokenProvider) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val token = resolveToken(exchange.request)
        return if (!token.isNullOrBlank() && tokenProvider.validateToken(token)) {
            Mono.fromCallable { tokenProvider.getAuthentication(token) }
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap { authentication ->
                    chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                }
        } else chain.filter(exchange)
    }

    private fun resolveToken(request: ServerHttpRequest): String? {
        val bearerToken: String? = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return if (!bearerToken.isNullOrBlank() && bearerToken.startsWith(HEADER_PREFIX)) {
            bearerToken.removePrefix(HEADER_PREFIX)
        } else null
    }

    companion object {
        const val HEADER_PREFIX = "Bearer "
    }
}
