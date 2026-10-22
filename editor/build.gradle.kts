plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.codirex.webide.editor"
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

    // Sora Editor (LGPL-2.1+). The module is kept as a plain, unmodified dependency.
    api(libs.sora.editor)
    api(libs.sora.language.textmate)
    // Faster highlighting with less memory pressure, and support for more TextMate grammars.
    implementation(libs.sora.oniguruma.native)
    implementation(libs.gson)

    testImplementation(libs.junit)
}
