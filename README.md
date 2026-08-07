# App de Venda de Ingressos com Cielo Smart

App Android de venda de ingressos para eventos locais, com pagamento pelo ecossistema
**Cielo Smart / Cielo LIO** via integração local por deep link.

Fluxo: **listar eventos → escolher quantidade e forma de pagamento → pagar na Cielo → registrar o
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
./gradlew :app:testDebugUnitTest
```

### Testando o pagamento com o Emulador Cielo

O app **não** embute nenhum SDK da Cielo: ele conversa com o app de integração da Cielo instalado no
mesmo dispositivo. Para transacionar sem um terminal físico:

1. Baixe o **Emulador Cielo** em
   https://docs.cielo.com.br/cielo-smart/docs/baixando-o-emulador-cielo
2. Instale-o no mesmo AVD do app (o emulador é suportado até o Android 10; não deve ser instalado em
   um terminal Smart real).
3. Abra o app, escolha um evento, a quantidade e a forma de pagamento e toque em **Pagar com Cielo**.
   O Emulador Cielo abre com o valor já preenchido e permite simular **Sucesso**, **Cancelado** ou
   **Erro**.

Também é possível simular a resposta da Cielo sem o emulador, entregando a Intent de retorno na mão:

```bash
adb shell am start -a android.intent.action.VIEW -d "order://response?response=<json-em-base64>&responsecode=0" com.example.cielo
```

### Credenciais

`clientID` e `accessToken` estão **mockados** em
[CieloCredentials.kt](app/src/main/java/com/example/cielo/cielo/CieloCredentials.kt)
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

O JSON carrega `accessToken`, `clientID`, `reference`, `items[]`, `paymentCode` e `value` — todos os
valores monetários em **centavos inteiros**. Montado por
[CieloDeepLinkBuilder](app/src/main/java/com/example/cielo/cielo/CieloDeepLinkBuilder.kt).

**2. Manifest.** Três configurações obrigatórias em
[AndroidManifest.xml](app/src/main/AndroidManifest.xml):

- `<queries>` declarando o pacote da Cielo (obrigatório no Android 11+ para enxergar e chamar o app
  de integração). Além do `com.ads.lio.uriappclient` da documentação, declaramos também o
  `br.com.cielosmart.orderservice`, usado pelo Emulador Cielo, e uma consulta por intent no esquema
  `lio://` para não depender do nome do pacote;
- `<meta-data android:name="cs_integration_type" android:value="uri" />`, que identifica o app como
  integração por URI;
- o **contrato de resposta**: um `intent-filter` de `ACTION_VIEW` para `order://response` na
  `MainActivity`, idêntico ao `urlCallback` enviado na requisição.

**3. Resposta.** A Cielo devolve o resultado abrindo `order://response?response=<base64>` como uma
**nova Intent**. Como o app tem uma única Activity, ela é declarada `launchMode="singleTop"`: a
Intent chega em `onNewIntent` sem recriar a Activity, e os UiModels que aguardam o pagamento
sobrevivem. A `MainActivity` extrai o parâmetro `response` e publica no
[CieloResultBus](app/src/main/java/com/example/cielo/cielo/CieloResultBus.kt); o
[CheckoutUiModel](app/src/main/java/com/example/cielo/checkout/CheckoutUiModel.kt) consome, e o
[CieloResponseParser](app/src/main/java/com/example/cielo/cielo/CieloResponseParser.kt) interpreta.

O payload de sucesso é o pedido pago, com `payments[].authCode`, `cieloCode`, `mask`, `terminal` e
`paymentFields.statusCode` (`0` Pix, `1` autorizada, `2` cancelada). O de erro é
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

**Detalhe não óbvio, descoberto testando no emulador:** durante o pagamento o app da Cielo fica em
foreground e a tela de checkout vai para `STOPPED`. Uma ação emitida nesse intervalo em um
`SharedFlow` sem replay se perde — o app registrava a compra mas não navegava para o comprovante. Os
`SharedFlow` de ações usam `replay = 1` e a UI confirma o consumo em `onActionHandled()`, o que
entrega a ação quando a tela volta sem renavegar em recoletas posteriores.

