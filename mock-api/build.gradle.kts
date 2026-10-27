plugins {
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "no.kartverket.altinnmanagement.mock.MainKt"
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    // ktor pulls in an older netty with known vulnerabilities
    implementation(platform(libs.netty.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    testImplementation(ktorLibs.server.testHost)
}
