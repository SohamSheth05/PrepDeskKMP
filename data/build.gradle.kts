import java.util.Base64

// Room database, Supabase services, the bundled question bank, and the repository implementations.
plugins {
    kotlin("multiplatform"); kotlin("plugin.serialization")
    id("com.android.library"); id("com.google.devtools.ksp")
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.2.0")
            // The database type appears in the platform modules that :composeApp builds.
            api("androidx.room:room-runtime:2.7.2")
            implementation("androidx.sqlite:sqlite-bundled:2.5.2")
            implementation("io.ktor:ktor-client-core:3.6.0")
            api("io.insert-koin:koin-core:4.2.2")
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"); implementation("io.ktor:ktor-client-mock:3.6.0") }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.6.0") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        androidUnitTest.dependencies { implementation("org.robolectric:robolectric:4.14.1"); implementation("com.russhwolf:multiplatform-settings-test:1.2.0"); implementation("androidx.sqlite:sqlite-framework:2.5.2") }
    }
}
android {
    namespace = "com.prepdesk.data"; compileSdk = 37
    defaultConfig { minSdk = 24; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.2")
    add("kspIosArm64", "androidx.room:room-compiler:2.7.2")
    add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.7.2")
}

// Only public project configuration belongs in the client. Service-role/AI keys stay on the server.
val generateServiceConfig by tasks.registering {
    val url = providers.environmentVariable("PREPDESK_SUPABASE_URL").orElse("")
    val key = providers.environmentVariable("PREPDESK_SUPABASE_PUBLIC_KEY").orElse("")
    val privacy = providers.environmentVariable("PREPDESK_PRIVACY_URL").orElse("")
    val support = providers.environmentVariable("PREPDESK_SUPPORT_EMAIL").orElse("")
    inputs.property("url", url); inputs.property("publicKey", key)
    inputs.property("privacyUrl", privacy); inputs.property("supportEmail", support)
    val output = layout.buildDirectory.file("generated/serviceConfig/com/prepdesk/ServiceConfig.kt")
    outputs.file(output)
    doLast {
        fun literal(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r") + "\""
        require(url.get().isEmpty() || url.get().matches(Regex("https://[a-zA-Z0-9.-]+"))) { "Use an HTTPS Supabase project URL without a path" }
        val publicKey = key.get()
        require(publicKey.isEmpty() || publicKey.startsWith("sb_publishable_") || runCatching {
            val payload = Base64.getUrlDecoder().decode(publicKey.split('.')[1]).toString(Charsets.UTF_8)
            Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(payload)
        }.getOrDefault(false)) { "Use a publishable key or legacy anon key. Server keys must never be bundled into the app." }
        require(privacy.get().isEmpty() || privacy.get().matches(Regex("https://[A-Za-z0-9.-]+(?:/[^\\s]*)?"))) { "Use an HTTPS privacy-policy URL" }
        require(support.get().isEmpty() || support.get().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Use a valid support email address" }
        output.get().asFile.apply { parentFile.mkdirs(); writeText("package com.prepdesk\nobject ServiceConfig { const val url = ${literal(url.get())}; const val publicKey = ${literal(key.get())}; const val privacyUrl = ${literal(privacy.get())}; const val supportEmail = ${literal(support.get())} }\n") }
    }
}
kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(layout.buildDirectory.dir("generated/serviceConfig"))
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach { dependsOn(generateServiceConfig) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Robolectric 4.14 runs on JDK 17; the Gradle daemon may use a newer JDK.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    javaLauncher.set(project.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>().launcherFor {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    })
}
