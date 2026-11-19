plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.livecraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.livecraft"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    testImplementation("junit:junit:4.13.2")
}
