plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(ktorLibs.plugins.ktor)
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version.set(libs.versions.ktlint.cli.get())
}

application {
    mainClass = "no.kartverket.altinnmanagement.mock.MainKt"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    // ktor pulls in an older netty with known vulnerabilities
    implementation(platform(libs.netty.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
