package com.vitekkor.s3replicationservice.controller

import com.vitekkor.s3replicationservice.exceptions.handling.UserCreationError
import com.vitekkor.s3replicationservice.model.UserDto
import com.vitekkor.s3replicationservice.model.db.User
import com.vitekkor.s3replicationservice.repository.UserRepository
import com.vitekkor.s3replicationservice.util.toUser
import com.vitekkor.s3replicationservice.util.toUserDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@RestController
@RequestMapping("/user")
class UserController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @GetMapping("/")
    fun getUsers(): Flux<UserDto> {
        return userRepository.findAll().flatMap { user ->
            user.toUserDto().toMono()
        }
    }

    @PostMapping("/create")
    fun createUser(@RequestBody newUser: Mono<UserDto>): Mono<User> {
        return newUser.flatMap { userDto ->
            userRepository.save(userDto.toUser(checkNotNull(userDto.password).let(passwordEncoder::encode)))
        }.onErrorMap(IllegalStateException::class.java) {
            UserCreationError(400, "Invalid password")
        }
    }

    @PostMapping("/save")
    fun saveUser(@RequestBody newUser: Mono<UserDto>): Mono<User> {
        return newUser.flatMap { userDto ->
            userRepository.findById(userDto.login).flatMap {
                userRepository.save(userDto.toUser(it.password))
            }
        }
    }

}
