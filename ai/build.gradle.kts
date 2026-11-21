// NextNotes AI: provider adapters, streaming, key storage and prompts. No UI and no dependency on
// the xnotes code, so it can be unit tested on its own and the fork's diff against upstream stays small.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nextnotes.ai"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Android's org.json is a stub on the unit-test classpath; the real one stands in.
    testImplementation(libs.json)
}
