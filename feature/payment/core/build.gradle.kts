plugins {
    id("cielosmart.jvm.library")
}

// Sem dependência alguma: o contrato de pagamento é só `suspend` + tipos de dados. Não conhece UI,
// DI, Android, adquirente — nem coroutines além do que a linguagem já oferece.
