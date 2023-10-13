import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.3"
    kotlin("jvm") version "1.9.20-Beta2"
    kotlin("plugin.spring") version "1.9.20-Beta2"
    kotlin("plugin.serialization") version "1.9.20-Beta2"
    id("org.ajoberstar.grgit") version "4.1.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

val jjwtVersion = "0.12.2"

dependencies {
    // spring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra-reactive")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // kotlin
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("io.projectreactor.addons:reactor-extra:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // jwt
    implementation("io.jsonwebtoken:jjwt-api:${jjwtVersion}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${jjwtVersion}")

    // security
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.3")

    // s3
    implementation("software.amazon.awssdk:s3:2.20.162")
    implementation("software.amazon.awssdk:netty-nio-client:2.20.162")

    // shedlock
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.8.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-cassandra:5.8.0")



    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:cassandra")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("**/version.json") {
        filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to mapOf(
                "branch.name" to grgit.branch.current().name,
                "build.number" to (project.properties["build.number"] ?: "@build.number@"),
                "build.date" to (project.properties["buildTimestamp"] ?: "@build.date@"),
                "git.commit.id" to grgit.head().id,
                "project.artifactId" to project.name,
                "project.version" to project.version.toString(),
            )
        )
    }
}
