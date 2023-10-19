package com.vitekkor.s3replicationservice.security

import com.vitekkor.s3replicationservice.configuration.properties.JwtProperties
import com.vitekkor.s3replicationservice.model.auth.AuthenticationResponse
import com.vitekkor.s3replicationservice.repository.UserRepository
import com.vitekkor.s3replicationservice.util.JWTKotlinxDeserializer
import com.vitekkor.s3replicationservice.util.JWTKotlinxSerializer
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Jwts.SIG
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
    private val userRepository: UserRepository,
    private val reactiveUserDetailsService: ReactiveUserDetailsService,
) {


    private val log = KotlinLogging.logger {}
    private val secretKey: SecretKey

    init {
        val secret = Base64.getEncoder().encode(jwtProperties.secretKey.toByteArray(StandardCharsets.UTF_8))
        secretKey = Keys.hmacShaKeyFor(secret)
    }

    fun createToken(authentication: Authentication): String {
        val username: String = authentication.name
        val authorities: Collection<GrantedAuthority> = authentication.authorities
        val claims = Jwts.claims().subject(username)
        if (authorities.isNotEmpty()) {
            claims.add(
                AUTHORITIES_ROLES_KEY,
                authorities.filter { it.authority.startsWith("ROLE") }
                    .joinToString(separator = ",", transform = GrantedAuthority::getAuthority)
            )
            claims.add(
                AUTHORITIES_SCOPES_KEY,
                authorities.filter { it.authority.startsWith("SCOPE") }
                    .joinToString(separator = ",", transform = GrantedAuthority::getAuthority)
            )
            claims.add(
                AUTHORITIES_FILE_EXT_KEY,
                authorities.filter { it.authority.startsWith("FILE_EXT") }
                    .joinToString(separator = ",", transform = GrantedAuthority::getAuthority)
            )
            claims.add(
                AUTHORITIES_IP_KEY,
                authorities.filter { it.authority.startsWith("IP") }
                    .joinToString(separator = ",", transform = GrantedAuthority::getAuthority)
            )
        }
        val now = Date()
        val validity = Date(now.time + jwtProperties.validityInMs)
        return Jwts.builder()
            .json(JWTKotlinxSerializer)
            .claims(claims.build()).issuedAt(now)
            .expiration(validity)
            .signWith(secretKey, SIG.HS256)
            .compact()
    }

    fun createRefreshToken(authentication: Authentication): String {
        val username: String = authentication.name
        val claims = Jwts.claims().subject(username)
        val now = Date()
        val validity = Date(now.time + jwtProperties.refreshValidityInMs)
        return Jwts.builder()
            .json(JWTKotlinxSerializer)
            .claims(claims.build()).issuedAt(now)
            .expiration(validity)
            .signWith(secretKey, SIG.HS256)
            .compact()
    }

    fun refresh(refreshToken: String): Mono<AuthenticationResponse> {
        if (validateToken(refreshToken)) {
            val authentication = getAuthentication(refreshToken)
            val login = (authentication.principal as User).username
            return userRepository.findById(login).flatMap {
                if (!it.isActive) Mono.empty() else it.toMono()
            }.switchIfEmpty(Mono.defer { Mono.error(Exception()) }).flatMap {
                reactiveUserDetailsService.findByUsername(it.login)
            }.flatMap {
                val realAuthentication =
                    UsernamePasswordAuthenticationToken(
                        authentication.principal,
                        authentication.credentials,
                        it.authorities
                    )
                val accessToken = createToken(realAuthentication)
                val newRefreshToken = createRefreshToken(realAuthentication)
                AuthenticationResponse(accessToken = accessToken, refreshToken = newRefreshToken).toMono()
            }
        }
        return Mono.error(Exception())
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser().json(JWTKotlinxDeserializer).verifyWith(secretKey).build().parseSignedClaims(token).payload
    }

    fun getAuthentication(token: String): Authentication {
        val claims: Claims = getClaims(token)
        val roles = claims[AUTHORITIES_ROLES_KEY]
        val authorities: MutableList<GrantedAuthority> =
            if (roles == null) AuthorityUtils.NO_AUTHORITIES else AuthorityUtils
                .commaSeparatedStringToAuthorityList(roles.toString())
        claims[AUTHORITIES_SCOPES_KEY]?.let {
            authorities.addAll(
                AuthorityUtils
                    .commaSeparatedStringToAuthorityList(it.toString())
            )
        }
        claims[AUTHORITIES_IP_KEY]?.let {
            authorities.addAll(
                AuthorityUtils
                    .commaSeparatedStringToAuthorityList(it.toString())
            )
        }
        claims[AUTHORITIES_FILE_EXT_KEY]?.let {
            authorities.addAll(
                AuthorityUtils
                    .commaSeparatedStringToAuthorityList(it.toString())
            )
        }
        val user = userRepository.findById(claims.subject).block()
        val userIsActive = user?.isActive ?: false
        val credentialsNonExpired = userIsActive
        val principal = User(claims.subject, "", userIsActive, credentialsNonExpired, userIsActive, userIsActive, authorities)
        val authentication = UsernamePasswordAuthenticationToken(principal, token, authorities)
        if (!userIsActive) {
            authentication.isAuthenticated = false
        }
        return authentication
    }

    fun validateToken(token: String): Boolean {
        try {
            Jwts.parser().verifyWith(secretKey)
                .json(JWTKotlinxDeserializer)
                .build().parseSignedClaims(token)
            return true
        } catch (e: JwtException) {
            log.trace("Invalid JWT token", e)
        } catch (e: IllegalArgumentException) {
            log.trace("Invalid JWT token", e)
        }
        return false
    }

    companion object {
        private const val AUTHORITIES_ROLES_KEY = "roles"
        private const val AUTHORITIES_SCOPES_KEY = "scopes"
        private const val AUTHORITIES_IP_KEY = "ips"
        private const val AUTHORITIES_FILE_EXT_KEY = "files"
    }
}
