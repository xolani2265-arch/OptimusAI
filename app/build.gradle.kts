plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.optimus.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.optimus.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("OPTIMUS_KEYSTORE_PATH")
            val keystorePassword = System.getenv("OPTIMUS_KEYSTORE_PASSWORD")
            val keyPassword = System.getenv("OPTIMUS_KEY_PASSWORD")
            val keyAlias = System.getenv("OPTIMUS_KEY_ALIAS")

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyPassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyPassword = keyPassword
                this.keyAlias = keyAlias
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
