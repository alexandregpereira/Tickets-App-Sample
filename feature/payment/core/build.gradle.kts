plugins {
    id("cielosmart.android.library")
}

android {
    namespace = "com.example.payment.core"
}

dependencies {
    // Só coroutines: o contrato de pagamento não conhece UI, DI nem adquirente.
    implementation(libs.kotlinx.coroutines.core)
}
