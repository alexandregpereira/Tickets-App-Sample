# App de Venda de Ingressos com Cielo Smart

App Android de venda de ingressos para eventos locais, com pagamento pelo ecossistema
**Cielo Smart / Cielo LIO** via integração local por deep link.

Fluxo: **listar eventos → escolher a quantidade → pagar na Cielo → registrar o
desfecho → exibir o comprovante**.

## Como executar

### Pré-requisitos

- JDK 21
- Android SDK com a plataforma 36 (`compileSdk = 36`, `minSdk = 24`)
- Um dispositivo ou AVD

### Rodando

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installDebug
```

Os testes unitários:

```bash
./gradlew testDebugUnitTest
```

### Testando o pagamento com o Emulador Cielo

O app **não** embute nenhum SDK da Cielo: ele conversa com o app de integração da Cielo instalado no
mesmo dispositivo. Para transacionar sem um terminal físico:

1. Baixe o **Emulador Cielo** em
   https://docs.cielo.com.br/cielo-smart/docs/baixando-o-emulador-cielo
2. Instale-o no mesmo AVD do app (o emulador é suportado até o Android 10; não deve ser instalado em
   um terminal Smart real).
3. Abra o app, escolha um evento e a quantidade e toque em **Pagar**.
   O Emulador Cielo abre com o valor já preenchido e permite simular **Sucesso**, **Cancelado** ou
   **Erro**.

Também é possível simular a resposta da Cielo sem o emulador, entregando a Intent de retorno na mão:

```bash
adb shell am start -a android.intent.action.VIEW -d "order://response?response=<json-em-base64>&responsecode=0" com.example.cielo
```

### Credenciais

`clientID` e `accessToken` estão **mockados** em
[CieloCredentials.kt](feature/payment/common/src/main/java/com/example/payment/cielo/CieloCredentials.kt)
(`MOCK_CLIENT_ID` / `MOCK_ACCESS_TOKEN`). Substitua pelos valores reais obtidos no
[Portal de Desenvolvedores da Cielo](https://desenvolvedores.cielo.com.br/api-portal/), cadastrando
um aplicativo com a API **Cielo Smart - Order Manager**. O Emulador Cielo aceita os valores mockados.

## Como foi feita a integração com a Cielo Smart

A Cielo Smart oferece dois modelos de integração: **remota** (APIs do Order Manager, para PDV) e
**local via deep link** (intents Android, para apps que rodam no próprio terminal). Este app usa a
integração local — é a indicada para um app de venda no terminal e, segundo a própria documentação,
dispensa SDK.

**1. Requisição.** O pedido é serializado em JSON, convertido para Base64 e enviado numa Intent
`ACTION_VIEW`:

```
lio://payment?request=<base64>&urlCallback=order://response
```

O JSON carrega `accessToken`, `clientID`, `reference`, `items[]` e `value` — todos os valores
monetários em **centavos inteiros**. Montado por
[CieloDeepLinkBuilder](feature/payment/common/src/main/java/com/example/payment/cielo/CieloDeepLinkBuilder.kt).

**2. Manifest.** Três configurações obrigatórias, divididas entre dois manifestos que o AGP
une no merge — as duas específicas da Cielo moram em
[feature/payment/common](feature/payment/common/src/main/AndroidManifest.xml), e só o
`intent-filter` fica em [app](app/src/main/AndroidManifest.xml), porque um `intent-filter` precisa
de uma Activity e a Activity mora lá:

- `<queries>` declarando o pacote da Cielo (obrigatório no Android 11+ para enxergar e chamar o app
  de integração). Além do `com.ads.lio.uriappclient` da documentação, declaramos também o
  `br.com.cielosmart.orderservice`, usado pelo Emulador Cielo, e uma consulta por intent no esquema
  `lio://` para não depender do nome do pacote;
- `<meta-data android:name="cs_integration_type" android:value="uri" />`, que identifica o app como
  integração por URI;
