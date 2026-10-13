pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "PrepDeskKMP"
// :domain has no platform or framework code; :ui depends on :domain only, so screens cannot reach data internals.
include(":domain", ":data", ":ui", ":composeApp")
