# App de Venda de Ingressos com Cielo Smart

Projeto para explorar na prática a integração de pagamento do ecossistema **Cielo Smart / Cielo LIO**
em um app Android, usando a integração local por deep link.

O app vende ingressos para eventos locais e percorre o fluxo completo: **listar eventos → escolher a
quantidade → pagar na Cielo → registrar o desfecho → exibir o comprovante**.

## Como executar

**Pré-requisitos:** JDK 21, Android SDK com a plataforma 36 (`compileSdk = 36`, `minSdk = 24`) e um
dispositivo ou AVD.

```bash
./gradlew :app:installDebug
```

```bash
./gradlew testDebugUnitTest
```

### Testando o pagamento com o Emulador Cielo

O app **não** embute nenhum SDK da Cielo: ele conversa com o app de integração instalado no mesmo
dispositivo. Para transacionar sem um terminal físico, baixe o
[Emulador Cielo](https://docs.cielo.com.br/cielo-smart/docs/baixando-o-emulador-cielo) e instale-o no
mesmo AVD (suportado até o Android 10; não deve ser instalado em um terminal Smart real). Ao tocar em
**Pagar**, o emulador abre com o valor preenchido e permite simular **Sucesso**, **Cancelado** ou
**Erro**.

Também dá para entregar a Intent de retorno na mão, sem o emulador:

```bash
adb shell am start -a android.intent.action.VIEW -d "order://response?response=<json-em-base64>&responsecode=0" com.example.cielo
```

### Credenciais

`clientID` e `accessToken` estão **mockados** em
[CieloCredentials.kt](feature/payment/common/src/main/java/com/example/payment/cielo/CieloCredentials.kt).
Para valer em produção, substitua pelos valores obtidos no
[Portal de Desenvolvedores da Cielo](https://desenvolvedores.cielo.com.br/api-portal/) cadastrando um
app com a API **Cielo Smart - Order Manager**. O Emulador Cielo aceita os valores mockados.

## A integração com a Cielo Smart

A Cielo Smart oferece integração **remota** (APIs do Order Manager, para PDV) e **local via deep link**
(intents Android, para apps que rodam no próprio terminal). Este app usa a local, que é a indicada
para venda no terminal e dispensa SDK.

**1. Requisição.** O pedido vira JSON, é convertido para Base64 e enviado numa Intent `ACTION_VIEW`:

```
lio://payment?request=<base64>&urlCallback=order://response
```

O JSON carrega `accessToken`, `clientID`, `reference`, `items[]` e `value` — todos os valores
monetários em **centavos inteiros**. Montado por
[CieloDeepLinkBuilder](feature/payment/common/src/main/java/com/example/payment/cielo/CieloDeepLinkBuilder.kt).

**2. Manifest.** Três configurações obrigatórias, todas em
[feature/payment/common](feature/payment/common/src/main/AndroidManifest.xml) e unidas ao manifesto do
app pelo merge do AGP — o `:app` não declara nada de pagamento:

- `<queries>` declarando o pacote da Cielo (obrigatório no Android 11+ para enxergar o app de
  integração). Além do `com.ads.lio.uriappclient` da documentação, declaramos o
  `br.com.cielosmart.orderservice` usado pelo Emulador Cielo e uma consulta por intent no esquema
  `lio://`, que não depende do nome do pacote;
- `<meta-data android:name="cs_integration_type" android:value="uri" />`;
- o **contrato de resposta**: um `intent-filter` de `ACTION_VIEW` para `order://response` na
  `PaymentActivity`, idêntico ao `urlCallback` enviado. Os dois lados moram no mesmo módulo
  (`CieloDeepLinkBuilder.CALLBACK_URI` e o manifesto), então não há como um mudar sem o outro.

**3. Resposta.** A Cielo devolve o resultado abrindo `order://response?response=<base64>` como uma
**nova Intent**, entregue à
[PaymentActivity](feature/payment/common/src/main/java/com/example/payment/cielo/PaymentActivity.kt)
(`singleTop`, então chega em `onNewIntent` sem recriar nada). Ela encerra devolvendo o payload pela
Activity Result API, e o
[CieloResponseParser](feature/payment/common/src/main/java/com/example/payment/cielo/CieloResponseParser.kt)
traduz os campos da Cielo (`payments[].authCode`, `cieloCode`, `mask`, `terminal`,
`paymentFields.statusCode`) para o vocabulário agnóstico do contrato — nenhum nome da Cielo atravessa
a fronteira do módulo. É de lá que sai também a **forma de pagamento escolhida no terminal**, exibida
no comprovante: como o app não envia `paymentCode`, `primaryProductName` + `secondaryProductName` são
a única fonte dessa informação (`productName` é só fallback, porque o Emulador devolve um mock fixo
nele). O payload de erro é `{"code": N, "reason": "..."}`, com `1` cancelado pelo usuário, `2`
genérico, `3` erro de pagamento e `4` erro de autenticação.

Referências: [Pagamento](https://docs.cielo.com.br/cielo-smart/docs/pagamento) ·
[Recuperando dados](https://docs.cielo.com.br/cielo-smart/docs/recuperando-dados) ·
[Android Manifest](https://docs.cielo.com.br/cielo-smart/docs/configurando-o-android-manifest) ·
[Códigos de erro](https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro)

## Decisões arquiteturais

### MVI com UiModel + StateFlow

Cada tela tem um `UiModel` (um `ViewModel`) que expõe um `StateFlow<UiState>` com o estado completo e
imutável e um `SharedFlow<UiAction>` para efeitos únicos. As intenções vão da UI para o UiModel por
**funções públicas** (`onPayClick()`, `onIncreaseQuantityClick()`), sem um `sealed class Intent`
intermediário. O `UiState` já traz os derivados prontos (`totalInCents`, `canPay`), o que mantém os
Composables burros.

**Detalhe que só aparece rodando:** durante o pagamento o checkout vai para `STOPPED`, porque outra
tela assume o primeiro plano, e uma ação emitida nesse intervalo num `SharedFlow` sem replay se
perderia — a compra seria registrada, mas a tela nunca navegaria para o comprovante. Por isso as ações
usam `replay = 1` e a UI confirma o consumo em `onActionHandled()`.

### Uma Activity para a UI, uma para o pagamento

Toda a UI vive numa `MainActivity` só, com Compose e navegação — ela não sabe que pagamento existe. A
exceção é a **`PaymentActivity`**, dentro de `feature:payment:common`: uma tela translúcida sem
conteúdo que abre o app da Cielo, é dona do `intent-filter` de `order://response` e devolve o desfecho
pela Activity Result API. É ela que permite ao resto do app tratar cobrança como uma chamada que
suspende e devolve um resultado — sem barramento, sem `onNewIntent` espalhado, sem estado "aguardando
retorno" vivendo no checkout.

A parte difícil é **distinguir "a adquirente respondeu" de "o usuário voltou sem concluir"**, porque a
segunda não gera callback nenhum. A distinção é por ciclo de vida: desistência só vale num `onResume`
que venha depois de a tela ter sido pausada **e** que se sustente por um instante. O app da Cielo troca
de tela durante o fluxo, e nessas transições a `PaymentActivity` chega a resumir por um piscar —
concluir desistência no primeiro `onResume` matava pagamentos aprovados.

O segundo detalhe é **sobreviver à recriação da Activity**. Girar o dispositivo dentro do app da Cielo
recria a `MainActivity`: o `ActivityResultRegistry` guarda o resultado pendente, mas o *callback* morre
com a instância antiga, e a corrotina ficaria suspensa para sempre. O `PaymentResultLauncher` revincula
a chave de cada pagamento em andamento a toda Activity nova, e registrar a chave faz o registry
entregar na hora o resultado guardado. A regra de "uma vez por instância, de novo a cada instância"
mora em `PaymentBindings`, sem tipos do Android, coberta por testes de JVM.

O `replay = 1` tem uma armadilha própria aqui: proteger a *emissão* não basta, porque o replay
reentrega a ação a cada coletor novo — a `PaymentActivity` recriada recebia o `OpenDeepLink` antigo e
abria o app da Cielo uma segunda vez. A correção é a mesma do checkout: confirmar o consumo em
`onActionHandled()`, que limpa o replay.

### Arquitetura multi-módulo

O app é dividido em módulos Gradle para que a fronteira com a Cielo seja **garantida pelo compilador**,
e não por disciplina: `:feature:checkout:common` depende de `:feature:payment:core`, nunca de
`:feature:payment:common`, então importar `CieloResponseParser` no checkout não compila.

```
:app  ──────────────► todos (apenas raiz de composição)
 │
 ├─► :feature:shop:common ──────► :feature:shop:core
 ├─► :feature:checkout:common ──► :feature:shop:core
 │                             ─► :feature:payment:core
 └─► :feature:payment:common ──► :feature:payment:core   ← ÚNICO módulo que conhece a Cielo

 :ui  e  :core:money   ← folhas compartilhadas, usadas por várias features
```

| Módulo | Tipo | Conteúdo |
| --- | --- | --- |
| `:app` | app Android | `MainActivity`, `TicketsNavHost`, `startKoin` |
| `:ui` | lib Android | tema e `CollectUiActions` |
| `:core:money` | **Kotlin/JVM** | `formatAsBrl` |
| `:feature:shop:core` | **Kotlin/JVM** | `Event`, `GetEventUseCase` (interface) |
| `:feature:shop:common` | lib Android | catálogo mockado, listagem, `shopModule` |
| `:feature:checkout:common` | lib Android | checkout, `Purchase`/`PurchaseRepository`, comprovante |
| `:feature:payment:core` | **Kotlin/JVM** | contrato de pagamento agnóstico |
| `:feature:payment:common` | lib Android | tudo que é Cielo + implementações, `paymentModule` |

Os módulos `core` são **Kotlin/JVM puro**: carregam contratos e tipos de domínio, que não precisam de
framework, e a escolha é auto-verificável — com o plugin JVM, um `import android.*` ali não compila.
Na prática também sai mais barato (geram JAR, sem manifesto, sem AAR).

O contrato de pagamento cabe em três arquivos — `PaymentOrder`, `PaymentResult` e:

```kotlin
fun interface StartPaymentUseCase { suspend operator fun invoke(order: PaymentOrder): PaymentResult }
```

- **Uma chamada, um resultado.** Cobrar suspende até o desfecho, mesmo com um app externo no meio do
  caminho. Quem chama não observa canal nenhum nem sabe que existe uma Activity ali.
- **`StartPaymentUseCase` não grava a compra.** Ele fala só de `PaymentOrder` e não conhece `Event`,
  `Purchase` nem `PurchaseRepository`. Quem registra é o `CheckoutUiModel`, que grava a compra como
  `PENDING` **antes** de chamar o pagamento: se o processo morrer com a Cielo em foreground, resta um
  registro ligado à `reference` para reconciliar.
- **`PaymentError.ABANDONED` distingue "desistiu" de "foi recusado".** Voltar sem concluir não é um
  cancelamento da adquirente: nada foi cobrado, então o checkout descarta a compra e libera a tela sem
  comprovante. Um `CANCELLED_BY_USER` vindo da Cielo, esse sim, vira compra cancelada e comprovante.

Tudo que é usado apenas dentro do módulo é `internal`: todas as classes `Cielo*`, os `UiModel`s, os
`UiState`s, `Purchase` e `PurchaseRepository`. A superfície pública de cada feature são o módulo Koin e
as telas que o NavHost chama — por isso as telas não recebem o UiModel por parâmetro, ele é resolvido
dentro do Composable. `build-logic/` é um build composto com três convention plugins, para os sete
módulos não repetirem o mesmo bloco `android { }`.

### Prevenção de cobrança duplicada

1. **Trava de reentrada.** `onPayClick()` é ignorado enquanto houver pagamento em andamento e o botão
   fica desabilitado. Double tap não abre dois checkouts.
2. **Uma tentativa, uma `reference`, uma compra.** Cada tentativa gera o seu UUID, enviado à Cielo e
   usado como identificador da compra. Uma tentativa abandonada é descartada por inteiro no início da
   seguinte, então o pedido enviado reflete sempre o que está na tela.
3. **Repositório idempotente.** `recordResult()` ignora escritas sobre uma compra que já tem desfecho
   definitivo e `discard()` se recusa a remover uma: protege contra reentrega da mesma Intent pelo
   Android e garante que uma venda registrada não some.

**Ordenação que custou um bug:** o descarte da tentativa abandonada acontece no início do próximo
`onPayClick()`, e não quando o usuário volta. O Android pode entregar o `ON_RESUME` **antes** da Intent
de retorno; zerar a `reference` ali fazia o desfecho legítimo que já estava a caminho ser descartado.

### Tratamento de erros

| Situação | Tratamento |
| --- | --- |
| Cielo Smart/Emulador não instalado | `ActivityNotFoundException` vira `PaymentError.APP_NOT_FOUND`; card com a orientação de instalar. O checkout nem abriu, então a tentativa é descartada por inteiro |
| Usuário volta sem concluir | `PaymentError.ABANDONED`: compra descartada, tela liberada, **sem** comprovante e sem mensagem |
| Cancelado pelo usuário (`code` 1) | Compra registrada como `CANCELLED`, comprovante exibe o motivo |
| Erro de pagamento/autenticação (`code` 3/4) | Compra registrada como `DENIED` com o motivo |
| `response` ausente, Base64 ou JSON inválido | `CieloPaymentError.INVALID_RESPONSE`. O parser **nunca lança** — uma exceção aqui deixaria a compra em limbo |
| Pedido sem transação | `CieloPaymentError.PAYMENT` |

## Bibliotecas externas

| Biblioteca | Por quê |
| --- | --- |
| **Jetpack Compose** (BOM) + Material 3 | UI declarativa; Material 3 é o recomendado pelas boas práticas da própria Cielo |
| **Koin** | DI sem geração de código, configuração em um módulo por feature, e injeta parâmetros de runtime (`eventId`, `purchaseReference`) nos UiModels com `parametersOf` |
| **Navigation Compose** (type-safe) | Três rotas em uma Activity, com argumentos tipados via `@Serializable` em vez de strings |
| **kotlinx.serialization** | Serializa a requisição e navega o JSON de resposta. Escolhida em vez de `org.json` porque funciona em testes de JVM puros — `org.json` é stub fora do dispositivo |
| **Turbine** + **kotlinx-coroutines-test** | Testar `StateFlow`/`SharedFlow` dos UiModels de forma legível |
| **Gradle `kotlin-dsl`** (build-logic) | Convention plugins próprios, para os módulos não repetirem a configuração de Android/Compose |

Nenhuma biblioteca de mock: as dependências são interfaces pequenas, então os fakes são escritos à
mão. Menos mágica no teste, mais legibilidade.

## Limitações conhecidas

- **Persistência em memória.** Um `ConcurrentHashMap` mantém o foco no fluxo de pagamento e na
  idempotência, mas se o Android matar o processo enquanto a Cielo está em foreground as compras
  pendentes se perdem. O `PurchaseRepository` já tem a interface certa para persistir sem tocar nos
  UiModels.
- **Serviço em primeiro plano não implementado.** A Cielo recomenda um foreground service durante o
  pagamento, para o Android não matar o app de integração. A consequência está mitigada: a compra é
  gravada antes de abrir o pagamento e o desfecho volta pela Activity Result API — entrega única, sem
  janela em que um retorno se perca por falta de quem o escute.
- **Sem testes instrumentados.** A lógica crítica (idempotência, protocolo, máquina de estados) é
  testável na JVM. O preço é que o comportamento da `PaymentActivity` — ordem de ciclo de vida,
  revinculação após recriação — só é garantido rodando no emulador.
- **`MainDispatcherRule` duplicada** em `:feature:checkout:common` e `:feature:shop:common`. Para dois
  arquivos idênticos de 15 linhas, a duplicação pareceu mais barata que um módulo `:core:testing`.

## Próximos passos

1. **Persistência com SQLite** e uma tela de histórico, resolvendo a perda de estado na morte do
   processo.
2. **Credenciais fora do código**, vindo de `local.properties`/BuildConfig ou de um backend.
3. **Um segundo `feature:payment:*`** — nem que fosse um mock — para provar que o contrato
   `payment:core` aguenta outra adquirente, e um teste de arquitetura que falhe o build se algum módulo
   passar a importar `com.example.payment.cielo`.
4. **Observabilidade** — Crashlytics e métricas de conversão do funil de pagamento.
