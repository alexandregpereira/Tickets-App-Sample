# Ticket Sales App with Cielo Smart

*[Leia em português](README.pt-BR.md)*

A project built to explore, in practice, how to integrate the **Cielo Smart / Cielo LIO** payment
ecosystem into an Android app, using the local deep link integration.

The app sells tickets for local events and walks the full flow: **list events → pick the quantity →
pay through Cielo → record the outcome → show the receipt**.

## Running it

**Prerequisites:** JDK 21, the Android SDK with platform 36 (`compileSdk = 36`, `minSdk = 24`) and a
device or AVD.

```bash
./gradlew :app:installDebug
```

```bash
./gradlew testDebugUnitTest
```

### Testing payments with the Cielo Emulator

The app bundles **no** Cielo SDK: it talks to the integration app installed on the same device. To
transact without a physical terminal, download the
[Cielo Emulator](https://docs.cielo.com.br/cielo-smart/docs/baixando-o-emulador-cielo) and install it
on the same AVD (supported up to Android 10; it must not be installed on a real Smart terminal).
Tapping **Pay** opens the emulator with the amount already filled in and lets you simulate
**Success**, **Cancelled** or **Error**.

You can also hand over the response Intent yourself, without the emulator:

```bash
adb shell am start -a android.intent.action.VIEW -d "order://response?response=<base64-json>&responsecode=0" com.example.cielo
```

### Credentials

`clientID` and `accessToken` are **mocked** in
[CieloCredentials.kt](feature/payment/common/src/main/java/com/example/payment/cielo/CieloCredentials.kt).
For real use, replace them with the values issued by the
[Cielo Developer Portal](https://desenvolvedores.cielo.com.br/api-portal/) when registering an app
with the **Cielo Smart - Order Manager** API. The Cielo Emulator accepts the mocked values.

## The Cielo Smart integration

Cielo Smart offers a **remote** integration (Order Manager APIs, for POS systems) and a **local deep
link** one (Android intents, for apps running on the terminal itself). This app uses the local one,
which is the recommended path for selling on the terminal and requires no SDK.

**1. Request.** The order is serialized to JSON, Base64-encoded and sent in an `ACTION_VIEW` Intent:

```
lio://payment?request=<base64>&urlCallback=order://response
```

The JSON carries `accessToken`, `clientID`, `reference`, `items[]` and `value` — every monetary value
as **integer cents**. Assembled by
[CieloDeepLinkBuilder](feature/payment/common/src/main/java/com/example/payment/cielo/CieloDeepLinkBuilder.kt).

**2. Manifest.** Three mandatory pieces of configuration, all of them in
[feature/payment/common](feature/payment/common/src/main/AndroidManifest.xml) and merged into the app
manifest by AGP — `:app` declares nothing about payments:

- `<queries>` declaring Cielo's package (required on Android 11+ to even see the integration app).
  Beyond the `com.ads.lio.uriappclient` from the docs, we declare `br.com.cielosmart.orderservice`,
  used by the Cielo Emulator, plus an intent query on the `lio://` scheme, which doesn't depend on a
  package name at all;
- `<meta-data android:name="cs_integration_type" android:value="uri" />`;
- the **response contract**: an `ACTION_VIEW` `intent-filter` for `order://response` on
  `PaymentActivity`, identical to the `urlCallback` we send. Both sides live in the same module
  (`CieloDeepLinkBuilder.CALLBACK_URI` and the manifest), so neither can change without the other.

**3. Response.** Cielo returns the outcome by opening `order://response?response=<base64>` as a
**new Intent**, delivered to
[PaymentActivity](feature/payment/common/src/main/java/com/example/payment/cielo/PaymentActivity.kt)
(`singleTop`, so it arrives in `onNewIntent` without recreating anything). It finishes by handing the
payload back through the Activity Result API, and
[CieloResponseParser](feature/payment/common/src/main/java/com/example/payment/cielo/CieloResponseParser.kt)
translates Cielo's fields (`payments[].authCode`, `cieloCode`, `mask`, `terminal`,
`paymentFields.statusCode`) into the contract's acquirer-agnostic vocabulary — no Cielo name crosses
the module boundary. That is also where the **payment method chosen on the terminal** comes from,
shown on the receipt: since the app doesn't send `paymentCode`, `primaryProductName` +
`secondaryProductName` are the only source for it (`productName` is just a fallback, because the
Emulator returns a fixed mock in it). The error payload is `{"code": N, "reason": "..."}`, where `1`
is cancelled by the user, `2` generic, `3` payment error and `4` authentication error.

References: [Payment](https://docs.cielo.com.br/cielo-smart/docs/pagamento) ·
[Retrieving data](https://docs.cielo.com.br/cielo-smart/docs/recuperando-dados) ·
[Android Manifest](https://docs.cielo.com.br/cielo-smart/docs/configurando-o-android-manifest) ·
[Error codes](https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro)

## Architectural decisions

### MVI with UiModel + StateFlow

Every screen has a `UiModel` (a `ViewModel`) exposing a `StateFlow<UiState>` holding the complete,
immutable state and a `SharedFlow<UiAction>` for one-shot effects. Intents travel from the UI to the
UiModel through **public functions** (`onPayClick()`, `onIncreaseQuantityClick()`), with no
intermediate `sealed class Intent`. The `UiState` already carries the derived values
(`totalInCents`, `canPay`), which keeps the Composables dumb.

**A detail that only shows up at runtime:** during payment the checkout goes to `STOPPED`, because
another screen takes over the foreground, and an action emitted in that window on a `SharedFlow`
without replay would be lost — the purchase would be recorded, but the screen would never navigate to
the receipt. That's why actions use `replay = 1` and the UI acknowledges consumption in
`onActionHandled()`.

### One Activity for the UI, one for the payment

The whole UI lives in a single `MainActivity`, with Compose and navigation — it has no idea payments
exist. The exception is **`PaymentActivity`**, inside `feature:payment:common`: a translucent,
contentless screen that opens the Cielo app, owns the `order://response` `intent-filter` and returns
the outcome through the Activity Result API. It is what lets the rest of the app treat charging as a
call that suspends and returns a result — no event bus, no `onNewIntent` scattered around, no
"waiting for the response" state living in the checkout.

The hard part is **telling "the acquirer replied" apart from "the user backed out"**, because the
latter produces no callback at all. The distinction is made through the lifecycle: a dropout only
counts on an `onResume` that comes after the screen has been paused **and** that holds for a moment.
The Cielo app switches screens mid-flow, and during those transitions `PaymentActivity` briefly
resumes — concluding a dropout on the first `onResume` was killing approved payments.

The second detail is **surviving Activity recreation**. Rotating the device inside the Cielo app
recreates `MainActivity`: `ActivityResultRegistry` keeps the pending result, but the *callback* dies
with the old instance, and the coroutine would stay suspended forever. `PaymentResultLauncher`
rebinds each in-flight payment's key to every new Activity, and registering the key makes the
registry deliver the stored result immediately. The "once per instance, again on every instance" rule
lives in `PaymentBindings`, free of Android types and covered by JVM tests.

`replay = 1` has a trap of its own here: guarding the *emission* isn't enough, because replay
redelivers the action to every new collector — the recreated `PaymentActivity` received the old
`OpenDeepLink` and opened the Cielo app a second time. The fix is the same as in the checkout:
acknowledge consumption in `onActionHandled()`, which clears the replay.

### Multi-module architecture

The app is split into Gradle modules so the boundary with Cielo is **enforced by the compiler**
rather than by discipline: `:feature:checkout:common` depends on `:feature:payment:core`, never on
`:feature:payment:common`, so importing `CieloResponseParser` in the checkout doesn't compile.

```
:app  ──────────────► everything (composition root only)
 │
 ├─► :feature:shop:common ──────► :feature:shop:core
 ├─► :feature:checkout:common ──► :feature:shop:core
 │                             ─► :feature:payment:core
 └─► :feature:payment:common ──► :feature:payment:core   ← the ONLY module that knows about Cielo

 :ui  and  :core:money   ← shared leaves, used by several features
```

| Module | Type | Contents |
| --- | --- | --- |
| `:app` | Android app | `MainActivity`, `TicketsNavHost`, `startKoin` |
| `:ui` | Android lib | theme and `CollectUiActions` |
| `:core:money` | **Kotlin/JVM** | `formatAsBrl` |
| `:feature:shop:core` | **Kotlin/JVM** | `Event`, `GetEventUseCase` (interface) |
| `:feature:shop:common` | Android lib | mocked catalog, event list, `shopModule` |
| `:feature:checkout:common` | Android lib | checkout, `Purchase`/`PurchaseRepository`, receipt |
| `:feature:payment:core` | **Kotlin/JVM** | acquirer-agnostic payment contract |
| `:feature:payment:common` | Android lib | everything Cielo + implementations, `paymentModule` |

The `core` modules are **pure Kotlin/JVM**: they hold contracts and domain types, which need no
framework, and the choice is self-enforcing — with the JVM plugin, an `import android.*` in there
doesn't compile. It's also cheaper in practice (they produce JARs, no manifest, no AAR).

The payment contract fits in three files — `PaymentOrder`, `PaymentResult` and:

```kotlin
fun interface StartPaymentUseCase { suspend operator fun invoke(order: PaymentOrder): PaymentResult }
```

- **One call, one result.** Charging suspends until the outcome, even with an external app in the
  middle. The caller observes no channel and doesn't know there's an Activity involved.
- **`StartPaymentUseCase` doesn't record the purchase.** It only speaks `PaymentOrder` and knows
  nothing about `Event`, `Purchase` or `PurchaseRepository`. Recording is `CheckoutUiModel`'s job,
  and it writes the purchase as `PENDING` **before** calling payment: if the process is killed with
  Cielo in the foreground, a record tied to the `reference` remains for reconciliation.
- **`PaymentError.ABANDONED` separates "backed out" from "was declined".** Leaving without finishing
  is not an acquirer cancellation: nothing was charged, so the checkout discards the purchase and
  just unlocks the screen, with no receipt. A `CANCELLED_BY_USER` coming from Cielo, on the other
  hand, becomes a cancelled purchase and a receipt.

Everything used only inside a module is `internal`: all the `Cielo*` classes, the `UiModel`s, the
`UiState`s, `Purchase` and `PurchaseRepository`. Each feature's public surface is its Koin module and
the screens the NavHost calls — which is why screens don't take the UiModel as a parameter, it's
resolved inside the Composable. `build-logic/` is an included build with three convention plugins, so
the seven modules don't repeat the same `android { }` block.

### Preventing duplicate charges

1. **Reentrancy guard.** `onPayClick()` is ignored while a payment is in flight and the button is
   disabled. A double tap doesn't open two checkouts.
2. **One attempt, one `reference`, one purchase.** Each attempt generates its own UUID, sent to Cielo
   and used as the purchase identifier. An abandoned attempt is discarded entirely at the start of
   the next one, so the order we send always reflects what's on screen.
3. **Idempotent repository.** `recordResult()` ignores writes over a purchase that already has a
   final outcome, and `discard()` refuses to remove one: it protects against Android redelivering the
   same Intent and guarantees a recorded sale never disappears.

**An ordering detail that cost a bug:** the abandoned attempt is discarded at the start of the next
`onPayClick()`, not when the user comes back. Android can deliver `ON_RESUME` **before** the response
Intent; clearing the `reference` there made a legitimate outcome already on its way get discarded.

### Error handling

| Situation | Handling |
| --- | --- |
| Cielo Smart/Emulator not installed | `ActivityNotFoundException` becomes `PaymentError.APP_NOT_FOUND`; a card explains how to install it. The checkout never even opened, so the attempt is discarded entirely |
| User leaves without finishing | `PaymentError.ABANDONED`: purchase discarded, screen unlocked, **no** receipt and no message |
| Cancelled by the user (`code` 1) | Purchase recorded as `CANCELLED`, the receipt shows the reason |
| Payment/authentication error (`code` 3/4) | Purchase recorded as `DENIED` with the reason |
| Missing `response`, invalid Base64 or JSON | `CieloPaymentError.INVALID_RESPONSE`. The parser **never throws** — an exception here would leave the purchase in limbo |
| Order with no transaction | `CieloPaymentError.PAYMENT` |

## Third-party libraries

| Library | Why |
| --- | --- |
| **Jetpack Compose** (BOM) + Material 3 | Declarative UI; Material 3 is what Cielo's own best practices recommend |
| **Koin** | DI with no code generation, one configuration module per feature, and it injects runtime parameters (`eventId`, `purchaseReference`) into the UiModels with `parametersOf` |
| **Navigation Compose** (type-safe) | Three routes in one Activity, with arguments typed via `@Serializable` instead of strings |
| **kotlinx.serialization** | Serializes the request and navigates the response JSON. Chosen over `org.json` because it works in pure JVM tests — `org.json` is a stub off-device |
| **Turbine** + **kotlinx-coroutines-test** | Testing the UiModels' `StateFlow`/`SharedFlow` readably |
| **Gradle `kotlin-dsl`** (build-logic) | Our own convention plugins, so the modules don't repeat the Android/Compose setup |

No mocking library: the dependencies are small interfaces, so the fakes are written by hand. Less
magic in the tests, more readability.

## Known limitations

- **In-memory persistence.** A `ConcurrentHashMap` keeps the focus on the payment flow and on
  idempotency, but if Android kills the process while Cielo is in the foreground, pending purchases
  are lost. `PurchaseRepository` already has the right interface to persist without touching the
  UiModels.
- **No foreground service.** Cielo recommends a foreground service during payment, so Android doesn't
  kill the integration app. The consequence is mitigated: the purchase is written before opening
  payment and the outcome comes back through the Activity Result API — a single delivery, with no
  window in which a response could be lost for lack of a listener.
- **No instrumented tests.** The critical logic (idempotency, protocol, state machine) is testable on
  the JVM. The price is that `PaymentActivity`'s behavior — lifecycle ordering, rebinding after
  recreation — is only guaranteed by running on the emulator.
- **Duplicated `MainDispatcherRule`** in `:feature:checkout:common` and `:feature:shop:common`. For
  two identical 15-line files, duplication seemed cheaper than a `:core:testing` module.

## Next steps

1. **SQLite persistence** and a purchase history screen, fixing the state loss on process death.
2. **Credentials outside the source**, coming from `local.properties`/BuildConfig or a backend.
3. **A second `feature:payment:*`** — even a mock one — to prove the `payment:core` contract holds up
   for another acquirer, plus an architecture test that fails the build if any module starts
   importing `com.example.payment.cielo`.
4. **Observability** — Crashlytics and conversion metrics for the payment funnel.
