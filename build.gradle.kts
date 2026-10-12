plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.chaquo.python") version "17.0.0" apply false
}

// A single Android application module keeps the first release easy to build and install.
// Engine boundaries are explicit Kotlin packages so runtime/provider modules can be
// extracted into Gradle modules later without changing the public interfaces.
