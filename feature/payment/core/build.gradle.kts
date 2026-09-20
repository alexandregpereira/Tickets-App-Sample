plugins {
    id("cielosmart.jvm.library")
}

// No dependencies at all: the payment contract is just `suspend` + data types. It knows nothing of
// UI, DI, Android or acquirers — not even coroutines beyond what the language itself offers.
