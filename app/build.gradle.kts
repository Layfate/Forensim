import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing. The upload keystore and its passwords never live in this repository: they're
// read from keystore.properties (storeFile, storePassword, keyAlias, keyPassword), by default in
// ~/Documents/Forensimkeys, or wherever FORENSIM_KEYSTORE_PROPERTIES points. Without that file,
// release builds fall back to the debug key (fine for local testing, not for publishing).
val releaseKeys = Properties().apply {
    val path = System.getenv("FORENSIM_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/Documents/Forensimkeys/keystore.properties"
    val f = file(path)
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.forensim.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.forensim.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase project. Only the PUBLISHABLE key may ever be placed in the app; the
        // service_role key and AI provider keys live exclusively in Edge Function secrets.
        buildConfigField("String", "SUPABASE_URL", "\"https://dadmvplgluqewozshtdz.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_r4vspIgXrAS_Px68yIzswg_pf4VTorH\"")
    }

    signingConfigs {
        if (!releaseKeys.isEmpty) create("release") {
            storeFile = file(releaseKeys.getProperty("storeFile"))
            storePassword = releaseKeys.getProperty("storePassword")
            keyAlias = releaseKeys.getProperty("keyAlias")
            keyPassword = releaseKeys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 (shrink + optimize) and a non-debuggable runtime are what make Compose scroll at
            // full frame rate; debug builds run partly interpreted and always feel laggy.
            optimization {
                enable = true
            }
            // Project keep rules (see the file for why each rule exists).
            proguardFiles("proguard-rules.pro")
            // The upload key when its properties file is present (see the top of this file);
            // otherwise the debug key, so the optimized build still installs for local testing.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // BouncyCastle (pulled in by pdfbox-android) ships ~4 MB of post-quantum data files that
            // PDF text extraction never touches.
            excludes += "org/bouncycastle/**"
        }
        jniLibs {
            // OpenCV (marker AR) is ~30 MB per CPU type. Phones are ARM; the x86 copies only serve
            // emulators, where marker mode simply reports itself unavailable.
            excludes += listOf("lib/x86/libopencv_java4.so", "lib/x86_64/libopencv_java4.so")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    // Installs the baseline profiles shipped by Compose & co. on sideloaded builds too (AOT-compiles
    // the hot UI code), so first scrolls and transitions do not stutter while the JIT warms up.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    // Poppins / Manrope / DM Sans from Google Fonts at runtime (Inter is bundled as the fallback).
    implementation(libs.androidx.compose.ui.text.google.fonts)
    // Per-app language (English / Filipino), including Android 12 and older.
    implementation(libs.androidx.appcompat)
    // Lottie animations (success badge, scan loader, review) made for Forensim in res/raw.
    implementation(libs.lottie.compose)
    // On-device PDF text extraction for "Generate quiz from PDF" (Apache-2.0). Only the text is sent to the AI.
    implementation(libs.pdfbox.android)
    // Case codes: scan a professor's QR (Play services scanner, no camera permission) and draw QR codes (ZXing core).
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)
    // Real AR (camera + surface tracking) in the Evidence lab. "AR Optional": phones without it still work.
    implementation(libs.arcore)
    // Camera-view fallback for phones without ARCore (camera preview + rotation sensor).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Marker AR on phones without ARCore: finds a printed ArUco marker and the phone's pose to it.
    implementation(libs.opencv)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
