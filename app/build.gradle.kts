import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.clutchalarm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clutchalarm.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // QA success code is absent from ordinary debug AND all release builds.
    sourceSets.getByName("debug").java.srcDir(
        if (providers.gradleProperty("behaviorQa").orNull == "true") "src/behaviorQa/java" else "src/productionAuth/java",
    )
    sourceSets.getByName("release").java.srcDir("src/productionAuth/java")

    buildTypes {
        getByName("debug") {
            // QA identity is debug-only; even an opt-in release keeps production identity.
            if (providers.gradleProperty("behaviorQa").orNull == "true") {
                applicationIdSuffix = ".behaviorqa"
                versionNameSuffix = "-behaviorqa"
                manifestPlaceholders["appLabel"] = "ClutchAlarm QA"
            }
            buildConfigField("boolean", "QA_DIAGNOSTICS", (providers.gradleProperty("behaviorQa").orNull == "true").toString())
        }
        getByName("release") { buildConfigField("boolean", "QA_DIAGNOSTICS", "false") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

android.sourceSets.getByName("test").resources.srcDir("src/main/assets")
