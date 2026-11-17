import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.focusvpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusvpn"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }

    // The release key lives outside the repo (PRD §5). The properties file names the keystore, the alias and a
    // file holding the password. Point FOCUSVPN_SIGNING at another file to build with a different key.
    val signingFile = file(System.getenv("FOCUSVPN_SIGNING") ?: "D:/Android/keys/focusvpn.properties")
    if (signingFile.isFile) {
        val p = Properties().apply { signingFile.inputStream().use { load(it) } }
        val pass = file(p.getProperty("storePasswordFile")).readText().trim()
        signingConfigs.create("focusvpn") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = pass
            keyAlias = p.getProperty("keyAlias")
            keyPassword = pass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The FocusVPN key when its properties file is on this machine; anyone else's build gets the debug key.
            signingConfig = signingConfigs.findByName("focusvpn") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The blocklist format is shared with buildSrc, which builds blocklist.bin from the same code.
    sourceSets["main"].java.srcDir("../blocklist-core/src")

    // Stored uncompressed so the app can memory-map it (hashes don't compress anyway).
    androidResources {
        noCompress += "bin"
    }
}

// ---- Blocklist (PRD §5): blocklists/*.txt -> blocklist.bin asset, with the grow-only check ----

val blocklistDir = rootProject.layout.projectDirectory.dir("blocklists")

androidComponents {
    onVariants { variant ->
        val task = tasks.register<BuildBlocklistTask>("build${variant.name.replaceFirstChar { it.uppercase() }}Blocklist") {
            listDir.set(blocklistDir)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, BuildBlocklistTask::outputDir)
    }
}

// Unit tests load the real blocklist.bin, the same file the debug APK ships.
tasks.withType<Test>().configureEach {
    val bin = tasks.named<BuildBlocklistTask>("buildDebugBlocklist").flatMap { it.outputDir.file("blocklist.bin") }
    inputs.file(bin).withPathSensitivity(PathSensitivity.NONE)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dfocusvpn.blocklist=${bin.get().asFile.absolutePath}") })
}

tasks.register<SnapshotReleasedBlocklistsTask>("snapshotReleasedBlocklists") {
    description = "Records the current lists as the last release's, for the grow-only check. Run only when releasing."
    listDir.set(blocklistDir)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
