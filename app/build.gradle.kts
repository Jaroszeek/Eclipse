import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Klucz podpisu wersji release leży poza repozytorium: ~/.eclipse-signing/keystore.properties (README, „Wersja release”).
val signing = Properties().apply {
    val file = File(System.getProperty("user.home"), ".eclipse-signing/keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "pl.eclipse.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pl.eclipse.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (!signing.isEmpty) {
            create("release") {
                storeFile = File(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.tink.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.haze.blur)
    implementation(libs.vico.compose.m3)
    implementation(libs.calendar.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
