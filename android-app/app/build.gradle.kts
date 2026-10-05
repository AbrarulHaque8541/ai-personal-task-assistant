plugins {
    id("com.android.application")
}

android {
    namespace = "com.cue.daymark"
    compileSdk = 35

    flavorDimensions += "distribution"

    productFlavors {
        create("githubSideload") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_INSTALLATION_ENABLED", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATER_INSTALLATION_ENABLED", "false")
        }
    }

    defaultConfig {
        applicationId = "com.cue.daymark"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
