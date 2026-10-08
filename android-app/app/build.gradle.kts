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
        // Same app identity as v1.0.1 (com.cue.daymark). Higher versionCode so Android
        // treats this as an in-place update and preserves app-private encrypted data
        // when signed with the same production keystore secrets already in GitHub.
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            val anyProvided = keystorePath != null || keystorePassword != null || alias != null || keyPassword != null

            if (anyProvided) {
                val missing = mutableListOf<String>()
                if (keystorePath.isNullOrBlank()) missing.add("KEYSTORE_PATH")
                if (keystorePassword.isNullOrBlank()) missing.add("KEYSTORE_PASSWORD")
                if (alias.isNullOrBlank()) missing.add("KEY_ALIAS")
                if (keyPassword.isNullOrBlank()) missing.add("KEY_PASSWORD")

                if (missing.isNotEmpty()) {
                    throw GradleException(
                        "Release signing configuration error: Missing required environment variable(s): " +
                        missing.joinToString(", ") +
                        ". All four variables (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) must be set."
                    )
                }

                val keystoreFile = file(keystorePath!!)
                if (!keystoreFile.exists()) {
                    throw GradleException(
                        "Release signing configuration error: Keystore file not found at KEYSTORE_PATH: ${keystoreFile.absolutePath}"
                    )
                }

                storeFile = keystoreFile
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
            val isSigningConfigured = System.getenv("KEYSTORE_PATH") != null
            if (isSigningConfigured) {
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

gradle.taskGraph.whenReady {
    val hasReleaseAssembleTask = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true) &&
        task.name.startsWith("assemble", ignoreCase = true)
    }

    if (hasReleaseAssembleTask) {
        val missing = mutableListOf<String>()
        if (System.getenv("KEYSTORE_PATH").isNullOrBlank()) missing.add("KEYSTORE_PATH")
        if (System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()) missing.add("KEYSTORE_PASSWORD")
        if (System.getenv("KEY_ALIAS").isNullOrBlank()) missing.add("KEY_ALIAS")
        if (System.getenv("KEY_PASSWORD").isNullOrBlank()) missing.add("KEY_PASSWORD")

        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release build rejected: Missing required signing environment variable(s): " +
                missing.joinToString(", ") +
                ". Refusing to build an unsigned or partially configured release APK."
            )
        }
    }
}

dependencies {
    // Uses only Android platform APIs at runtime; no remote app/library dependencies.
}
