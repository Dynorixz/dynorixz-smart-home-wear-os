plugins {
    alias(libs.plugins.android.application)
}

val releaseKeyStorePath = System.getenv("DYNORIXZ_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("DYNORIXZ_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("DYNORIXZ_KEY_ALIAS")
val releaseKeyPassword = System.getenv("DYNORIXZ_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeyStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.dynorixz.smarthome.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dynorixz.smarthome"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeyStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
}
