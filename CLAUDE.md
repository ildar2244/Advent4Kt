# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Advent4Kt is a multi-module Kotlin project consisting of:
- **app**: Android application module (API 29+, target SDK 36)
- **tgbot**: Pure JVM/Java library module (likely for Telegram bot functionality)

Both modules target Java 11 and use Kotlin 2.0.21.

## Build Commands

### Build the entire project
```bash
./gradlew build
```

### Build specific modules
```bash
./gradlew :app:build
./gradlew :tgbot:build
```

### Clean build
```bash
./gradlew clean build
```

### Run Android app
```bash
./gradlew :app:installDebug
# or with device running
./gradlew :app:installDebug && adb shell am start -n com.example.advent4kt/.MainActivity
```

## Testing

### Run all tests
```bash
./gradlew test
```

### Run unit tests for specific module
```bash
./gradlew :app:test
./gradlew :tgbot:test
```

### Run Android instrumentation tests
```bash
./gradlew :app:connectedAndroidTest
```

### Run a specific test class
```bash
./gradlew :app:test --tests com.example.advent4kt.ExampleUnitTest
```

## Project Architecture

### Module Structure
- **Root**: Multi-module Gradle project using Kotlin DSL and version catalogs (`gradle/libs.versions.toml`)
- **app module**: Standard Android app with single MainActivity, uses Material Design components and edge-to-edge display
- **tgbot module**: Pure Kotlin JVM library (no Android dependencies)

### Key Configuration Details
- Version catalog pattern in `gradle/libs.versions.toml` for dependency management
- Android app namespace: `com.example.advent4kt`
- TGBot package: `com.example.tgbot`
- Minimum Android SDK: 29 (Android 10)
- Target/Compile SDK: 36
- Uses `alias(libs.plugins.*)` pattern for plugin management

### Source Locations
- Android app code: `app/src/main/java/com/example/advent4kt/`
- Android tests: `app/src/test/` (unit) and `app/src/androidTest/` (instrumentation)
- TGBot code: `tgbot/src/main/java/com/example/tgbot/`
- Android resources: `app/src/main/res/`
- Android manifest: `app/src/main/AndroidManifest.xml`
