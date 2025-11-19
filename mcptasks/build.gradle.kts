import java.util.Properties

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildconfig)
    application
}

application {
    mainClass.set("com.example.mcptasks.app.TasksMcpServerKt")
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
    packageName("com.example.mcptasks")
    buildConfigField("String", "MCP_TASKS_WS_HOST", "\"${localProperties.getProperty("MCP_TASKS_WS_HOST", "localhost")}\"")
    buildConfigField("Int", "MCP_TASKS_WS_PORT", localProperties.getProperty("MCP_TASKS_WS_PORT", "8766"))
    buildConfigField("String", "MCP_TASKS_DB_PATH", "\"${localProperties.getProperty("MCP_TASKS_DB_PATH", "./data/tasks.db")}\"")

    // YandexGPT API configuration
    buildConfigField("String", "YANDEX_GPT_API_KEY", "\"${localProperties.getProperty("YANDEX_GPT_API_KEY", "")}\"")
    buildConfigField("String", "YANDEX_CLOUD_FOLDER_ID", "\"${localProperties.getProperty("YANDEX_CLOUD_FOLDER_ID", "")}\"")

    // Telegram API configuration
    buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"${localProperties.getProperty("TELEGRAM_BOT_TOKEN", "")}\"")
    buildConfigField("String", "TELEGRAM_CHANNEL_CHAT_ID", "\"${localProperties.getProperty("TELEGRAM_CHANNEL_CHAT_ID", "")}\"")
}

dependencies {
    // Ktor Server (for WebSocket MCP transport)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)

    // Ktor Client (for YandexGPT and Telegram API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Exposed ORM for SQLite
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    // SQLite JDBC Driver
    implementation(libs.sqlite.jdbc)

    // Logging
    implementation(libs.slf4j.simple)
}
