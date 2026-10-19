plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.codirex.webide.core"
    compileSdk = (property("webide.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (property("webide.minSdk") as String).toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
}
