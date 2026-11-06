plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.folgui.relogio"
    compileSdk = 35

    defaultConfig {
        // O relógio e o celular só conversam se tiverem o mesmo id e a mesma assinatura
        applicationId = "com.example.folgui"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")

    // Telas feitas para a tela pequena e redonda do relógio
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    // Conversa com o celular pelo Bluetooth
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    // O play-services traz uma versão antiga do Fragment, que atrapalha a volta da tela de voz
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // org.json do Android não funciona fora do aparelho
    testImplementation("org.json:json:20240303")
}
