plugins {
    id("com.android.application")
}

// ---------------------------------------------------------------------------
// Production release signing (#41 / 34A)
//
// Credentials are read from, in order of precedence:
//   1. environment variables RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD /
//      RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD (what CI uses, fed from secrets)
//   2. an untracked android-app/keystore.properties (local convenience)
//
// The build FAILS CLOSED for release/bundle/package tasks when the credentials
// are absent. It never falls back to the debug key, so nothing that looks like a
// production artifact can ever be produced unsigned or debug-signed.
// Debug/device-test builds are deliberately unaffected.
// ---------------------------------------------------------------------------

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = java.util.Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSecret(envName: String, propertyName: String): String? {
    val fromEnv = System.getenv(envName)?.takeIf { it.isNotBlank() }
    if (fromEnv != null) return fromEnv
    return releaseKeystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}

val hasReleaseSigning = listOf(
    "RELEASE_STORE_FILE" to "storeFile",
    "RELEASE_STORE_PASSWORD" to "storePassword",
    "RELEASE_KEY_ALIAS" to "keyAlias",
    "RELEASE_KEY_PASSWORD" to "keyPassword",
).all { (envName, propertyName) -> releaseSecret(envName, propertyName) != null }

val productionReleaseTasks = listOf(
    "assembleGithubSideloadRelease",
    "assemblePlayRelease",
    "bundleGithubSideloadRelease",
    "bundlePlayRelease",
    "packageGithubSideloadRelease",
    "packagePlayRelease",
)

if (!hasReleaseSigning) {
    gradle.taskGraph.whenReady {
        val requested = allTasks.map { it.name }
        val requestedProductionTask = productionReleaseTasks.firstOrNull { it in requested }
        if (requestedProductionTask != null) {
            throw GradleException(
                "Production release signing is not configured. Set RELEASE_STORE_FILE, " +
                    "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD " +
                    "(or provide android-app/keystore.properties) before building a release " +
                    "variant (attempted task: $requestedProductionTask). Refusing to produce an " +
                    "unsigned or debug-signed production artifact."
            )
        }
    }
}

android {
    namespace = "com.cue.daymark"
    compileSdk = 35

    flavorDimensions += "distribution"

    productFlavors {
        create("githubSideload") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
        }
    }

    defaultConfig {
        applicationId = "com.cue.daymark"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "com.cue.daymark.DaymarkPlatformInstrumentation"
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSecret("RELEASE_STORE_FILE", "storeFile")!!)
                storePassword = releaseSecret("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = releaseSecret("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = releaseSecret("RELEASE_KEY_PASSWORD", "keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // When credentials are absent the release build has no signingConfig and the
            // task-graph guard above fails the build before any artifact is produced.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    // Uses only Android platform APIs at runtime; no remote app/library dependencies.
}
