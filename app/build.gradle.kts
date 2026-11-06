import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// O login com Google só liga com o google-services.json do Firebase, que fica fora do Git
val configFirebase = file("google-services.json")
if (configFirebase.exists()) apply(plugin = "com.google.gms.google-services")

/** O id do cliente web (tipo 3), que o login com Google pede. */
fun clienteWebGoogle(): String {
    if (!configFirebase.exists()) return ""
    return Regex("\"client_id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"client_type\"\\s*:\\s*3")
        .find(configFirebase.readText())?.groupValues?.get(1).orEmpty()
}

// A chave do Gemini vem do .env na raiz do projeto, que fica fora do Git
val ambiente: Map<String, String> = rootProject.file(".env").takeIf(File::exists)?.readLines().orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"', '\'') }

android {
    namespace = "com.example.folgui"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.folgui"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${ambiente["GEMINI_API_KEY"].orEmpty()}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${ambiente["GEMINI_MODEL"] ?: "gemini-3-flash-preview"}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT", "\"${clienteWebGoogle()}\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navegação entre telas com Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Persistência de preferências com DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Login com Google e cópia dos dados na conta
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Conversa com a versão de relógio pelo Bluetooth
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Testes da lógica das escalas
    testImplementation("junit:junit:4.13.2")
    // org.json do Android não funciona fora do aparelho: os testes da IA usam esta implementação
    testImplementation("org.json:json:20240303")

    // Testes no aparelho: agenda e banco de verdade
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