- o **contrato de resposta**: um `intent-filter` de `ACTION_VIEW` para `order://response` na
  `MainActivity`, idêntico ao `urlCallback` enviado na requisição. É a única amarração entre `:app` e
  `:feature:payment:common` que o grafo de módulos não consegue garantir.

**3. Resposta.** A Cielo devolve o resultado abrindo `order://response?response=<base64>` como uma
**nova Intent**. Como o app tem uma única Activity, ela é declarada `launchMode="singleTop"`: a
Intent chega em `onNewIntent` sem recriar a Activity, e os UiModels que aguardam o pagamento
sobrevivem. A `MainActivity` apenas repassa a Intent ao `PaymentResultDispatcher`, implementado por
[CieloPaymentResultSource](feature/payment/common/src/main/java/com/example/payment/cielo/CieloPaymentResultSource.kt); o
[CheckoutUiModel](feature/checkout/common/src/main/java/com/example/checkout/CheckoutUiModel.kt) consome, e o
[CieloResponseParser](feature/payment/common/src/main/java/com/example/payment/cielo/CieloResponseParser.kt) interpreta.

O payload de sucesso é o pedido pago, com `payments[].authCode`, `cieloCode`, `mask`, `terminal` e
`paymentFields.statusCode` (`0` Pix, `1` autorizada, `2` cancelada). O `CieloResponseParser` traduz
esses campos para o vocabulário do contrato agnóstico — `authorizationCode`, `acquirerCode` e afins —
de modo que nenhum nome da Cielo atravessa a fronteira do módulo. É de lá que sai também a
**forma de pagamento escolhida no terminal**, exibida no comprovante: como o app não envia
`paymentCode`, `paymentFields.primaryProductName` + `secondaryProductName` são a única fonte dessa
informação. A documentação descreve `productName` como a forma "compilada", mas o Emulador Cielo
devolve nesse campo um texto fixo de mock, então ele é só fallback. O de erro é
`{"code": N, "reason": "..."}`, com `1` cancelado pelo usuário, `2` genérico, `3` erro de pagamento e
`4` erro de autenticação.

