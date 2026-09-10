import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val revenueCatApiKey = localProperties.getProperty("REVENUECAT_API_KEY", "")
val safeCircleApiBaseUrl = localProperties.getProperty("SAFECIRCLE_API_BASE_URL", "")
val safeCircleDemoApiToken = localProperties.getProperty("SAFECIRCLE_DEMO_API_TOKEN", "")

android {
    namespace = "com.harshapriya.safecircle"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.harshapriya.safecircle"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
        buildConfigField("String", "SAFECIRCLE_API_BASE_URL", "\"$safeCircleApiBaseUrl\"")
        buildConfigField("String", "SAFECIRCLE_DEMO_API_TOKEN", "\"$safeCircleDemoApiToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(libs.revenuecat)
    implementation(libs.revenuecat.ui)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.work)
    testImplementation(libs.junit)
}
