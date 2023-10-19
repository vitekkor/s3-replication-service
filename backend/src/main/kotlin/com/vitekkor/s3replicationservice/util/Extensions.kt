package com.vitekkor.s3replicationservice.util

import com.vitekkor.s3replicationservice.model.UserDto
import com.vitekkor.s3replicationservice.model.db.User
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.GrantedAuthority

val FilePart.contentType
    get() = headers().contentType ?: MediaType.APPLICATION_OCTET_STREAM

val User.authorities
    get() = (claims + roles.map { role -> "ROLE_$role" } + ips.map { ip -> "IP_$ip" }).toTypedArray()

val GrantedAuthority.isIpAuthority
    get() = authority.startsWith("IP_")

val GrantedAuthority.isExtensionAuthority
    get() = authority.startsWith("FILE_EXT_")

fun User.toUserDto(): UserDto {
    return UserDto(
        login = login,
        roles = roles,
        isActive = isActive,
        scopes = claims.asSequence().filter { it.startsWith("SCOPE_") }.map { it.removePrefix("SCOPE_") }.toSet(),
        files = claims.asSequence().filter { it.startsWith("FILE_EXT") }.map { it.removePrefix("FILE_EXT_") }.toSet(),
        ips = ips.asSequence().map { it.removePrefix("IP_") }.toSet()
    )
}

fun UserDto.toUser(password: String): User {
    return User(
        login = login,
        password = password,
        roles = roles,
        isActive = isActive,
        claims = (scopes.asSequence().map { scope -> "SCOPE_$scope" } + files.asSequence().map { file -> "FILE_EXT_$file" }).toSet(),
        ips = ips
    )
}

fun String.apiPathShouldBeFilteredByExt(): Boolean {
    return matches("""/api/(upsert|get|delete)""".toRegex())
}
