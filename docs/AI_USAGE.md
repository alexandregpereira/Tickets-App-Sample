# Uso de IA na construção da solução

O case pede a documentação do harness do agente: specs, decisões arquiteturais, uso da IA (prompts e
restrições) e os resultados que orientaram a implementação. Este documento registra isso.

## Harness

- **Ferramenta:** Claude Code (Opus 5), rodando com acesso ao repositório, ao Gradle, ao `adb` e à
  web.
- **Modo de trabalho:** um ciclo de *planejar → confirmar → implementar → verificar*. O agente
  primeiro produziu um plano escrito, levantou as decisões em aberto, e só começou a escrever código
  depois da aprovação.
- **Verificação:** o agente compilou, rodou os testes e exercitou o app no emulador Android com o
  Emulador Cielo instalado, lendo screenshots e `logcat` para confirmar o comportamento.

## Specs de entrada

Duas fontes, ambas lidas pelo agente e não parafraseadas de memória:

1. **O enunciado** (`Case Android.pdf`), do qual saíram os requisitos funcionais (5 fluxos) e não
   funcionais (tratamento de erro, prevenção de duplicidade, testes, README).
2. **A documentação oficial da Cielo Smart**, buscada a partir de
   `https://docs.cielo.com.br/cielo-smart/docs/conheca-a-cielo-smart` e do índice
   `llms.txt`. As páginas efetivamente usadas: `modelos-de-integracao`,
   `deep-link-exemplo-de-codigo`, `pagamento`, `recuperando-dados`,
   `configurando-o-android-manifest`, `codigos-de-erro`,
   `valores-aceitos-no-campo-paymentcode`, `autenticacao-e-credenciais`, `listagem-de-pedidos`,
   `cancelamento`, `servico-em-primeiro-plano` e `integracao-via-deep-link`.

O JSON de exemplo, os códigos de erro e o payload de resposta usados nos testes vêm literalmente
dessa documentação, não de suposição.

## Restrições impostas ao agente

Restrições de arquitetura definidas antes da implementação:

- Kotlin + Jetpack Compose para toda a UI; **nenhum XML de layout**.
- **Uma única Activity**, que inicia o Compose.
- **Koin** para injeção de dependência.
- **MVI**: a UI conversa com um `UiModel` que detém o `UiState`; estado por `StateFlow`, ações por
  `SharedFlow`, intenções enviadas por **funções públicas** do `UiModel`.
- Um `UseCase` com `suspend operator fun invoke()` devolvendo uma lista mockada de eventos.
- UI simples: uma tela de listagem e outra de seleção de quantidade.
- `clientID` e `accessToken` mockados, para serem preenchidos depois.

## Decisões deixadas para o humano

O agente não escolheu sozinho o escopo. Antes de codar, apresentou as opções em aberto e a
implementação seguiu as respostas:

| Pergunta | Decisão |
| --- | --- |
| Como atender aos requisitos 4 e 5 (registrar desfecho e exibir comprovante) com 2 telas? | Adicionar uma **terceira tela** de comprovante |
| QR Code e/ou persistência com Room? | **Nenhum dos dois** — repositório em memória |
| Como escolher o `paymentCode`? | O app **não envia** o campo: a forma de pagamento é escolhida na tela da própria Cielo Smart |
| Quão longe ir nos testes? | Testes unitários de UiModel, builder do deep link, parser e guarda de duplicidade; **sem** testes instrumentados |

## Resultados que orientaram a implementação

Três achados da verificação mudaram o código — vale registrar porque nenhum deles apareceria sem
executar de verdade:

**1. A ação de navegação se perdia no retorno da Cielo.** No primeiro teste ponta a ponta contra o
Emulador Cielo, o pagamento era aprovado (o `logcat` mostrava `status: PAID` e `statusCode: 1`), a
compra era registrada, mas o app não navegava para o comprovante. Causa: durante o pagamento o app da
Cielo fica em foreground, a tela de checkout vai para `STOPPED` e o coletor de ações é desmontado;
a ação emitida nesse intervalo num `SharedFlow` sem replay era descartada. Correção: `replay = 1` nos
`SharedFlow` de ações, com confirmação de consumo pela UI (`onActionHandled()`).

**2. Visibilidade de pacote incompleta.** O `logcat` acusava
`AppsFilter: ... com.example.cielo -> br.com.cielosmart.orderservice BLOCKED`. A documentação só cita
`com.ads.lio.uriappclient` no `<queries>`, mas o Emulador Cielo usa outro pacote. O manifest passou a
declarar os dois pacotes **e** uma consulta por intent no esquema `lio://`, que não depende do nome do
pacote.

**3. Tela presa ao voltar da Cielo.** Sair da Cielo Smart pelo botão voltar não gera callback nenhum,
então o checkout ficava para sempre em "aguardando pagamento", com o botão desabilitado. Correção:
liberar a tela no `ON_RESUME` — mas só quando não houver um retorno publicado e ainda não processado,
porque `onNewIntent` e `onResume` acontecem na mesma passagem pela main thread e liberar sem essa
guarda faria a tela piscar de volta ao estado ocioso um instante antes de navegar para o comprovante.

Os cenários de aprovação, cancelamento e volta sem callback foram validados ponta a ponta contra o
Emulador Cielo, e os 35 testes unitários passam.

## O que a IA *não* decidiu

- A arquitetura (MVI, Koin, Compose, uma Activity) foi imposta, não sugerida.
- O escopo (3 telas, sem Room, sem QR, sem seletor de pagamento, profundidade dos testes) foi escolhido
  pelo humano a partir das opções apresentadas.
- O protocolo da Cielo não foi inventado: cada campo, código de erro e requisito de manifest tem uma
  página da documentação oficial como origem, citada no código e no README.
