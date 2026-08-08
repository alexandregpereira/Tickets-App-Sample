plugins {
    id("cielosmart.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.payment.cielo"

    testOptions {
        unitTests {
            // Permite instanciar tipos do framework usados nos testes (ex.: ActivityNotFoundException)
            // sem que os stubs do android.jar lancem "Stub!".
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":feature:payment:core"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
