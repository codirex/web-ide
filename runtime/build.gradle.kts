plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.codirex.webide.runtime"
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
    implementation(project(":core"))

    testImplementation(libs.junit)
}
