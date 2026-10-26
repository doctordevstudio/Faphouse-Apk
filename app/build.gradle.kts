import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.drdev.faphousepro"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.drdev.faphousepro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fvisibility=hidden", "-fvisibility-inlines-hidden")
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=ON")
            }
        }

        buildConfigField("String", "API_BASE_URL", "\"https://dekhohatkeyellowhouse.onrender.com\"")
        buildConfigField("String", "FAPHOUSE_DOMAIN", "\"faphouse2.com\"")
        buildConfigField("String", "TELEGRAM_URL", "\"https://t.me/drdevstudio\"")
        buildConfigField("String", "BOT_URL", "\"https://t.me/drdevbot\"")
        buildConfigField("String", "BUILD_TS", "\"${System.currentTimeMillis()}\"")
        // SHA-256 of the release APK signing cert (lowercase hex, colons stripped).
        // MUST match EXPECTED_APK_SIG_SHA256 in the backend .env.
        buildConfigField("String", "SIGNATURE_HASH", "\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module"
        )
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += "**/libnative-lib.so"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.startup)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}