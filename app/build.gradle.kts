plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.optimus.ai"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.optimus.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.2.0"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