### Uma única Activity

Além de ser a arquitetura moderna recomendada, aqui há um motivo concreto: a `MainActivity` é também
o ponto de entrada do `order://response`. Com `singleTop`, o retorno da Cielo não destrói nada.

### Organização por feature

`event/`, `checkout/`, `purchase/`, `cielo/`, `di/`, `ui/`. A pasta `cielo/` isola **todo** o
conhecimento sobre o protocolo da Cielo; o resto do app fala em `Event`, `Purchase` e
`CieloPaymentResult`. Trocar o deep link pela integração remota do Order Manager mexeria só nessa
pasta.

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

Nenhuma biblioteca de mock (Mockito/MockK): as dependências são interfaces pequenas, então os fakes
são escritos à mão. Menos mágica no teste, mais legibilidade no code review.

## Testes automatizados

`./gradlew :app:testDebugUnitTest` — 26 testes cobrindo os cenários críticos:

- **`CieloDeepLinkBuilderTest`** — esquema/host/params da URI, round-trip Base64, valores em
  centavos, propagação da `reference`, cada `paymentCode`.
- **`CieloResponseParserTest`** — pedido aprovado (com um recorte do payload real da documentação),
  cada código de erro 1–4, `statusCode` 2 como cancelamento, pedido sem transação, e resposta
  ausente/malformada/não-JSON sem lançar exceção.
- **`CheckoutUiModelTest`** — quantidade limitada entre 1 e 10, recálculo do total, deep link com a
  quantidade e o meio de pagamento corretos, compra gravada como `PENDING` antes do checkout, **o
  segundo toque em Pagar é ignorado**, **a retentativa reusa a mesma `reference`**, aprovação e
  cancelamento registrados e navegando para o comprovante, e **resposta repetida não altera uma
  compra já concluída**.
- **`PurchaseRepositoryTest`** — idempotência de `start` e `recordResult`, mapeamento de
  cancelamento/recusa, resultado para referência desconhecida.
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
  perdem. O `PurchaseRepository` já tem a interface certa para virar Room sem tocar nos UiModels.
- **Sem QR Code.** É opcional no case; o comprovante já vincula o ingresso à compra concluída pela
  `reference` e pelos dados da transação.
- **Serviço em primeiro plano não implementado.** A documentação da Cielo recomenda um foreground
  service durante o pagamento, para o Android não matar o app de integração enquanto ele está em
  background. Deixei de fora para não inflar o exercício, mas mitiguei a consequência: a compra é
  gravada antes do deep link e o `CieloResultBus` usa `replay = 1`, então uma resposta que chegue
  antes do coletor não se perde.
- **Sem testes instrumentados.** A lógica crítica (idempotência, protocolo, máquina de estados) está
  toda em código testável na JVM, que roda rápido. Testes de UI em Compose cobririam a camada mais
  fina e mais volátil.

## O que faria com mais tempo

1. **Persistência com Room** e uma tela de histórico de compras, resolvendo a perda de estado na
   morte do processo.
2. **Foreground service** durante o pagamento, como recomenda a Cielo, e reconciliação ativa de
   compras `PENDING` na volta do app (a integração de listagem de pedidos, `lio://orders`, permite
   consultar o desfecho de um pedido cuja resposta se perdeu).
3. **Cancelamento/estorno** via `lio://payment-reversal`, que reaproveita quase toda a infraestrutura
   já existente.
4. **QR Code do ingresso** vinculado à compra aprovada, e impressão do comprovante pelo terminal via
   deep link de impressão.
5. **Credenciais fora do código**, vindo de `local.properties`/BuildConfig ou de um backend, em vez
   de constantes.
6. **Testes de UI em Compose** e um teste instrumentado que valide o contrato do `intent-filter` de
   `order://response`.
7. **Observabilidade** — Crashlytics e métricas de conversão do funil de pagamento, como sugerem as
   boas práticas da Cielo.
