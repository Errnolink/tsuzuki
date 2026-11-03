plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

android {
    namespace = "dev.errnolink.tsuzuki.designsystem"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    api(compose.foundation)
    api(compose.animation)
    implementation(compose.material3.core)
    implementation(androidx.corektx)
    implementation(compose.activity)
    implementation(libs.haze)
    implementation(libs.backdrop)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
