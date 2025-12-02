import java.util.Properties

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildconfig)
    application
}

application {
    mainClass.set("com.example.mcpgit.app.GitMcpServerKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

// Чтение local.properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// Настройка BuildConfig
buildConfig {
    packageName("com.example.mcpgit")
    buildConfigField("String", "MCP_GIT_WS_HOST", "\"${localProperties.getProperty("MCP_GIT_WS_HOST", "localhost")}\"")
    buildConfigField("Int", "MCP_GIT_WS_PORT", localProperties.getProperty("MCP_GIT_WS_PORT", "8767"))
    buildConfigField("String", "GIT_REPO_PATH", "\"${localProperties.getProperty("GIT_REPO_PATH", ".")}\"")
}

dependencies {
    // MCP SDK
    implementation(libs.mcp.kotlin.sdk.server)

    // Ktor Server (for WebSocket MCP transport)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.sse)  // Required by MCP SDK

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.slf4j.simple)
}
