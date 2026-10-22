plugins {
    alias(libs.plugins.android.application)
    id("org.codirex.trace") version "1.0.0"
}

android {
    namespace = "org.codirex.webide"
    compileSdk = (property("webide.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "org.codirex.webide"
        minSdk = (property("webide.minSdk") as String).toInt()
        targetSdk = (property("webide.targetSdk") as String).toInt()
        versionCode = 1
        versionName = "0.0.2-phase1"
    }

    // One APK per ABI plus a universal APK. The ABI split only produces separate
    // files once native libraries (the bootstrap) are packaged, starting in phase 1.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // targetSdk 28 is a deliberate decision, not an oversight.
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(project(":terminal"))
    implementation(project(":editor"))
    implementation(project(":lsp"))
    implementation(project(":workspace"))
    implementation(project(":git"))
    implementation(project(":templates"))
    implementation(project(":preview"))

    implementation(libs.material)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.coordinatorlayout)
}
