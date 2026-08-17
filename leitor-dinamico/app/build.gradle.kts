plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nipi.leitordinamico"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nipi.leitordinamico"
        // 26 (Android 8) permite usar só ícone adaptativo, sem PNG de várias densidades.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-beta"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            // O PdfBox traz metadados que colidem entre si no empacotamento.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    // Extração de texto de PDF. Java puro, licença Apache 2.0.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
