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
        create("release") {
            // Release signing is driven entirely by CI environment variables so no
            // keystore or secret ever enters the repository. When KEYSTORE_PATH is
            // unset (local debug builds), this config stays inert and Gradle uses
            // the default debug signing key.
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")
            if (keystorePath != null && keystorePassword != null && alias != null && keyPassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = alias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Signed with the release config only when CI provides a keystore;
            // otherwise Gradle falls back to its default unsigned/debug behavior.
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
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
