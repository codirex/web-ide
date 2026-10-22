plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.codirex.webide.terminal"
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
    implementation(project(":runtime"))

    // Exposes TerminalView and (through it) TerminalSession to the modules that show a terminal.
    api(libs.termux.terminal.view)

    testImplementation(libs.junit)
}
