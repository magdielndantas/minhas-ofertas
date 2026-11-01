plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.minhasofertas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minhasofertas.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.0.21"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        val variant = this
        val variantName = variant.name
        val vName = variant.versionName
        variant.outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output?.filters?.firstOrNull()?.identifier ?: ""
            val abiSuffix = if (abi.isNotEmpty()) "-$abi" else ""
            if (variantName == "release") {
                output?.outputFileName = "MinhasOfertas-v${vName}${abiSuffix}.apk"
            } else {
                output?.outputFileName = "MinhasOfertas-v${vName}${abiSuffix}-${variantName}.apk"
            }
        }
    }
}

dependencies {
    // Jetpack Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Image loading with Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // TDLib prebuilt Android AAR
    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("io.github.tdlib-android:ktx:0.1.1")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Security Crypto for Keystore EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
