plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.decio.skipii"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sutube"
        minSdk = 26
        targetSdk = 36
        versionCode = 113
        versionName = "1.13"
    }

    signingConfigs {
        create("release") {
            val envPath = providers.environmentVariable("KEYSTORE_BASE64").orNull
            val localKeystore = file("sutube.jks")
            val chosenFile = when {
                !envPath.isNullOrBlank() && file(envPath).isFile -> file(envPath)
                localKeystore.isFile -> localKeystore
                else -> null
            }
            if (chosenFile != null) {
                storeFile = chosenFile
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orElse("YOUR_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").orElse("YOUR_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orElse("YOUR_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null } ?: signingConfigs.getByName("debug")
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    packaging {
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    val media3 = "1.10.1"

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui-compose:$media3")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.window:window:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
