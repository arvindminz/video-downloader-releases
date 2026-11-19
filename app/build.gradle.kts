plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.videodownloader"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.videodownloader"
//        minSdk = 31
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Required by youtubedl-android — the library ships native .so files
    // (Python + yt-dlp + FFmpeg binaries). They MUST be extracted from the
    // APK at install time, otherwise the runtime crashes with
    // UnsatisfiedLinkError when merging streams.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // ── Jetpack Compose ────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Material Components (for dialog themes) ────────────────────
    implementation(libs.material)

    // ── youtubedl-android (media extraction + FFmpeg merging) ──────
    implementation(libs.library)
    implementation(libs.ffmpeg)

    // ── WorkManager + Coroutines ───────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ── Media3 ExoPlayer for Video Player UI ───────────────────────
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // ── Coil for thumbnails ────────────────────────────────────────
    implementation(libs.coil)

    // ── RecyclerView (optional, for XML fallback) ──────────────────
    implementation(libs.androidx.recyclerview)

    // ── Testing ────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}