plugins {
    id("cielosmart.android.library.compose")
}

android {
    namespace = "com.example.checkout"
}

dependencies {
    // Só os contratos: o checkout não enxerga nem o catálogo nem a adquirente por dentro.
    implementation(project(":feature:payment:core"))
    implementation(project(":feature:shop:core"))
    implementation(project(":core:money"))
    implementation(project(":ui"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
