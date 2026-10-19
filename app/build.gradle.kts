plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.subhaneetshrestha.afnopadd"
    // Android 17 ships as minor platform releases (37.0, 37.1, 37.2); together these select platforms;android-37.2.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Permanent: F-Droid identifies the app by this ID and users lose data if it changes.
        applicationId = "io.github.subhaneetshrestha.afnopadd"
        // Android 10: every supported device gets Jetpack Ink's low-latency front-buffer inking.
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
}

kotlin {
    // Compiles with JDK 17 regardless of which JDK runs Gradle (auto-provisioned via Foojay).
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
