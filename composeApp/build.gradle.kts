// The Android app and iOS framework: platform entry points and the Koin graph joining :data and :ui.
plugins {
    kotlin("multiplatform"); kotlin("plugin.compose")
    id("org.jetbrains.compose"); id("com.android.application")
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "ComposeApp"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain")); implementation(project(":data")); implementation(project(":ui"))
            implementation(compose.runtime); implementation(compose.ui)
            implementation("io.insert-koin:koin-core:4.2.2")
        }
        androidMain.dependencies { implementation("androidx.activity:activity-compose:1.9.3"); implementation("io.insert-koin:koin-android:4.2.2") }
        androidUnitTest.dependencies {
            implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("org.robolectric:robolectric:4.14.1"); implementation("io.insert-koin:koin-test:4.2.2")
            implementation("com.russhwolf:multiplatform-settings:1.2.0"); implementation("com.russhwolf:multiplatform-settings-test:1.2.0")
            implementation("androidx.sqlite:sqlite-framework:2.5.2")
        }
    }
}
android {
    namespace = "com.prepdesk"; compileSdk = 37
    defaultConfig { applicationId = "com.prepdesk"; minSdk = 24; targetSdk = 36; versionCode = 2; versionName = "1.1.0" }
    signingConfigs {
        create("production") {
            System.getenv("PREPDESK_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("PREPDESK_STORE_PASSWORD")
            keyAlias = System.getenv("PREPDESK_KEY_ALIAS")
            keyPassword = System.getenv("PREPDESK_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("PREPDESK_KEYSTORE") != null) signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// Robolectric 4.14 runs on JDK 17; the Gradle daemon may use a newer JDK.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    javaLauncher.set(project.extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>().launcherFor {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    })
}

// Run this before creating an Android store artifact. Regular release builds can be checked unsigned.
tasks.register("productionBundle") {
    dependsOn("verifyProductionConfiguration", "bundleRelease")
}
val verifyProductionConfiguration by tasks.registering {
    doLast {
        require(!System.getenv("PREPDESK_SUPABASE_URL").isNullOrBlank() && !System.getenv("PREPDESK_SUPABASE_PUBLIC_KEY").isNullOrBlank()) { "Configure the public Supabase URL and publishable key" }
        require(!System.getenv("PREPDESK_PRIVACY_URL").isNullOrBlank() && !System.getenv("PREPDESK_SUPPORT_EMAIL").isNullOrBlank()) { "Configure the published privacy policy and support email before release" }
        require(listOf("PREPDESK_KEYSTORE", "PREPDESK_STORE_PASSWORD", "PREPDESK_KEY_ALIAS", "PREPDESK_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }) { "Configure production signing before generating a store artifact" }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { mustRunAfter(verifyProductionConfiguration) }
