plugins {
    id("cielosmart.android.library.compose")
}

android {
    namespace = "com.example.checkout"
}

dependencies {
    // Contracts only: the checkout sees neither the catalog nor the acquirer internals.
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
