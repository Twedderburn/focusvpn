plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// The blocklist format is compiled into both the build (here) and the app, so they hash names identically.
kotlin {
    sourceSets["main"].kotlin.srcDir("../blocklist-core/src")
}
