plugins {
    id("cielosmart.jvm.library")
}

dependencies {
    // `api` porque `PaymentResultSource.results` expõe `Flow` na API pública do módulo.
    // Fora isso, o contrato de pagamento não conhece UI, DI, Android nem adquirente.
    api(libs.kotlinx.coroutines.core)
}
