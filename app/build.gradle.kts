import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "es.kalvo.escanerqr"
    compileSdk = 36

    defaultConfig {
        applicationId = "es.kalvo.escanerqr"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "2.5"
        // The S24 is arm64; the library also ships x86 builds for emulators we do not need.
        ndk { abiFilters += setOf("arm64-v8a") }
    }

    // Release signing: only if `firma.properties` exists in the project root (never committed).
    // Keys: storeFile (absolute path), storePassword, keyAlias, keyPassword.
    val firma = rootProject.file("firma.properties")
    if (firma.exists()) {
        val p = Properties().apply { firma.inputStream().use { load(it) } }
        signingConfigs {
            create("propia") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (firma.exists()) signingConfig = signingConfigs.getByName("propia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.1")
    // Camera preview and frames (Apache 2.0).
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    // Barcode reading, all formats, on the phone (zxing-cpp, Apache 2.0). No ML Kit, no Play Services.
    implementation("io.github.zxing-cpp:android:3.1.1")
    // Only to draw a code as a QR picture ("Ver como QR"), pure Java (ZXing, Apache 2.0).
    implementation("com.google.zxing:core:3.5.3")
    // Wifis part (was the «Mis wifis» app until v2.0): commands and the wifi service as the shell user (Shizuku, MIT).
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Lets the app call the hidden wifi service methods (Apache 2.0, Maven Central).
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    testImplementation("junit:junit:4.13.2")
}
