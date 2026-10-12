plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

// Release signing material is supplied only through CI environment variables.
// Never keep a keystore or signing password in this repository.
val releaseStoreFilePath = providers.environmentVariable("RIVO_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RIVO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RIVO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RIVO_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() } && file(releaseStoreFilePath!!).isFile

android {
    namespace = "com.rivo.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rivo.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chaquopy 17 supports 64-bit Android ABIs for Python 3.13. These cover
        // current Android phones and the standard x86_64 emulator image.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            // A release APK is only packaged when a separately managed signing
            // configuration is supplied to Gradle; debug builds need no secrets.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "installRelease", "packageRelease", "validateSigningRelease")) {
        doFirst {
            if (!releaseSigningConfigured) {
                throw GradleException(
                    "A release artifact requires RIVO_RELEASE_STORE_FILE, RIVO_RELEASE_STORE_PASSWORD, " +
                        "RIVO_RELEASE_KEY_ALIAS, and RIVO_RELEASE_KEY_PASSWORD. Configure a private signing key; " +
                        "debug builds remain available without release secrets.",
                )
            }
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPython("python3.13")
        pip {
            // Chaquopy embeds these at APK build time. Device-time pip installation
            // is deliberately not enabled; the app reports missing requirements.
            install("python-telegram-bot==22.8")
            install("requests==2.34.2")
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // The editor currently uses TextFieldValue, VisualTransformation, and
    // non-wrapping BasicTextField input. Keep Compose 1.6.8 until a deliberate
    // migration of CodeEditor to the state-based TextFieldState API.
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
