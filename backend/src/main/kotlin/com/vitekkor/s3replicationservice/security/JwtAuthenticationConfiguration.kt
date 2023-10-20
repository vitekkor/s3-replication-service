package com.vitekkor.s3replicationservice.security

import com.vitekkor.s3replicationservice.repository.UserRepository
import com.vitekkor.s3replicationservice.util.ExtensionMatcher
import com.vitekkor.s3replicationservice.util.IpAddressMatcher
import com.vitekkor.s3replicationservice.util.apiPathShouldBeFilteredByExt
import com.vitekkor.s3replicationservice.util.authorities
import com.vitekkor.s3replicationservice.util.isExtensionAuthority
import com.vitekkor.s3replicationservice.util.isIpAuthority
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
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authorization.AuthorizationContext
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.server.adapter.ForwardedHeaderTransformer
import reactor.core.publisher.Mono


@Configuration
@EnableWebFluxSecurity
class JwtAuthenticationConfiguration(
    private val jwtTokenAuthenticationFilter: JwtTokenAuthenticationFilter,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun forwardedHeaderTransformer() = ForwardedHeaderTransformer()

    private fun checkIp(
        authentication: Mono<Authentication>,
        context: AuthorizationContext,
    ): Mono<AuthorizationDecision> {
        val remoteAddress = context.exchange.request.remoteAddress
        return authentication
            .map { a ->
                if (remoteAddress == null) return@map false
                val ip = if (remoteAddress.isUnresolved) {
                    remoteAddress.hostString
                } else {
                    remoteAddress.address.hostAddress
                }
                val requestPath = context.exchange.request.path.toString()
                val matchesByExtension = if (requestPath.apiPathShouldBeFilteredByExt()) {
                    a.authorities.filter(GrantedAuthority::isExtensionAuthority).map {
                        ExtensionMatcher(it.authority.removePrefix("FILE_EXT_"))
                    }.any { it.matches(requestPath) }
                } else {
                    true
                }
                val user = (a.principal as User)
                val userIsActive = user.isAccountNonExpired && user.isAccountNonLocked && user.isCredentialsNonExpired
                userIsActive && a.authorities.filter(
                    GrantedAuthority::isIpAuthority
                ).map {
                    IpAddressMatcher(it.authority.removePrefix("IP_"))
                }.any { it.matches(ip) } && matchesByExtension
            }.map { granted: Boolean -> AuthorizationDecision(granted) }
    }

    @Bean
    fun userDetailsService(users: UserRepository): ReactiveUserDetailsService? {
        return ReactiveUserDetailsService { username: String? ->
            username?.let {
                users.findById(it)
                    .map { user ->
                        User.withUsername(user.login).password(user.password)
                            .authorities(*user.authorities)
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
        http.cors {
            it.configurationSource {
                CorsConfiguration().apply {
                    applyPermitDefaultValues()
                    allowedMethods = listOf(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.DELETE.name())
                }
            }
        }.csrf { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers("/version", "/healthCheck", "/actuator/prometheus", "/auth/**").permitAll()
                    .pathMatchers("/user/**").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.GET).hasAuthority("SCOPE_read")
                    .pathMatchers(HttpMethod.DELETE).hasAuthority("SCOPE_write")
                    .pathMatchers(HttpMethod.POST).hasAuthority("SCOPE_write")
                    .pathMatchers(HttpMethod.PUT).hasAuthority("SCOPE_write")
                    .pathMatchers("/**").access(::checkIp)
                    .anyExchange().authenticated()
            }.addFilterAt(jwtTokenAuthenticationFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        return http.build()
    }
}
