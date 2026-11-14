import java.util.Properties

plugins {
    id("com.android.application")
}

// Assinatura de release. Lê keystore.properties (fora do Git). Sem esse arquivo, o build de release sai sem assinatura.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.containsKey("storeFile")

android {
    namespace = "com.douglasribeiro.mytasker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.douglasribeiro.mytasker"
        minSdk = 26
        targetSdk = 36
        // Aumente a cada versão publicada. O Android só instala por cima se for maior.
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        aidl = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
