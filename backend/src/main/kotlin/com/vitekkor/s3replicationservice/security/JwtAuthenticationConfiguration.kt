package com.vitekkor.s3replicationservice.security

import com.vitekkor.s3replicationservice.repository.UserRepository
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authorization.AuthorizationContext
import reactor.core.publisher.Mono


@Configuration
@EnableWebFluxSecurity
class JwtAuthenticationConfiguration(
    private val jwtTokenAuthenticationFilter: JwtTokenAuthenticationFilter,
) {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    private fun currentUserMatchesClaims(
        authentication: Mono<Authentication>,
        context: AuthorizationContext,
    ): Mono<AuthorizationDecision> {
        // TODO ip check, permissions check
        return authentication
            .map { a -> context.variables["user"] == a.name }
            .map { granted: Boolean -> AuthorizationDecision(granted) }
    }

    @Bean
    fun userDetailsService(users: UserRepository): ReactiveUserDetailsService? {
        return ReactiveUserDetailsService { username: String? ->
            username?.let {
                users.findById(it)
                    .map { user ->
                        User.withUsername(user.login).password(user.password)
                            .authorities(*user.roles.toTypedArray())
                            .accountExpired(!user.isActive)
                            .credentialsExpired(!user.isActive)
                            .disabled(!user.isActive)
                            .accountLocked(!user.isActive)
                            .build()
                    }
            } ?: Mono.empty()
        }
    }

    @Bean
    fun reactiveAuthenticationManager(
        userDetailsService: ReactiveUserDetailsService,
        passwordEncoder: PasswordEncoder,
    ): ReactiveAuthenticationManager? {
        val authenticationManager = UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService)
        authenticationManager.setPasswordEncoder(passwordEncoder)
        return authenticationManager
    }

    @Bean("secureFilter")
    fun secureFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        // TODO add paths
        http.csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers("/version", "/healthCheck", "/actuator/prometheus", "/auth/**").permitAll()
                    .pathMatchers(HttpMethod.GET).hasAuthority("SCOPE_read")
                    .pathMatchers(HttpMethod.DELETE).hasAuthority("SCOPE_write")
                    .pathMatchers(HttpMethod.POST).hasAuthority("SCOPE_write")
                    .pathMatchers(HttpMethod.PUT).hasAuthority("SCOPE_write")
                    .anyExchange().access(::currentUserMatchesClaims)
            }.addFilterAt(jwtTokenAuthenticationFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        return http.build()
    }
}
