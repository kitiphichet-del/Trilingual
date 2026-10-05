plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.trilingual.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trilingual.ai"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("previewStable") {
            storeFile = file("preview-debug.jks")
            storePassword = "trilingual-preview"
            keyAlias = "trilingual-preview"
            keyPassword = "trilingual-preview"
        }
    }
    buildTypes {
        getByName("debug") {
            // v0.1.0/v0.1.1 CI builds used ephemeral debug signatures. v0.1.2 starts a stable preview track.
            applicationIdSuffix = ".preview2"
            manifestPlaceholders["appDisplayName"] = "TriLingual AI 0.1.2"
            signingConfig = signingConfigs.getByName("previewStable")
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["appDisplayName"] = "TriLingual AI"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.mlkit:translate:17.0.3")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
