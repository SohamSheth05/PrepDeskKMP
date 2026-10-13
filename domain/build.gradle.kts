// Plain Kotlin models, rules, and repository contracts. No UI, database, or network dependencies.
plugins {
    kotlin("multiplatform"); kotlin("plugin.serialization")
    id("com.android.library")
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android {
    namespace = "com.prepdesk.domain"; compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
