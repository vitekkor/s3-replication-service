package com.vitekkor.s3replicationservice.security

import com.vitekkor.s3replicationservice.configuration.properties.JwtProperties
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
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(private val jwtProperties: JwtProperties) {


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
                AUTHORITIES_KEY,
                authorities.joinToString(separator = ",", transform = GrantedAuthority::getAuthority)
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

    fun getAuthentication(token: String): Authentication {
        val claims: Claims =
            Jwts.parser().json(JWTKotlinxDeserializer).verifyWith(secretKey).build().parseSignedClaims(token).payload
        val authoritiesClaim = claims[AUTHORITIES_KEY]
        val authorities: Collection<GrantedAuthority> =
            if (authoritiesClaim == null) AuthorityUtils.NO_AUTHORITIES else AuthorityUtils
                .commaSeparatedStringToAuthorityList(authoritiesClaim.toString())
        val principal = User(claims.subject, "", authorities)
        return UsernamePasswordAuthenticationToken(principal, token, authorities)
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
        private const val AUTHORITIES_KEY = "roles"
    }
}
