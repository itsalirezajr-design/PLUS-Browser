plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.plus.browser"
    compileSdk = 35
    defaultConfig { applicationId = "com.plus.browser"; minSdk = 26; targetSdk = 35; versionCode = 12; versionName = "1.1" }
    signingConfigs {
        create("plusDebug") {
            storeFile = rootProject.file("signing/plus-debug.keystore")
            storePassword = "android"
            keyAlias = "plusdebug"
            keyPassword = "android"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("plusDebug") } }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
