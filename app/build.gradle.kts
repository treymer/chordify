import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.treymer.cadence"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 6
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storeFilePath  = System.getenv("KEYSTORE_PATH")      ?: keystoreProperties.getProperty("storeFile")
    val storePass      = System.getenv("KEYSTORE_PASSWORD")  ?: keystoreProperties.getProperty("storePassword")
    val keyAliasVal    = System.getenv("KEY_ALIAS")          ?: keystoreProperties.getProperty("keyAlias")
    val keyPassVal     = System.getenv("KEY_PASSWORD")       ?: keystoreProperties.getProperty("keyPassword")
    val hasSigningInfo = storeFilePath != null && storePass != null && keyAliasVal != null && keyPassVal != null

    if (hasSigningInfo) {
        signingConfigs {
            create("release") {
                storeFile     = file(storeFilePath!!)
                storePassword = storePass
                keyAlias      = keyAliasVal
                keyPassword   = keyPassVal
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigningInfo) signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(files("libs/TarsosDSP-Android-latest.jar"))
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
