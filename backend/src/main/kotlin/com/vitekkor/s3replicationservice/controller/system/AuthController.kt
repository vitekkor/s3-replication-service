package com.vitekkor.s3replicationservice.controller.system

import com.vitekkor.s3replicationservice.model.auth.AuthenticationRequest
import com.vitekkor.s3replicationservice.model.auth.AuthenticationResponse
import com.vitekkor.s3replicationservice.model.auth.RefreshRequest
import com.vitekkor.s3replicationservice.model.auth.ValidateRequest
import com.vitekkor.s3replicationservice.security.JwtTokenProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono


@RestController
@RequestMapping("/auth")
class AuthController(
    private val tokenProvider: JwtTokenProvider,
    private val authenticationManager: ReactiveAuthenticationManager,
) {
    @PostMapping("/login")
    fun login(@RequestBody authRequest: Mono<AuthenticationRequest>): Mono<ResponseEntity<AuthenticationResponse>> {
        return authRequest.flatMap { authenticationRequest ->
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(
                    authenticationRequest.login, authenticationRequest.password
                )
            ).map { authentication: Authentication ->
                val accessToken = tokenProvider.createToken(authentication)
                val refreshToken = tokenProvider.createRefreshToken(authentication)
                AuthenticationResponse(accessToken = accessToken, refreshToken = refreshToken)
            }
        }.map { jwt ->
            val httpHeaders = HttpHeaders()
            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.accessToken}")
            ResponseEntity.status(HttpStatus.OK).headers(httpHeaders).body(jwt)
        }.onErrorResume(BadCredentialsException::class.java) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthenticationResponse(it.message)).toMono()
        }
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody refreshRequest: Mono<RefreshRequest>): Mono<ResponseEntity<AuthenticationResponse>> {
        return refreshRequest.flatMap { authenticationRequest ->
            tokenProvider.refresh(authenticationRequest.refreshToken)
        }.map { jwt ->
            val httpHeaders = HttpHeaders()
            httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.accessToken}")
            ResponseEntity.status(HttpStatus.OK).headers(httpHeaders).body(jwt)
        }.onErrorResume(BadCredentialsException::class.java) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthenticationResponse(it.message)).toMono()
        }
    }

    @PostMapping("/validate")
    fun validateToken(@RequestBody validateRequest: Mono<ValidateRequest>): Mono<Boolean> {
        return validateRequest.map {
            tokenProvider.validateToken(it.token)
        }
    }
}
