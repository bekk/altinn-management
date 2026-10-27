// Jib shells out to Apache Commons Compress to read Zstandard-compressed layers (DHI base images use zstd), but
// Commons Compress needs the zstd-jni library on jib's own classloader to do so. Adding it as a project dependency
// is not enough: it must be on the buildscript classpath so jib itself can see it.
buildscript {
    dependencies {
        classpath(libs.zstd.jni)
    }
}

plugins {
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.jib)
}

val dockerImage = providers.gradleProperty("dockerImage").orElse("altinn-management-rest-server:local")
val dockerTags = providers.gradleProperty("dockerTags").orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()
    ?: emptySet()

application {
    mainClass = "no.kartverket.altinnmanagement.restserver.MainKt"
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.netty)
    // ktor pulls in an older netty with known vulnerabilities
    implementation(platform(libs.netty.bom))

    testImplementation(ktorLibs.server.testHost)
}

jib {
    from {
        image = "ghcr.io/kartverket/emudhra-images/jre/dhi:jre-25.0-alpine3.24@sha256:0560e1680f602893991ce298a03975ebf3cc467fc0b01275ed1d83d03e013044"
    }
    to {
        image = dockerImage.get()
        tags = dockerTags
    }
    container {
        ports = listOf("8080")
        user = "1000:1000"
    }
}
