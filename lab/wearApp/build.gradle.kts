plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.simpmusic.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.simpmusic.wear"
        minSdk = 30          // Wear OS 3+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-f1"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false          // el bug de R8 del PR #1864 no aplica aquí
        }
        release {
            isMinifyEnabled = false          // se activa en F4 con las reglas del PR #1864
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "com.google.android.horologist.annotations.ExperimentalHorologistApi",
            "androidx.media3.common.util.UnstableApi",
        )
    }
}

dependencies {
    // --- UI de reloj ---
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.ongoing)

    // --- Horologist: pantallas y repositorio ya hechos ---
    implementation(libs.horologist.media)
    implementation(libs.horologist.media.data)
    implementation(libs.horologist.media.ui)
    implementation(libs.horologist.media.ui.model)
    implementation(libs.horologist.audio.ui)
    implementation(libs.horologist.compose.layout)

    // --- Media3: MediaController para hablar con el servicio ---
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)

    implementation(libs.activity.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    testImplementation("junit:junit:4.13.2")
}
