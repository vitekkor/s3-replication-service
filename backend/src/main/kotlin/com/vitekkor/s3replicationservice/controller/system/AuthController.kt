package com.vitekkor.s3replicationservice.controller.system

import com.vitekkor.s3replicationservice.model.AuthenticationRequest
import com.vitekkor.s3replicationservice.security.JwtTokenProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono


@RestController
@RequestMapping("/auth")
class AuthController(
    private val tokenProvider: JwtTokenProvider,
    private val authenticationManager: ReactiveAuthenticationManager,
) {
    @PostMapping("/login")
    fun login(@RequestBody authRequest: Mono<AuthenticationRequest>): Mono<ResponseEntity<Map<String, String>>> {
        return authRequest
            .flatMap { authenticationRequest ->
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken(
                        authenticationRequest.login, authenticationRequest.password
                    )
                ).map { authentication: Authentication ->
                    tokenProvider.createToken(authentication)
                }
            }.map { jwt ->
                val httpHeaders = HttpHeaders()
                httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                val tokenBody = mapOf("access_token" to jwt)
                ResponseEntity.status(HttpStatus.OK).headers(httpHeaders).body(tokenBody)
            }
    }
}
