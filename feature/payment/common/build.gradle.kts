plugins {
    id("cielosmart.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.payment.cielo"

    testOptions {
        unitTests {
            // Lets tests instantiate framework types (e.g. ActivityNotFoundException) without the
            // android.jar stubs throwing "Stub!".
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":feature:payment:core"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.kotlinx.serialization.json)
    // PaymentActivity: ComponentActivity + Activity Result API + ViewModel. No Compose.
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.viewmodel)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
