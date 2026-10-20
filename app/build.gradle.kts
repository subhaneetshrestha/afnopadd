plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
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

    lint {
        // The quality bar from SPEC.md: a Lint warning fails the build.
        warningsAsErrors = true
        abortOnError = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // Ship the exported Room schemas with the instrumented tests so migrations can be replayed.
        getByName("androidTest").assets.directories.add("schemas")
    }
}

room {
    // Committed schema history is what makes migrations testable.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    // Compiles with JDK 17 regardless of which JDK runs Gradle (auto-provisioned via Foojay).
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Coroutines are pinned explicitly so the app and the test APK resolve the same version;
    // Compose and Lifecycle would otherwise pull an older core than kotlinx-coroutines-test.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Core and UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Stroke engine and stylus input
    implementation(libs.bundles.androidx.ink)
    implementation(libs.androidx.input.motionprediction)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    // JVM unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