Referências: [Pagamento](https://docs.cielo.com.br/cielo-smart/docs/pagamento) ·
[Recuperando dados](https://docs.cielo.com.br/cielo-smart/docs/recuperando-dados) ·
[Android Manifest](https://docs.cielo.com.br/cielo-smart/docs/configurando-o-android-manifest) ·
[Códigos de erro](https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro)

## Decisões arquiteturais

### MVI com UiModel + StateFlow

Cada tela tem um `UiModel` (um `ViewModel`) que expõe:

- `StateFlow<UiState>` — estado completo e imutável da tela;
- `SharedFlow<UiAction>` — efeitos únicos (navegação).

As intenções vão da UI para o UiModel por **funções públicas** (`onPayClick()`,
`onIncreaseQuantityClick()`), sem um `sealed class Intent` intermediário: menos cerimônia, mesma
direção única de fluxo. O `UiState` é a única fonte de verdade da tela e já traz derivados prontos
(`totalInCents`, `canPay`, `canIncreaseQuantity`), o que mantém os Composables burros.

**Detalhe não óbvio, que só aparece rodando no emulador:** durante o pagamento o app da Cielo fica em
foreground e a tela de checkout vai para `STOPPED`. Uma ação emitida nesse intervalo em um
`SharedFlow` sem replay se perderia — a compra seria registrada, mas a tela nunca navegaria para o
comprovante. Por isso os `SharedFlow` de ações usam `replay = 1` e a UI confirma o consumo em
`onActionHandled()`: a ação é entregue quando a tela volta, sem renavegar em recoletas posteriores.

### Uma única Activity

Além de ser a arquitetura moderna recomendada, aqui há um motivo concreto: a `MainActivity` é também
o ponto de entrada do `order://response`. Com `singleTop`, o retorno da Cielo não destrói nada.

### Arquitetura multi-módulo

O app é dividido em módulos Gradle para que a fronteira com a Cielo seja **garantida pelo
compilador**, e não por disciplina: `:feature:checkout:common` depende de `:feature:payment:core`,
nunca de `:feature:payment:common`, então uma tentativa de importar `CieloResponseParser` no checkout
não compila.

```
:app  ──────────────► todos (apenas raiz de composição)
 │
 ├─► :feature:shop:common ──────► :feature:shop:core
 ├─► :feature:checkout:common ──► :feature:shop:core
 │                             ─► :feature:payment:core
 └─► :feature:payment:common ──► :feature:payment:core   ← ÚNICO módulo que conhece a Cielo

 :ui  e  :core:money   ← módulos folha, usados pelas features
```

| Módulo | Pacote | Conteúdo |
| --- | --- | --- |
| `:app` | `com.example.cielo` | `MainActivity`, `TicketsNavHost`, `startKoin` |
| `:ui` | `com.example.ui` | tema e `CollectUiActions` |
| `:core:money` | `com.example.core.money` | `formatAsBrl` (Kotlin puro, sem Android) |
| `:feature:shop:core` | `com.example.shop.core` | `Event`, `GetEventUseCase` (interface) |
| `:feature:shop:common` | `com.example.shop` | catálogo mockado, listagem, `shopModule` |
| `:feature:checkout:common` | `com.example.checkout` | checkout, `Purchase`/`PurchaseRepository`, comprovante, `checkoutModule` |
| `:feature:payment:core` | `com.example.payment.core` | contrato de pagamento agnóstico |
| `:feature:payment:common` | `com.example.payment.cielo` | tudo que é Cielo + implementações, `paymentModule` |

#### O contrato `payment:core`

```kotlin
fun interface StartPaymentUseCase { suspend operator fun invoke(order: PaymentOrder): StartPaymentResult }
interface PaymentResultSource { val results: Flow<PaymentResult>; fun hasPendingResult(): Boolean; fun consume() }
interface PaymentResultDispatcher { fun dispatch(intent: Intent): Boolean }
```

Três decisões que valem explicar num code review:

- **O contrato é assíncrono em duas etapas.** `StartPaymentUseCase` só diz se conseguiu *abrir* o
  pagamento; o desfecho chega por `PaymentResultSource`. Isso não é capricho: entre as duas etapas
  existe um app externo que assume a tela, e modelar isso como uma chamada única seria mentira.
- **`StartPaymentUseCase` não grava a compra.** Ele fala apenas de `PaymentOrder` — referência,
  centavos e itens — e não conhece `Event`, `Purchase` nem `PurchaseRepository`. Quem registra a
  compra é o `CheckoutUiModel`, que grava o `Purchase` como `PENDING` **antes** de chamar o
  pagamento: se o processo for morto com o app de pagamento em foreground, resta um registro ligado
  à `reference` para reconciliar o desfecho quando o retorno chegar.
- **`PaymentResultDispatcher` existe para a Activity.** A `MainActivity` recebe a Intent de retorno,
  mas não deve saber que o resultado vem num query param `response` em Base64. Ela só repassa a
  Intent; a decodificação mora em `:feature:payment:common`.

#### Visibilidade

Tudo que é usado apenas dentro do módulo é `internal`: **todas** as classes `Cielo*`, os `UiModel`s,
os `UiState`s, `Purchase` e `PurchaseRepository`. A superfície pública de cada feature são o módulo
Koin e as telas que o NavHost chama. As telas por isso não recebem o UiModel por parâmetro — ele é
`internal` e é resolvido dentro do Composable.

O Koin liga implementações `internal` a interfaces públicas
(`factory<StartPaymentUseCase> { CieloStartPaymentUseCase(...) }`), e os testes de cada módulo
enxergam os seus próprios `internal`.

#### Convention plugins

`build-logic/` é um build composto com `cielosmart.android.library`,
`cielosmart.android.library.compose` e `cielosmart.jvm.library`. Sem isso, os sete módulos repetiriam
o mesmo bloco `android { }`; com isso, o build file de um módulo de feature tem cinco linhas e mudar
a `compileSdk` é uma edição só.

### Prevenção de cobrança duplicada

Requisito explícito do case, resolvido em três camadas:

1. **Chave de idempotência.** A `reference` enviada à Cielo é um UUID gerado **uma única vez** por
   compra e reutilizado nas retentativas — a Cielo enxerga sempre o mesmo pedido lógico.
2. **Trava de reentrada.** `onPayClick()` é ignorado enquanto `isPaymentInFlight` for verdadeiro, e o
   botão fica desabilitado. Double tap não abre dois checkouts.
3. **Repositório idempotente.** `PurchaseRepository.recordResult()` ignora escritas sobre uma compra
   que já tem desfecho definitivo. Protege contra reentrega da mesma Intent de resposta pelo Android.

A compra também é gravada como `PENDING` **antes** de abrir o deep link: se o processo morrer com a
Cielo em foreground, ainda existe um registro associado à `reference` para reconciliar.

### Tratamento de erros

Nenhum caminho de erro deixa o usuário sem saber se foi cobrado:

| Situação | Tratamento |
| --- | --- |
| Cielo Smart/Emulador não instalado | `ActivityNotFoundException` vira `StartPaymentResult.Failed`; card de erro com a orientação de instalar. Não houve cobrança, então a retentativa reaproveita a `reference` |
| Cancelado pelo usuário (`code` 1) | Compra registrada como `CANCELLED`, comprovante exibe o motivo |
| Erro de pagamento/autenticação (`code` 3/4) | Compra registrada como `DENIED` com o motivo |
| `response` ausente, Base64 inválido, JSON inválido | `CieloPaymentError.INVALID_RESPONSE`. O parser **nunca lança** — uma exceção aqui deixaria a compra em limbo |
| Pedido sem transação | `CieloPaymentError.PAYMENT` |

## Bibliotecas externas e justificativas

| Biblioteca | Por quê |
| --- | --- |
| **Jetpack Compose** (BOM) + Material 3 | UI declarativa; Material 3 é o recomendado pelas boas práticas da própria Cielo |
| **Koin** | DI pedida no enunciado do exercício. Sem geração de código, configuração em um único módulo, e injeta parâmetros de runtime (`eventId`, `purchaseReference`) nos UiModels com `parametersOf` |
| **Navigation Compose** (type-safe) | Três rotas em uma Activity, com argumentos tipados via `@Serializable` em vez de strings |
| **kotlinx.serialization** | Serializa a requisição e navega o JSON de resposta da Cielo. Escolhida em vez de `org.json` porque funciona em testes unitários de JVM puros — `org.json` é stub fora do dispositivo |
| **Turbine** + **kotlinx-coroutines-test** | Testar `StateFlow`/`SharedFlow` dos UiModels de forma legível |
| **Gradle `kotlin-dsl`** (build-logic) | Convention plugins próprios, para os sete módulos não repetirem a configuração de Android/Compose |

Nenhuma biblioteca de mock (Mockito/MockK): as dependências são interfaces pequenas, então os fakes
são escritos à mão. Menos mágica no teste, mais legibilidade no code review.

## Testes automatizados

`./gradlew testDebugUnitTest` — 37 testes, distribuídos pelos módulos que eles cobrem:

**`:feature:payment:common`** — o protocolo da Cielo:

- **`CieloDeepLinkBuilderTest`** — esquema/host/params da URI, round-trip Base64, valores em
  centavos, propagação da `reference` e ausência do `paymentCode`.
- **`CieloStartPaymentUseCaseTest`** — a tradução de `PaymentOrder` para a requisição da Cielo e o
  mapeamento de `ActivityNotFoundException` para `PaymentError.APP_NOT_FOUND`.
- **`CieloResponseParserTest`** — pedido aprovado (com um recorte do payload real da documentação),
  cada código de erro 1–4, `statusCode` 2 como cancelamento, pedido sem transação, resposta
  ausente/malformada/não-JSON sem lançar exceção, e a descrição da forma de pagamento escolhida no
  terminal (formato da documentação, formato do emulador, fallback e ausência).

**`:feature:checkout:common`** — a lógica do checkout, com dublês de `payment:core`
(`FakeStartPaymentUseCase`, `FakePaymentResultSource`). Estes testes **não conhecem a Cielo**: para
conferir a `reference`, leem o `PaymentOrder` que o checkout enviou ao dublê — testam a regra de
negócio, não o formato do fio, que é coberto pelos testes de `:feature:payment:common`.

- **`CheckoutUiModelTest`** — quantidade limitada entre 1 e 10, recálculo do total, `PaymentOrder`
  enviado com a quantidade e o valor corretos, compra gravada como `PENDING` antes de iniciar o
  pagamento, **o segundo toque em Pagar é ignorado**, **a retentativa reusa a mesma `reference`**
  (tanto após falha ao abrir quanto após voltar sem desfecho), **voltar do app de pagamento sem
  retorno libera a tela**, aprovação e cancelamento registrados e navegando para o comprovante, e
  **resultado repetido não altera uma compra já concluída**.
- **`CheckoutUiModelResumeRaceTest`** — a corrida entre o retorno do pagamento e o `ON_RESUME` da
  tela: com um desfecho publicado e ainda não processado a tela segue aguardando, e um retorno sem
  compra correspondente não trava liberações futuras.
- **`PurchaseRepositoryTest`** — idempotência de `start` e `recordResult`, mapeamento de
  cancelamento/recusa, resultado para referência desconhecida.

**`:feature:shop:common`**

- **`EventListUiModelTest`** — transição de carregamento para conteúdo e ação de navegação.

O fluxo completo também foi validado ponta a ponta contra o **Emulador Cielo** real, nos cenários de
sucesso e de cancelamento.

## Uso de IA

O case pede a documentação do harness do agente e do "como" a IA foi usada. Está em
[docs/AI_USAGE.md](docs/AI_USAGE.md).

## Trade-offs considerados

- **Deep link em vez do Order Manager remoto.** O modelo local é o adequado para um app que roda no
  terminal e dispensa SDK e backend. Em troca, o app depende de ter a Cielo instalada no dispositivo
  e não consegue consultar o pedido pelo servidor. O `<queries>` e o tratamento de
  `ActivityNotFoundException` cobrem a ausência do app.
- **Persistência em memória.** O enunciado deixa o banco livre e não avalia backend. Um
  `ConcurrentHashMap` mantém o exercício focado no fluxo de pagamento e na idempotência. O custo é
  real: se o Android matar o processo enquanto a Cielo está em foreground, as compras pendentes se
  perdem. O `PurchaseRepository` já tem a interface certa para persistir com SQLite sem tocar nos UiModels.
- **Serviço em primeiro plano não implementado.** A documentação da Cielo recomenda um foreground
  service durante o pagamento, para o Android não matar o app de integração enquanto ele está em
  background. Deixei de fora para não inflar o exercício, mas mitiguei a consequência: a compra é
  gravada antes do deep link e o `CieloResultBus` usa `replay = 1`, então uma resposta que chegue
  antes do coletor não se perde.
- **Sem testes instrumentados.** A lógica crítica (idempotência, protocolo, máquina de estados) está
  toda em código testável na JVM, que roda rápido.
- **`MainDispatcherRule` duplicada.** A regra de 15 linhas existe em `:feature:checkout:common` e em
  `:feature:shop:common`. A alternativa seria um módulo `:core:testing` só para ela — para dois
  arquivos idênticos e pequenos, a duplicação me pareceu mais barata que mais um módulo. Se um
  terceiro módulo precisar da regra, vale extrair.

## O que faria com mais tempo

1. **Persistência com SQLite** e uma tela de histórico de compras, resolvendo a perda de estado na
   morte do processo.
2. **Credenciais fora do código**, vindo de `local.properties`/BuildConfig ou de um backend, em vez
   de constantes.
3. **Um segundo `feature:payment:*`** — nem que fosse um mock — para provar na prática que o
   contrato `payment:core` aguenta outra adquirente, e um teste de arquitetura (Konsist ou similar)
   que falhe o build se algum módulo passar a importar `com.example.payment.cielo`.
4. **Observabilidade** — Crashlytics e métricas de conversão do funil de pagamento.
