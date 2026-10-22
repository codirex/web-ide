plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.codirex.webide.workspace"
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

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    // LiveData is part of this module's public API (the project list).
    api(libs.androidx.lifecycle.livedata)

    testImplementation(libs.junit)
}
