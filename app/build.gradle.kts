plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version comes from the git tag in CI (-PversionName=1.2.3); this is the local fallback.
val appVersionName: String = (project.findProperty("versionName") as String?) ?: "1.0.0"

// 1.2.3 -> 10203, so a newer tag always sorts above an older install.
val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toInt()
    ?: appVersionName.substringBefore('-').split(".").let { parts ->
        (parts.getOrNull(0)?.toIntOrNull() ?: 1) * 10000 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0) * 100 +
            (parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

// Set by CI when the signing secrets exist. Absent locally, and that is fine.
val releaseKeystore: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.dalakoti.apps.speechtotext"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dalakoti.apps.speechtotext"
        minSdk = 33
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // A ~600 MB model has no business in a 32-bit address space.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // With the CI secrets present this is a real release key. Without them it
            // falls back to the debug key, so `assembleRelease` still produces an APK
            // you can install — it just cannot update a differently-signed install.
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
