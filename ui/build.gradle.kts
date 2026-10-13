// ViewModels and Compose screens. Depends on :domain contracts only; :composeApp supplies implementations.
plugins {
    kotlin("multiplatform"); kotlin("plugin.compose")
    id("org.jetbrains.compose"); id("com.android.library")
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(compose.runtime); implementation(compose.foundation)
            implementation(compose.material3); implementation(compose.animation)
            // System back on Android returns to Today or More; no-op on iOS.
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.1")
            // MVVM: multiplatform ViewModel and lifecycle-aware state collection (matches Compose 1.12.1).
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            api("io.insert-koin:koin-core:4.2.2")
            implementation("io.insert-koin:koin-compose:4.2.2")
            implementation("io.insert-koin:koin-compose-viewmodel:4.2.2")
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") }
        androidMain.dependencies { implementation("androidx.activity:activity-compose:1.9.3") }
    }
}
android {
    namespace = "com.prepdesk.ui"; compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
