# Roteiro Técnico e Estruturado de Melhorias — Minhas Ofertas (v1.0.13)

**Versão da Base de Código Auditada:** `v1.0.13` (`versionCode 13`, `compileSdk 35`, `targetSdk 35`)  
**Data da Auditoria:** Novembro de 2026  
**Paradigma Arquitetural Obrigatório:** 100% On-Device (Zero Backend Intermediário, Zero Banco de Dados em Nuvem)  
**Padrão de Engenharia de Referência:** Modern Android Development (MAD) e Documentação Oficial TDLib  

---

## 1. Sumário Executivo

O aplicativo **Minhas Ofertas** é uma solução Android nativa desenvolvida em **Kotlin 2.0.21** e **Jetpack Compose (BOM 2024.10.01)**, projetada para operar no modelo estritamente **100% On-Device**. Ele se comunica diretamente com a infraestrutura distribuída do Telegram através da biblioteca nativa **TDLib (`io.github.tdlib-android:core:0.1.1`)** via chamadas JNI/C++, monitorando 14 canais públicos de promoções em tempo real, sem intermediação de servidores proprietários.

A auditoria técnica da versão `v1.0.13` identificou que, embora a proposta de valor do produto seja sólida e a interface em Material Design 3 apresente apelo visual, a fundação técnica, a arquitetura de dados e os mecanismos de parsing apresentam fragilidades críticas:
1. **Dados em RAM e Perda Total de Histórico:** O aplicativo não implementa banco de dados local (Room ou SQLite nativo). Todas as ofertas são acumuladas em um `MutableStateFlow` em memória RAM (`OfferRepository.kt:8`), sendo descartadas a cada encerramento do processo pelo sistema operacional.
2. **Ciclo de Vida TDLib Desconectado e Serviço Zumbi:** O cliente nativo da TDLib é mantido na `MainActivity` (`MainActivity.kt:36, 109`). O `TelegramMonitorService` roda em segundo plano com um bloco de execução vazio (`TelegramMonitorService.kt:37-39`), tornando-se um processo "zumbi" que exibe notificação persistente na barra de status mas não monitora mensagens quando a tela é rotacionada ou o app é fechado.
3. **Falhas no Motor de Parsing e Quebra de Agrupamento:** O parser ignora hiperlinks do Telegram (`TdApi.TextEntityTypeTextUrl`), perde links de afiliados em palavras-âncora, possui um bug que seleciona frete ou cashback como o menor preço do produto (`PriceParser.kt:75`) e quebra o agrupamento entre canais ao lidar com encurtadores (`amzn.to`).
4. **Gargalos de UI Thread e Inestabilidade no Compose:** Funções de alto custo computacional (`groupOffers`) e acessos bloqueantes ao disco (`File.exists()`) são executados sincronamente na Main Thread durante a renderização do Compose (`MainScreen.kt:64, 291`), causando stutter e quebra de 60/120 FPS.
5. **Alertas Locais Inativos:** A rotina de notificações locais de ofertas (`TelegramMonitorService.notifyNewOffer`) existe mas nunca é invocada no fluxo de mensagens, operando em canal de prioridade baixa sem som ou deep link.

Este documento consolida a auditoria técnica exaustiva e estabelece o **Roadmap Estruturado de Melhorias**, categorizado em 4 eixos balanceados e priorizado em uma matriz de esforço versus impacto.

---

## 2. Diagnóstico da Base de Código Atual (v1.0.13)

### 2.1. Inventário de Componentes e Estrutura de Pacotes

O módulo `:app` é composto por 13 classes e arquivos Kotlin principais, mapeados a seguir:

| Pacote / Arquivo | Componente / Classe | Papel Atual | Principais Fragilidades Diagnosticadas |
|---|---|---|---|
| `android/app/build.gradle.kts` | Configuração Gradle | SDKs (35), ABIs (`arm64-v8a`, `armeabi-v7a`), Compose BOM e dependências | Ausência de Room, DataStore, WorkManager e frameworks de teste (`src/test` vazio). |
| `android/app/src/main/AndroidManifest.xml` | Manifesto Android | Permissões, Activities e Foreground Service (`dataSync`) | Ausência de intent-filters para deep link; permissão `ACCESS_NETWORK_STATE` declarada sem uso. |
| `com.minhasofertas.app.MainActivity` | Activity Principal | Entry point, ciclo de vida do `TelegramManager` e renderização de telas | Acúmulo de responsabilidades de UI, TDLib e permissões; ausência de `ViewModel`. |
| `com.minhasofertas.app.telegram.TelegramManager` | Gerenciador TDLib | Comunicação JNI com TDLib, auth OTP e parsing de mensagens | Recriação sem esperar `AuthorizationStateClosed`; mapa não thread-safe; download de imagens 8MB em prioridade 32. |
| `com.minhasofertas.app.telegram.AuthState` | Máquina de Estados | Modelagem dos estados de autenticação do Telegram | `AuthState.Error` disparado em qualquer erro periférico de TDLib, ejetando o usuário do feed. |
| `com.minhasofertas.app.service.TelegramMonitorService` | Foreground Service | Serviço de monitoramento contínuo em primeiro plano | Corrotina em `onStartCommand` vazia; sem comunicação com TDLib; `notifyNewOffer` inativo. |
| `com.minhasofertas.app.data.OfferRepository` | Repositório | Repositório em memória com StateFlow de ofertas | `MutableStateFlow` em RAM; mutação não-atômica sem CAS (`update`); deduplicação restrita ao mesmo canal. |
| `com.minhasofertas.app.data.PriceParser` | Motor de Parsing | Extração de preços, títulos, cupons e links por regex | Bug de menor preço (frete/cupom capturado); ignora hiperlinks do Telegram; regex vulnerável a falsos positivos. |
| `com.minhasofertas.app.data.UserPreferences` | Preferências | Armazenamento de `api_id` e `api_hash` via SharedPreferences | Acesso síncrono; ausência de criptografia no `api_hash`; violação de diretrizes MAD (DataStore). |
| `com.minhasofertas.app.data.AppConfig` | Configurações | Listas estáticas de canais, termos permitidos e proibidos | Canais e palavras-chave hardcoded; usuário não possui Wishlist personalizada. |
| `com.minhasofertas.app.ui.MainScreen` | Tela Principal (Compose) | Feed de ofertas agrupadas, filtros rápidos e busca | `groupOffers` na Main Thread; I/O de disco com `File.exists()`; ausência de debounce; sem `SnackbarHost`. |
| `com.minhasofertas.app.ui.OnboardingScreen` | Tela de Entrada | Fluxo de 6 etapas para configuração de API e login Telegram | Sem tratamento de erros granulados; formulários sem validação semântica prévia. |
| `com.minhasofertas.app.ui.theme.Theme` | Design System | Configuração de tipografia e paleta de cores | Apenas `lightColorScheme`; ausência de Dark Theme; cores estáticas hardcoded na UI; touch targets < 48dp. |

---

## 3. Recomendações Estruturadas por Eixo

---

### Eixo 1: Técnico e Infraestrutura (TDLib, Ciclo de Vida, Concorrência, Rede e Bateria)

#### [TEC-01] Ciclo de Vida Resiliente da TDLib e Encerramento Gracioso com `AuthorizationStateClosed`
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:51-68`, o método `initClient()` executa `client?.send(TdApi.Close(), null)` de forma assíncrona, anula a referência (`client = null`) e imediatamente invoca `Client.create(...)`.
  - Em `MainActivity.kt:36, 107-110`, uma nova instância de `TelegramManager` é criada em `onCreate()` e encerrada em `onDestroy()`.
  - **Falha Técnica:** Segundo as especificações formais da TDLib, o banco de dados interno LevelDB/SQLite (`td.binlog`) permanece travado até que o evento `TdApi.AuthorizationStateClosed` seja emitido. Ao recriar a Activity em rotações de tela ou reinicializações do app, duas instâncias JNI competem pelo mesmo diretório, resultando em crashes com `java.lang.RuntimeException: Database is locked` ou corrupção do banco local.
- **Proposta Técnica:**
  - Implementar uma máquina de estados atômica (`AtomicBoolean(false)`) controlando o encerramento da TDLib.
  - Bloquear novas instanciações de `Client.create` até que o evento `TdApi.AuthorizationStateClosed` tenha sido recebido e confirmado via StateFlow.
  ```kotlin
  // TelegramManager.kt - Ciclo de reinicialização seguro
  private val isClosing = AtomicBoolean(false)
  private val _clientClosedSignal = MutableSharedFlow<Unit>(replay = 0)

  fun closeGracefully(onClosed: (() -> Unit)? = null) {
      if (client != null && !isClosing.getAndSet(true)) {
          client?.send(TdApi.Close()) { result ->
              Log.d(TAG, "TdApi.Close solicitado: $result")
          }
      }
  }

  private fun handleAuthState(state: TdApi.AuthorizationState) {
      when (state) {
          is TdApi.AuthorizationStateClosed -> {
              Log.i(TAG, "TDLib encerrada com sucesso. Liberando locks.")
              client = null
              isClosing.set(false)
              _authState.value = AuthState.Initial
              // Seguro para recriação ou cleanup
          }
          // ...
      }
  }
  ```
- **Justificativa MAD & TDLib:** Em conformidade com a [Documentação Oficial da TDLib](https://core.telegram.org/tdlib), múltiplas instâncias no mesmo `databaseDirectory` são terminantemente proibidas e resultam em lock estrito de arquivos I/O.
- **Estimativa:** Impacto: **Crítico** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [TEC-02] Monitoramento Ativo de Conectividade com `UpdateConnectionState` e `SetNetworkType`
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:75-103`, o método `onResult` trata apenas autorização, novas mensagens, novos chats e arquivos. O evento `TdApi.UpdateConnectionState` é ignorado.
  - O `AndroidManifest.xml:5` declara `ACCESS_NETWORK_STATE`, porém o código Kotlin nunca registra um `ConnectivityManager.NetworkCallback` e nunca invoca `TdApi.SetNetworkType`.
  - Em `MainScreen.kt:102`, o indicador *"Ao vivo • 14 canais integrados"* é renderizado estaticamente mesmo sem conexão de rede.
  - **Falha Técnica:** Sem `SetNetworkType`, a TDLib depende de timeouts longos do kernel Linux (que podem levar mais de 5 minutos) para identificar a perda de conexão TCP após alternância entre Wi-Fi e dados móveis (4G/5G). Durante esse período, o app falha em receber mensagens e drena bateria reenviando pacotes em sockets mortos.
- **Proposta Técnica:**
  - Expor `ConnectionStatus` reativo (`WaitingForNetwork`, `Connecting`, `Updating`, `Ready`) via `StateFlow` e tratá-lo no `onResult`.
  - Registrar um `NetworkCallback` no contexto da aplicação para notificar a TDLib instantaneamente:
  ```kotlin
  // NetworkMonitor.kt - Notificação de interfaces de rede à TDLib
  class NetworkMonitor(private val context: Context, private val onNetworkChanged: (TdApi.NetworkType) -> Unit) {
      private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

      fun startMonitoring() {
          val request = NetworkRequest.Builder()
              .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
              .build()

          cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
              override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                  val type = when {
                      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
                      caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TdApi.NetworkTypeMobile()
                      else -> TdApi.NetworkTypeOther()
                  }
                  onNetworkChanged(type)
              }

              override fun onLost(network: Network) {
                  onNetworkChanged(TdApi.NetworkTypeNone())
              }
          })
      }
  }
  ```
- **Justificativa MAD & TDLib:** Diretrizes oficiais de conectividade do [Android Developers: Monitor connectivity status](https://developer.android.com/training/monitoring-device-state/connectivity-status-type) e documentação de métodos de rede da TDLib (`TdApi.SetNetworkType`).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [TEC-03] Isolamento de Thread de Parsing com `Channel` e Coleções Thread-Safe
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:34-36`, as variáveis `chatTitles`, `chatUsernames` e `fileIdToOfferId` utilizam `mutableMapOf()`, que gera instâncias de `LinkedHashMap` sem sincronização de acesso.
  - Em `TelegramManager.kt:251-289`, o método `handleNewMessage` executa múltiplos regexes pesados (`PriceParser.isOfferMatching`, `extractPrices`, `extractCoupon`, `extractStoreLink`) na mesma thread de callback nativo JNI da TDLib.
  - **Falha Técnica:** Operações simultâneas de leitura/escrita em `mutableMapOf` sob rajadas de mensagens geram `ConcurrentModificationException`. Além disso, a execução síncrona de regex pesados na thread da TDLib atrasa o envio de pings do protocolo MTProto, induzindo os servidores do Telegram a considerar o cliente inativo.
- **Proposta Técnica:**
  - Substituir mapas comuns por `ConcurrentHashMap`.
  - Criar um canal desacoplado `Channel<TdApi.Message>(capacity = Channel.BUFFERED)` com processamento em pool de alta densidade de cálculo (`Dispatchers.Default`):
  ```kotlin
  // TelegramManager.kt - Processamento desacoplado de alta performance
  private val chatTitles = ConcurrentHashMap<Long, String>()
  private val chatUsernames = ConcurrentHashMap<Long, String>()
  private val fileIdToOfferId = ConcurrentHashMap<Int, Long>()

  private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  private val messageChannel = Channel<TdApi.Message>(capacity = Channel.BUFFERED)

  init {
      processingScope.launch {
          for (message in messageChannel) {
              parseAndProcessMessage(message) // Executa em Dispatchers.Default sem bloquear TDLib
          }
      }
  }

  private fun handleNewMessage(message: TdApi.Message) {
      messageChannel.trySend(message)
  }
  ```
- **Justificativa MAD & TDLib:** Diretriz MAD de preservação da thread de I/O e uso de Coroutine Dispatchers específicos ([Kotlin Coroutines Best Practices: Dispatchers.Default for CPU work](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [TEC-04] Atualização Atômica de Ofertas no Repositório com Compare-And-Set (CAS)
- **Diagnóstico do Código Atual:**
  - Em `OfferRepository.kt:14-35`, os métodos `addOffer` e `updateOfferImage` realizam mutações manuais:
    `val current = _offers.value.toMutableList(); current.add(...); _offers.value = current`
  - **Falha Técnica:** Condição de corrida severa (*Lost Update Race Condition*). Se uma nova oferta chega na thread A enquanto a notificação de término de download de imagem conclui na thread B, ambas lêem a mesma lista inicial e uma sobrescreve o StateFlow da outra, causando descarte silencioso de mensagens ou fotos.
- **Proposta Técnica:**
  - Utilizar o operador nativo atômico `_offers.update { currentList -> ... }`, que aplica a operação de forma segura via atomicidade CAS:
  ```kotlin
  // OfferRepository.kt - Mutações atômicas livres de condição de corrida
  fun addOffer(offer: Offer) {
      _offers.update { current ->
          val exists = current.any { it.channel == offer.channel && it.id == offer.id }
          if (exists) current else listOf(offer) + current
      }
  }

  fun updateOfferImage(offerId: Long, imagePath: String) {
      _offers.update { current ->
          current.map { if (it.id == offerId) it.copy(imagePath = imagePath) else it }
      }
  }
  ```
- **Justificativa MAD & TDLib:** Garantia formal de consistência de estado reativo em ambientes concorrentes no ecossistema Kotlin Coroutines ([MutableStateFlow.update](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/update.html)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [TEC-05] Resiliência no Tratamento de Erros da TDLib (Eliminar Ejeção Indevida para Onboarding)
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:98-102`, qualquer objeto do tipo `TdApi.Error` recebido no callback global `onResult` altera o estado global:
    `_authState.value = AuthState.Error(object.message)`
  - Em `MainActivity.kt:48`, o aplicativo usa `Crossfade(targetState = authState is AuthState.Ready)` para decidir qual tela exibir.
  - **Falha Técnica:** Erros comuns e temporários (como falha no download de um thumbnail, canal excluído, timeout passageiro ou erro 400 em comando acessório) acionam `AuthState.Error`. Isso faz com que **o usuário que estava visualizando as ofertas seja abruptamente ejetado para a tela de Onboarding/Erro**, exigindo reinserção de dados ou reinício do app.
- **Proposta Técnica:**
  - Separar os fluxos de erros de autorização dos erros operacionais comuns da TDLib.
  - Erros de autenticação devem ser tratados exclusivamente em resposta a comandos de autenticação (`checkAuthCode`, `checkPassword`, etc.).
  - Erros operacionais comuns devem ser emitidos para um `SharedFlow<String>` de avisos transitórios (exibidos via Snackbar na tela principal), sem jamais alterar o `_authState`.
- **Justificativa MAD & TDLib:** Preservação da sessão ativa do usuário e isolamento de falhas locais (Princípio de Tolerância a Falhas MAD).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [TEC-06] Download Racional de Imagens e Política de Limpeza de Cache (`OptimizeStorage`)
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:290-302`, o código obtém a foto da mensagem com `photo.sizes.maxByOrNull { it.photo.size }` e dispara `DownloadFile` com prioridade `32` (prioridade máxima absoluta da TDLib).
  - Em `TelegramManager.kt:108-113`, as fotos são salvas em `context.filesDir/tdlib_files` sem nenhum mecanismo de expiração.
  - **Falha Técnica:**
    1. O app baixa imagens em resolução original de até 8MB para renderizá-las em miniaturas de ~100dp no Compose, consumindo gigabytes de dados móveis 4G/5G desnecessariamente.
    2. Com 14 canais postando diariamente, o armazenamento do smartphone do usuário se esgota progressivamente, gerando alertas de falta de espaço no sistema operacional.
- **Proposta Técnica:**
  - Baixar fotos com tamanho intermediário tipificado pela TDLib (tipo `'m'` com ~320px ou `'x'` com ~800px).
  - Reduzir prioridade de download para `1` (segundo plano).
  - Executar rotina periódica invocando `TdApi.OptimizeStorage`, restringindo o cache total a 250 MB e descartando arquivos com mais de 7 dias:
  ```kotlin
  // TelegramManager.kt - Otimização de armazenamento e downloads
  fun trimStorageCache() {
      val maxSizeBytes = 250L * 1024 * 1024 // 250 MB
      val maxTimeSec = 7 * 24 * 3600         // 7 dias
      client?.send(
          TdApi.OptimizeStorage(
              maxSizeBytes, 0, -1, maxTimeSec,
              arrayOf(TdApi.FileTypeThumbnail(), TdApi.FileTypePhoto()),
              null, null, false, 0
          )
      ) { res ->
          Log.d(TAG, "Resultado da otimização de cache: $res")
      }
  }
  ```
- **Justificativa MAD & TDLib:** Conformidade com as diretrizes de gerenciamento de cache de arquivos do Android e API `TdApi.OptimizeStorage` da TDLib.
- **Estimativa:** Impacto: **Alto** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

### Eixo 2: Arquitetura e Persistência On-Device (Clean Architecture, Room e DataStore)

#### [ARQ-01] Persistência Local Reativa com Jetpack Room Database (100% On-Device)
- **Diagnóstico do Código Atual:**
  - Em `OfferRepository.kt:7-10`, as ofertas são mantidas exclusivamente em `private val _offers = MutableStateFlow<List<Offer>>(emptyList())`.
  - No `build.gradle.kts:61-91`, não há dependência de Room nem de SQLite.
  - **Falha Técnica:** A retenção de ofertas depende 100% da permanência do processo do app na memória RAM. Ao deslizar o app na lista de recentes (*swipe to dismiss*) ou quando o Android mata o processo sob pressão de memória, todas as promoções capturadas são destruídas. Ao reabrir o app, o feed exibe a mensagem de lista vazia.
- **Proposta Técnica:**
  - Integrar Jetpack Room (`androidx.room:room-runtime:2.6.1` e `room-ktx:2.6.1`).
  - Definir o schema SQLite local com tabelas indexadas para ofertas, histórico de preços e canais.
  - Configurar busca rápida com módulo Full-Text Search (FTS5) nativo do SQLite:
  ```kotlin
  // Room Entity - OfferEntity.kt
  @Entity(
      tableName = "offers",
      indices = [
          Index(value = ["canonical_id"]),
          Index(value = ["created_at"]),
          Index(value = ["price"])
      ]
  )
  data class OfferEntity(
      @PrimaryKey val id: Long,
      @ColumnInfo(name = "channel_id") val channelId: Long,
      @ColumnInfo(name = "channel_title") val channelTitle: String,
      @ColumnInfo(name = "canonical_id") val canonicalId: String,
      val title: String,
      @ColumnInfo(name = "raw_message") val rawMessage: String,
      val price: Double?,
      @ColumnInfo(name = "original_price") val originalPrice: Double?,
      @ColumnInfo(name = "discount_percent") val discountPercent: Int?,
      val coupon: String?,
      @ColumnInfo(name = "store_link") val storeLink: String?,
      @ColumnInfo(name = "primary_link") val primaryLink: String,
      @ColumnInfo(name = "image_path") val imagePath: String?,
      @ColumnInfo(name = "created_at") val createdAt: Long
  )
  ```
  ```kotlin
  // OfferDao.kt - Fluxo reativo para a UI
  @Dao
  interface OfferDao {
      @Query("SELECT * FROM offers ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
      fun observeOffersPaged(limit: Int, offset: Int): Flow<List<OfferEntity>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertOffer(offer: OfferEntity)

      @Query("UPDATE offers SET image_path = :imagePath WHERE id = :offerId")
      suspend fun updateImagePath(offerId: Long, imagePath: String)

      @Query("DELETE FROM offers WHERE created_at < :expirationTimestamp")
      suspend fun deleteExpiredOffers(expirationTimestamp: Long): Int
  }
  ```
- **Justificativa MAD & TDLib:** Princípio MAD da Fonte Única da Verdade (*Single Source of Truth - SSOT*) com persistência offline-first ([Guide to app architecture: Data layer](https://developer.android.com/topic/architecture/data-layer)).
- **Estimativa:** Impacto: **Crítico** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [ARQ-02] Desacoplamento da TDLib da Activity e Sustentação pelo `TelegramMonitorService`
- **Diagnóstico do Código Atual:**
  - Em `MainActivity.kt:36, 107-110`, a instância do `TelegramManager` pertence à `MainActivity` e é destruída em seu `onDestroy()`.
  - Em `TelegramMonitorService.kt:37-39`, o Foreground Service executa uma corrotina vazia sem nenhuma referência ao `TelegramManager`.
  - **Falha Técnica:** O aplicativo simula um monitoramento em segundo plano. Quando a Activity é destruída, o cliente TDLib morre, mas o serviço continua exibindo uma notificação de status "Minhas Ofertas (Ativo)", enganando o usuário.
- **Proposta Técnica:**
  - Promover `TelegramManager` a um Singleton com escopo de aplicação (`ApplicationScope`), orquestrado diretamente pelo ciclo de vida do `TelegramMonitorService`.
  - O `TelegramMonitorService` mantém o cliente TDLib ativo enquanto o monitoramento estiver ligado pelo usuário.
  - A `MainActivity` e os ViewModels apenas observam os fluxos do banco de dados Room e do repositório, desconectando-se da posse direta do cliente nativo.
- **Justificativa MAD & TDLib:** Separação estrita de ciclo de vida de UI e processos de longa duração em background no Android ([Android Services Overview](https://developer.android.com/guide/components/services)).
- **Estimativa:** Impacto: **Crítico** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [ARQ-03] Extração de Hiperlinks Formatados da TDLib (`TextEntityTypeTextUrl`)
- **Diagnóstico do Código Atual:**
  - Em `TelegramManager.kt:252-256`, o código extrai o texto da mensagem via:
    `val text = when (val content = message.content) { is TdApi.MessageText -> content.text.text ... }`
  - Em `PriceParser.kt:88-104`, o método `extractStoreLink` busca URLs através de regex aplicado apenas a essa string plana.
  - **Falha Técnica:** No ecossistema Telegram de ofertas, a grande maioria dos canais formata links usando textos âncora (ex: `👉 [COMPRE AQUI COM DESCONTO](https://amzn.to/3xyz)`). O atributo `content.text.text` retorna apenas a string plana `"COMPRE AQUI COM DESCONTO"`. O link real reside em `content.text.entities`, onde cada entidade do tipo `TdApi.TextEntityTypeTextUrl` guarda o atributo `url`. Como o app ignora as entidades:
    1. A URL da loja nunca é detectada (`storeLink = null`).
    2. O link da oferta é substituído pelo link do próprio canal do Telegram (`https://t.me/canal/12345`).
    3. Ao clicar em "Ver Oferta", o usuário é jogado no aplicativo do Telegram em vez de abrir o site de compras.
- **Proposta Técnica:**
  - Inspecionar `formattedText.entities` da TDLib e extrair URLs prioritárias de entidades `TextEntityTypeTextUrl`, combinando-as com links de texto plano:
  ```kotlin
  // TelegramManager.kt - Extração completa de hiperlinks da TDLib
  private fun extractAllUrlsFromMessage(formattedText: TdApi.FormattedText): List<String> {
      val urls = mutableListOf<String>()
      
      // 1. Extrai hiperlinks embutidos em palavras-âncora
      for (entity in formattedText.entities) {
          if (entity.type is TdApi.TextEntityTypeTextUrl) {
              val textUrl = (entity.type as TdApi.TextEntityTypeTextUrl).url
              if (!textUrl.isNullOrBlank()) {
                  urls.add(textUrl)
              }
          }
      }
      
      // 2. Extrai URLs no corpo do texto por regex como fallback
      val matcher = PriceParser.URL_REGEX.matcher(formattedText.text)
      while (matcher.find()) {
          urls.add(matcher.group())
      }
      
      return urls
  }
  ```
- **Justificativa MAD & TDLib:** Utilização integral das estruturas de dados tipificadas da TDLib (`TdApi.FormattedText`, `TdApi.TextEntity`) para não corromper a experiência de conversão de afiliados.
- **Estimativa:** Impacto: **Crítico** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [ARQ-04] Extrator de Identificadores Canônicos de Produtos On-Device
- **Diagnóstico do Código Atual:**
  - Em `OfferRepository.kt:54-57`, a chave de agrupamento executa `offer.storeLink.substringBefore("?").lowercase()`.
  - **Falha Técnica:**
    1. Para links de afiliados encurtados de canais diferentes (`https://amzn.to/3XyzA` vs `https://amzn.to/3AbCd`), o método produz chaves distintas, impedindo que o mesmo produto seja agrupado.
    2. A linha 58 (`PriceParser.normalizeTitleForGrouping`) nunca é acionada quando há link presente, quebrando a unificação de ofertas idênticas.
- **Proposta Técnica:**
  - Criar um **Resolvedor Canônico On-Device**:
    - **Amazon:** Extração de ASIN (código de 10 caracteres alfanuméricos) via regex `/(?:dp|gp/product|d)/([A-Z0-9]{10})`. Chave: `amzn:asin:<ID>`.
    - **Mercado Livre:** Extração de MLB via regex `MLB-?(\d{8,12})` ou `/p/(MLB\d+)`. Chave: `meli:mlb:<ID>`.
    - **Shopee:** Extração de `shopId` e `itemId` via regex `(?:\.i\.|item/)(\d+)/(\d+)`. Chave: `shopee:<shopId>_<itemId>`.
    - **Encurtadores (`amzn.to`, `meli.la`, `shope.ee`):** Disparo assíncrono em segundo plano de uma requisição HTTP `HEAD` com `followRedirects = true` diretamente pelo smartphone (usando OkHttp), descobrindo a URL final e extraindo o ID canônico sem nenhum backend intermediário.
    - **Fallback de Similaridade Textual:** Quando não houver link direto ou ID canônico, comparar títulos utilizando algoritmo Jaccard ponderado na CPU local com corte de similaridade >= 70%.
- **Justificativa MAD & TDLib:** Processamento de regras de negócio determinísticas localmente no dispositivo, viabilizando comparação de preços multi-canal sem vazamento de dados para a nuvem.
- **Estimativa:** Impacto: **Alto** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [ARQ-05] Sanitização do Motor de Parsing de Preços e Eliminação do Bug de Menor Preço
- **Diagnóstico do Código Atual:**
  - Em `PriceParser.kt:66-77`, o método `extractPrices` busca todos os números no formato monetário com `PRICE_REGEX` e na linha 75 executa:
    `val current = prices.minOrNull()`
  - **Falha Técnica:** Se o canal de ofertas incluir no texto `"Frete R$ 12,90"`, `"Cupom de R$ 20,00"` ou `"Cashback de R$ 15,00"`, o algoritmo elege **R$ 12,90** como o preço de um notebook de R$ 3.000,00, gerando dados totalmente falsos e destruindo a credibilidade do app.
- **Proposta Técnica:**
  - Aplicar uma esteira de higienização de texto antes do parsing de preços:
    1. Descartar valores precedidos por termos negativos: `frete`, `frete de`, `cupom de`, `desconto de`, `cashback`, `taxa`.
    2. Distinguir formalmente preço à vista/PIX de preço parcelado.
    3. Estabelecer heurística de piso mínimo (descartar valores inferiores a R$ 5,00 para categorias gerais de produtos).
  ```kotlin
  // PriceParser.kt - Parsing sanitizado e contextual de preços
  private val NEGATIVE_CONTEXT_REGEX = Pattern.compile(
      "(?:frete|frete\\s+de|cashback|cupom\\s+de|desconto\\s+de|taxa|economize)\\s*(?:de\\s*)?R?\\$\\s*([\\d.,]+)",
      Pattern.CASE_INSENSITIVE
  )

  fun extractSanitizedPrices(text: String): Pair<Double?, Double?> {
      // 1. Remove menções a frete, cupom em reais e parcelamentos
      val sanitized = removeNonProductPrices(text)
      
      // 2. Busca padrões explícitos 'De R$ X por R$ Y'
      val dePorMatch = DE_POR_REGEX.matcher(sanitized)
      if (dePorMatch.find()) {
          val pOriginal = parsePrice(dePorMatch.group(1))
          val pPromo = parsePrice(dePorMatch.group(2))
          if (pPromo != null && pOriginal != null) {
              return pPromo to pOriginal
          }
      }
      
      // 3. Extrai valores com piso mínimo de segurança
      val candidates = extractAllPrices(sanitized).filter { it >= 5.0 }
      val price = candidates.firstOrNull() ?: candidates.minOrNull()
      return price to null
  }
  ```
- **Justificativa MAD & TDLib:** Garantia de integridade e precisão matemática de dados extraídos por heurística local.
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [ARQ-06] Migração de `SharedPreferences` para Jetpack DataStore com Criptografia no Android Keystore
- **Diagnóstico do Código Atual:**
  - Em `UserPreferences.kt:6-24`, o `api_id` e o `api_hash` do usuário são gravados em arquivo XML de texto plano (`app_prefs.xml`) via `SharedPreferences`.
  - **Falha Técnica:** Violação das diretrizes de segurança móvel do Android (OWASP Mobile M2 e M9). O `api_hash` concede acesso à conta do Telegram do usuário e fica exposto em texto simples no armazenamento interno. Além disso, `SharedPreferences` opera com E/S síncrona bloqueante, podendo causar micro-pausas na Main Thread.
- **Proposta Técnica:**
  - Migrar para **Jetpack Preferences DataStore** (`androidx.datastore:datastore-preferences:1.1.1`).
  - Criptografar o `api_hash` utilizando chaves AES-256-GCM gerenciadas diretamente pelo **Android Keystore System** do hardware do smartphone.
  - Utilizar a mesma chave do Keystore para preencher o parâmetro `databaseEncryptionKey` na inicialização do TDLib (`TelegramManager.kt:121`).
- **Justificativa MAD & TDLib:** Diretrizes de segurança oficial do Android ([DataStore Overview](https://developer.android.com/topic/libraries/architecture/datastore) e [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [ARQ-07] Criação da Suíte de Testes Unitários Automatizados no Módulo Android
- **Diagnóstico do Código Atual:**
  - O diretório `android/app/src` contém apenas a pasta `main`. A pasta `test` inexiste.
  - **Falha Técnica:** Zero cobertura de testes automatizados para lógicas complexas e suscetíveis a regressão, como regex de preços, extração de cupons, sanitização de títulos, normalização de links e deduplicação de mensagens.
- **Proposta Técnica:**
  - Adicionar as dependências de testes unitários no `build.gradle.kts`:
    `testImplementation("junit:junit:4.13.2")`, `testImplementation("io.mockk:mockk:1.13.13")`, `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")`.
  - Criar suíte de testes unitários em `android/app/src/test/java/com/minhasofertas/app/`:
    - `PriceParserTest.kt`: validação de regex em casos reais de postagens de canais (preços com pontos, vírgulas, parcelas, fretes e cupons).
    - `OfferRepositoryTest.kt`: validação de deduplicação multi-canal e agrupamento com encurtadores.
    - `CanonicalUrlResolverTest.kt`: extração de ASIN, MLB e ItemId.
- **Justificativa MAD & TDLib:** Requisito basilar de Modern Android Development para garantia de qualidade contínua e integridade de regras de negócio ([Test apps on Android](https://developer.android.com/training/testing)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

### Eixo 3: Recursos Funcionais e Sistema de Alertas Locais

#### [FUNC-01] Ativação do Sistema de Alertas Locais de Alta Prioridade (`IMPORTANCE_HIGH`)
- **Diagnóstico do Código Atual:**
  - Em `TelegramMonitorService.kt:83-92`, a função `notifyNewOffer` foi implementada mas **nunca é chamada em nenhum ponto do código-fonte**.
  - O canal de notificação `monitor_channel` foi criado com `NotificationManager.IMPORTANCE_LOW`, que suprime som, vibração e notificações flutuantes (*heads-up*).
  - **Falha Técnica:** A principal promessa funcional de um monitorador de promoções — alertar o usuário quando surge uma oferta imperdível ou produto desejado — está completamente inoperante.
- **Proposta Técnica:**
  - Criar um canal dedicado de alertas urgentes (`offers_alert_channel`) com `NotificationManager.IMPORTANCE_HIGH`.
  - Interligar o recebimento de mensagens na TDLib com a verificação de regras de interesse (Wishlist / Descontos >= 50% / Menor Preço Histórico).
  - Quando houver correspondência, invocar o disparo imediato da notificação local com som, vibração e resumo da oferta.
- **Justificativa MAD & TDLib:** Uso de canais nativos de notificação em conformidade com o Android 8.0+ ([Notification Channels](https://developer.android.com/develop/ui/views/notifications/channels)) sem requerer Firebase Cloud Messaging (FCM) ou infraestruturas externas.
- **Estimativa:** Impacto: **Crítico** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [FUNC-02] Deep Linking On-Device em Notificações Locais
- **Diagnóstico do Código Atual:**
  - Em `TelegramMonitorService.kt:85-92`, a notificação criada em `notifyNewOffer` não define nenhum `setContentIntent`.
  - O `AndroidManifest.xml` não possui filtros de intenção (`intent-filter`) para esquemas de navegação profunda.
  - **Falha Técnica:** Ao tocar na notificação, nada acontece ou o app apenas abre na tela inicial sem localizar a oferta alertada, forçando o usuário a procurar manualmente no meio de centenas de itens.
- **Proposta Técnica:**
  - Adicionar suporte a deep link local na `MainActivity` via Intent extras ou URI customizada (`minhasofertas://offer/{id}`).
  - Anexar um `PendingIntent` imutável à notificação local (`PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT`) transportando o `offerId`.
  - Ao abrir o app através da notificação, a lista da `MainScreen` rola automaticamente até a posição do card correspondente (`lazyListState.animateScrollToItem`).
- **Justificativa MAD & TDLib:** Diretrizes de experiência de usuário e deep linking nativo do Android ([Create deep links to app content](https://developer.android.com/training/app-links/deep-linking)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [FUNC-03] Módulo de Configuração Local de Canais e Lista de Desejos (Wishlist)
- **Diagnóstico do Código Atual:**
  - Em `AppConfig.kt:4-44`, as listas `DEFAULT_CHANNELS`, `DEFAULT_KEYWORDS` e `DEFAULT_IGNORE_TERMS` são constantes imutáveis hardcoded em código Kotlin.
  - **Falha Técnica:** O usuário não possui autonomia para monitorar seus próprios canais favoritos do Telegram, não pode cadastrar palavras-chave de produtos que deseja comprar (ex: "PlayStation 5", "Air Fryer", "RTX 4060") e não pode ajustar faixas de preço para disparo de alerta.
- **Proposta Técnica:**
  - Criar as tabelas locais no Room: `monitored_channels` e `wishlist_keywords`.
  - Disponibilizar telas na interface Compose para:
    1. **Gerenciar Canais:** Adicionar `@username` de qualquer canal público do Telegram, ativar/desativar monitoramento por canal.
    2. **Wishlist Inteligente:** Cadastrar palavras-chave de interesse, teto máximo de preço e alternância para receber notificação sonora urgente exclusivamente quando esses itens aparecerem.
- **Justificativa MAD & TDLib:** Autonomia e personalização do usuário com armazenamento 100% on-device em Room/DataStore.
- **Estimativa:** Impacto: **Alto** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [FUNC-04] Histórico de Preços e Detecção de Mínimas Históricas On-Device
- **Diagnóstico do Código Atual:**
  - O aplicativo não armazena registros históricos de valores observados para um mesmo produto.
  - **Falha Técnica:** Impossibilidade de informar se um preço divulgado é realmente uma promoção verídica ou se o canal está anunciando o item pelo valor habitual.
- **Proposta Técnica:**
  - Criar a entidade `PriceHistoryEntity` vinculada ao `canonical_id` do produto, registrando preço e timestamp a cada ocorrência.
  - Calcular métricas de valor na camada de domínio:
    - Menor preço já registrado nos últimos 30/60/90 dias.
    - Preço médio histórico.
  - Exibir badge comemorativo no card do Compose quando o preço atual for igual ao menor preço histórico: `"📉 Menor preço em 30 dias!"`.
- **Justificativa MAD & TDLib:** Agregação de inteligência de compras puramente no hardware do usuário, agregando alto valor competitivo sem custos de servidores.
- **Estimativa:** Impacto: **Alto** | Esforço: **Médio** | Priorização: **Evolução Futura**

---

#### [FUNC-05] Rotinas de Manutenção e Retenção On-Device com `WorkManager`
- **Diagnóstico do Código Atual:**
  - Ausência da biblioteca `androidx.work:work-runtime-ktx` no `build.gradle.kts`.
  - Toda a manutenção em segundo plano depende exclusivamente do Foreground Service.
  - **Falha Técnica:** No Android 14+ (API 34 e 35),Foreground Services do tipo `dataSync` sofrem restrições rígidas se mantidos por dias sem interação. Além disso, se o aparelho for reiniciado, não há nenhum serviço agendado para purgar dados obsoletos e arquivos órfãos de fotos em disco.
- **Proposta Técnica:**
  - Adicionar o Jetpack WorkManager (`androidx.work:work-runtime-ktx:2.9.1`).
  - Configurar um `PeriodicWorkRequest` (`DataRetentionWorker`) para rodar a cada 24 horas sob restrições favoráveis (dispositivo conectado ao carregador e ocioso):
    1. Deletar ofertas do banco Room com mais de 30 dias.
    2. Excluir fotos órfãs na pasta `tdlib_files` que não estejam associadas a nenhuma oferta retida.
    3. Acionar `telegramManager.trimStorageCache()`.
- **Justificativa MAD & TDLib:** Padrão oficial recomendado pelo Google para tarefas assíncronas em segundo plano confiáveis e com economia de bateria ([Schedule tasks with WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)).
- **Estimativa:** Impacto: **Médio** | Esforço: **Baixo** | Priorização: **Quick Win**

---

### Eixo 4: Experiência do Usuário (UX) e Jetpack Compose

#### [UX-01] Arquitetura MVVM para o Feed com `MainViewModel` e Processamento em `Dispatchers.Default`
- **Diagnóstico do Código Atual:**
  - Em `MainActivity.kt:44-56` e `MainScreen.kt:55-66`, o estado é observado diretamente de Singletons (`OfferRepository.offers.collectAsState()`) e repassado como parâmetros planos.
  - Em `MainScreen.kt:64-67`, o agrupamento `OfferRepository.groupOffers(offers)` é executado sincronamente dentro de um bloco `remember(offers)` na **thread de Composição de UI**.
  - **Falha Técnica:** Toda mensagem recebida ou imagem atualizada dispara a recomposição e força a reexecução de dezenas de regexes de agrupamento na UI Thread, causando perda visível de quadros (*dropped frames/jank*) durante a rolagem do feed.
- **Proposta Técnica:**
  - Criar `MainViewModel : ViewModel()` com `SavedStateHandle`.
  - Encapsular a agregação de dados em um `StateFlow<FeedUiState>` imutável, executando o agrupamento e a filtragem sob `Dispatchers.Default` fora da Main Thread:
  ```kotlin
  // MainViewModel.kt - UDF e processamento em background
  class MainViewModel(private val offerRepository: OfferRepository) : ViewModel() {
      private val _searchQuery = MutableStateFlow("")
      private val _selectedFilter = MutableStateFlow(OfferFilter.ALL)

      val uiState: StateFlow<FeedUiState> = combine(
          offerRepository.observeOffers(),
          offerRepository.isMonitoringActive,
          _searchQuery.debounce(300),
          _selectedFilter
      ) { offers, isMonitoring, query, filter ->
          // Executado em pool de CPU fora da Main Thread
          val grouped = offerRepository.groupOffers(offers)
          val filtered = filterOffers(grouped, query, filter)
          FeedUiState.Success(filtered, isMonitoring)
      }.flowOn(Dispatchers.Default)
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = FeedUiState.Loading
      )
  }
  ```
- **Justificativa MAD & TDLib:** Diretrizes oficiais de arquitetura de UI do Android com Unidirectional Data Flow ([UDF and UI Layer Architecture](https://developer.android.com/topic/architecture/ui-layer)).
- **Estimativa:** Impacto: **Crítico** | Esforço: **Médio** | Priorização: **Projeto Estrutural**

---

#### [UX-02] Otimização de Estabilidade do Compose e Eliminação de I/O de Disco Síncrono
- **Diagnóstico do Código Atual:**
  - Em `MainScreen.kt:56`, o parâmetro `offers: List<Offer>` utiliza a interface `java.util.List`, considerada **Unstable** pelo compilador do Compose, impedindo o *skipping* de recomposições.
  - Em `MainScreen.kt:289-296`, dentro do composable `ModernGroupedOfferCard`, executa-se:
    `val imageFile = File(offer.imagePath); val model = if (imageFile.exists()) imageFile else offer.imagePath`
  - **Falha Técnica:** A chamada `imageFile.exists()` realiza I/O de disco flash na Main Thread a cada card desenhado na `LazyColumn`. Isso viola as regras do `StrictMode` do Android e provoca micro-travamentos na rolagem contínua.
- **Proposta Técnica:**
  - Utilizar coleções imutáveis (`kotlinx.collections.immutable.ImmutableList`) ou `@Immutable data class FeedUiState`.
  - Remover a checagem manual `imageFile.exists()`. O Coil Compose já possui resolvers assíncronos nativos projetados para lidar com `File` ou string de caminho em background, sem travar a interface:
  ```kotlin
  // ModernGroupedOfferCard.kt - Delegação assíncrona limpa para o Coil
  AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
          .data(offer.imagePath) // Coil gerencia I/O em pool assíncrono interno
          .crossfade(true)
          .build(),
      contentDescription = offer.title,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop
  )
  ```
- **Justificativa MAD & TDLib:** Recomendações oficiais de desempenho do Jetpack Compose ([Jetpack Compose Performance: Stability and Skippability](https://developer.android.com/develop/ui/compose/performance/stability)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [UX-03] Campo de Busca com Debounce de 300ms e Empty States Contextuais com CTA
- **Diagnóstico do Código Atual:**
  - Em `MainScreen.kt:60, 137-160`, a cada caractere digitado em `OutlinedTextField`, a lista completa é filtrada de forma síncrona sem nenhum atraso de digitação (*debounce*).
  - Em `MainScreen.kt:222-252`, a tela de lista vazia exibe uma mensagem fixa genérica ("Aguardando novas ofertas dos canais..."). Se o usuário digita um termo inexistente (ex: "geladeira inox"), a mensagem exibida continua sendo a mesma de ausência de canais.
  - **Falha Técnica:** Desperdício de ciclos de processamento durante a digitação rápida e experiência de usuário confusa, fazendo o usuário acreditar que o monitoramento parou de funcionar quando na verdade foi o filtro de busca que não encontrou resultados.
- **Proposta Técnica:**
  - Aplicar `debounce(300)` via Coroutine Flow no `MainViewModel`.
  - Criar Empty States contextuais:
    1. **Sem ofertas capturadas:** Ilustração temática + "Aguardando postagens dos 14 canais do Telegram...".
    2. **Busca sem correspondência:** Ícone de busca vazia + "Nenhum resultado encontrado para '[termo]'" + Botão de Ação (CTA) em destaque: `"Limpar Busca"`.
    3. **Monitoramento pausado:** Aviso claro com botão de ação: `"Ativar Monitoramento"`.
- **Justificativa MAD & TDLib:** Padrões de design Material 3 para tratamento de estados vazios e responsividade de campos de entrada.
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [UX-04] Componente de Shimmer / Skeleton Loading na Inicialização e Sincronização
- **Diagnóstico do Código Atual:**
  - Na `MainScreen.kt`, não há nenhum indicador visual de carregamento ou esqueleto durante o período em que a TDLib se conecta aos datacenters e efetua o download do histórico recente de mensagens.
  - **Falha Técnica:** O aplicativo exibe instantaneamente a tela de lista vazia na inicialização fria, gerando a falsa impressão de que não há dados ou que o app travou, antes que os primeiros cards apareçam repentinamente com salto visual (*layout shift*).
- **Proposta Técnica:**
  - Criar o composable `OfferCardSkeleton` com animação gradiente linear de Shimmer baseada em `rememberInfiniteTransition` do Compose.
  - Exibir de 3 a 5 cards esqueletos enquanto o estado do feed for `FeedUiState.Loading` ou a TDLib estiver em estado `ConnectionStateConnecting` / `ConnectionStateUpdating`.
- **Justificativa MAD & TDLib:** Diretrizes de Material Design para feedback progressivo e estabilidade visual cumulativa ([Material 3 Loading States](https://m3.material.io/)).
- **Estimativa:** Impacto: **Médio** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [UX-05] Resiliência em Navegação com `CustomTabsIntent` e Integração de `SnackbarHost`
- **Diagnóstico do Código Atual:**
  - Em `MainScreen.kt:437, 597`, o aplicativo invoca `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(...)))` sem nenhum bloco `try/catch`.
  - Em `MainScreen.kt:89`, o `Scaffold` não instancia um `SnackbarHost`.
  - **Falha Técnica:**
    1. Se o dispositivo do usuário não tiver navegador configurado ou a URL tiver formato corrompido, o Android lança `ActivityNotFoundException` não tratada, resultando em **crash fatal imediato do aplicativo**.
    2. Não há nenhum canal visual para alertar o usuário sobre falhas na cópia de cupons ou erros de conexão da TDLib.
- **Proposta Técnica:**
  - Implementar um utilitário seguro de abertura de URLs (`LinkOpener`) utilizando **Chrome Custom Tabs** (`androidx.browser:browser:1.8.0`) com fallback seguro para navegadores externos protegidos por `try/catch`.
  - Adicionar `snackbarHost = { SnackbarHost(snackbarHostState) }` ao `Scaffold` principal, exibindo confirmações acessíveis de cópia de cupom de desconto com feedback háptico (`HapticFeedbackType.LongPress`).
- **Justificativa MAD & TDLib:** Garantia de estabilidade contra crashes de intents externas ([Android Custom Tabs Guide](https://developer.chrome.com/docs/android/custom-tabs/)).
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

#### [UX-06] Suporte Pleno a Dark Theme (Material 3), Dynamic Colors e Touch Targets de 48dp
- **Diagnóstico do Código Atual:**
  - Em `Theme.kt:24-36`, apenas `lightColorScheme` está configurado. O tema escuro (`darkColorScheme`) inexiste.
  - Em `MainScreen.kt:98, 105, 120, 154, 183, 277, 349, 386`, componentes consomem constantes estáticas globais (`Primary`, `Color(0xFF1E293B)`) em vez de tokens semânticos do tema.
  - Em `MainScreen.kt:431-464`, os chips de fontes comparadas possuem altura de toque de aproximadamente 28dp.
  - **Falha Técnica:**
    1. O aplicativo força tema claro mesmo quando o smartphone está em modo noturno, causando ofuscamento visual e consumo excessivo de energia em telas AMOLED/OLED.
    2. A altura de toque de 28dp viola a norma de acessibilidade Android e WCAG 2.1 (mínimo de **48x48 dp**), dificultando o toque por pessoas com limitações motoras ou telas de alta densidade.
- **Proposta Técnica:**
  - Implementar `DarkColorScheme` em `Theme.kt` com suporte a `dynamicDarkColorScheme`/`dynamicLightColorScheme` no Android 12+.
  - Substituir todas as cores literais por tokens semânticos `MaterialTheme.colorScheme.*`.
  - Aplicar `Modifier.minimumInteractiveComponentSize()` nos chips interativos de canais, assegurando alvos de toque mínimos de 48dp.
- **Justificativa MAD & TDLib:** Conformidade com as normas oficiais de acessibilidade do Google ([Android Accessibility Guidelines: Touch target size](https://developer.android.com/guide/topics/ui/accessibility/apps#touch-target-size)) e princípios de design sustentável Material You.
- **Estimativa:** Impacto: **Alto** | Esforço: **Baixo** | Priorização: **Quick Win**

---

## 4. Matriz de Priorização Estruturada

A matriz abaixo consolida todas as 25 recomendações técnicas, organizadas segundo a relação entre **Esforço Relativo de Implementação** e **Impacto Estratégico no Produto**:

| ID | Eixo | Título da Proposta | Esforço | Impacto | Classificação na Matriz |
|:---:|:---:|:---|:---:|:---:|:---:|
| **TEC-01** | Técnico | Ciclo de vida seguro TDLib com `AuthorizationStateClosed` | Baixo | Crítico | **Quick Win** |
| **TEC-02** | Técnico | Monitoramento de conectividade (`UpdateConnectionState` / `SetNetworkType`) | Baixo | Alto | **Quick Win** |
| **TEC-04** | Técnico | Mutação atômica em `OfferRepository` via `update` (CAS) | Baixo | Alto | **Quick Win** |
| **TEC-05** | Técnico | Resiliência em erros TDLib (eliminar ejeção para onboarding) | Baixo | Alto | **Quick Win** |
| **ARQ-03** | Arquitetura | Extração de hiperlinks formatados da TDLib (`TextEntityTypeTextUrl`) | Baixo | Crítico | **Quick Win** |
| **ARQ-05** | Arquitetura | Sanitização de preços (eliminar bug de frete/cashback como preço) | Baixo | Alto | **Quick Win** |
| **ARQ-06** | Arquitetura | Migração para Jetpack DataStore com criptografia Android Keystore | Baixo | Alto | **Quick Win** |
| **ARQ-07** | Arquitetura | Criação da suíte de testes unitários automatizados (`src/test`) | Baixo | Alto | **Quick Win** |
| **FUNC-02**| Funcional | Deep Linking On-Device em notificações locais | Baixo | Alto | **Quick Win** |
| **FUNC-05**| Funcional | Rotinas de manutenção e limpeza diária com `WorkManager` | Baixo | Médio | **Quick Win** |
| **UX-02**  | UX | Estabilidade do Compose e remoção de I/O síncrono (`File.exists`) | Baixo | Alto | **Quick Win** |
| **UX-03**  | UX | Busca com debounce de 300ms e Empty States contextuais com CTA | Baixo | Alto | **Quick Win** |
| **UX-04**  | UX | Shimmer / Skeleton Loading na inicialização e conexões | Baixo | Médio | **Quick Win** |
| **UX-05**  | UX | Navegação segura com `CustomTabsIntent` e adição de `SnackbarHost` | Baixo | Alto | **Quick Win** |
| **UX-06**  | UX | Suporte completo a Dark Theme (M3) e touch targets de 48dp | Baixo | Alto | **Quick Win** |
| **TEC-03** | Técnico | Isolamento de thread de parsing com `Channel` e `ConcurrentHashMap` | Médio | Alto | **Projeto Estrutural** |
| **TEC-06** | Técnico | Otimização de download de imagens intermediárias e `OptimizeStorage` | Médio | Alto | **Projeto Estrutural** |
| **ARQ-01** | Arquitetura | Persistência local reativa com Jetpack Room Database | Médio | Crítico | **Projeto Estrutural** |
| **ARQ-02** | Arquitetura | Desacoplamento da TDLib da Activity para o `TelegramMonitorService` | Médio | Crítico | **Projeto Estrutural** |
| **ARQ-04** | Arquitetura | Extrator de Identificadores Canônicos de Lojas (ASIN/MLB/Shopee) | Médio | Alto | **Projeto Estrutural** |
| **FUNC-01**| Funcional | Ativação do sistema de alertas locais de alta prioridade (`IMPORTANCE_HIGH`)| Médio | Crítico | **Projeto Estrutural** |
| **FUNC-03**| Funcional | Módulo de configuração local de canais e Wishlist personalizada | Médio | Alto | **Projeto Estrutural** |
| **UX-01**  | UX | Arquitetura MVVM para o feed (`MainViewModel`) em `Dispatchers.Default` | Médio | Crítico | **Projeto Estrutural** |
| **FUNC-04**| Funcional | Histórico de preços e detecção de mínimas históricas com badges | Médio | Alto | **Evolução Futura** |
| **UX-07**  | UX | Paginação on-device na `LazyColumn` e filtros por faixa de preço | Alto | Médio | **Evolução Futura** |

---

### 4.1. Sequenciamento Cronológico de Execução (Sprints Recomendados)

```
┌────────────────────────────────────────────────────────────────────────┐
│ FASE 1: QUICK WINS & ESTABILIZAÇÃO CRÍTICA (Sprint 1 - 2 semanas)     │
│ - Correção do ciclo de vida TDLib (TEC-01, TEC-05)                     │
│ - Correção de hiperlinks do Telegram e bug de frete (ARQ-03, ARQ-05)   │
│ - Atomicidade em memória e rede (TEC-02, TEC-04)                       │
│ - Proteção contra crashes na UI e Acessibilidade 48dp (UX-02, UX-05)   │
│ - Suíte de testes unitários para parsing e regras (ARQ-07)             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ FASE 2: FUNDAÇÃO ARQUITETURAL & PERSISTÊNCIA (Sprint 2 - 3 semanas)    │
│ - Implementação do Room Database On-Device (ARQ-01)                    │
│ - Migração de gerenciamento TDLib para o Service/Application (ARQ-02)  │
│ - Arquitetura MVVM com MainViewModel e debounce (UX-01, UX-03)         │
│ - Migração para DataStore criptografado com Keystore (ARQ-06)          │
│ - Dark Theme M3 e componentes de Shimmer (UX-04, UX-06)                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ FASE 3: INTELIGÊNCIA ON-DEVICE & ALERTAS LOCAIS (Sprint 3 - 3 semanas) │
│ - Ativação dos Alertas Locais de alta prioridade (FUNC-01, FUNC-02)    │
│ - Extrator Canônico Multi-Canal (ASIN, MLB, Shopee) (ARQ-04)           │
│ - Tela de Gerenciamento de Canais e Wishlist local (FUNC-03)           │
│ - Otimização de armazenamento e WorkManager (TEC-06, FUNC-05)          │
│ - Isolamento de thread de parsing com Channel nativo (TEC-03)          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ FASE 4: EVOLUÇÕES AVANÇADAS & ANALYTICS LOCAL (Sprint 4 - 4 semanas)  │
│ - Histórico e tendência de preços com gráfico on-device (FUNC-04)      │
│ - Paginação contínua na LazyColumn e filtros por faixa de preço (UX-07)│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Garantia Arquitetural da Restrição 100% On-Device

Todas as recomendações técnicas e funcionais deste documento foram rigorosamente concebidas sob a restrição inviolável: **nenhuma funcionalidade exigirá, direta ou indiretamente, infraestrutura de backend intermediário, servidores proxy proprietários ou bancos de dados na nuvem**.

A conformidade com a soberania local é demonstrada na tabela comparativa a seguir:

| Funcionalidade Planejada | Solução Tradicional (Exige Nuvem/Backend) | Solução Adotada no Minhas Ofertas (100% On-Device) |
|---|---|---|
| **Coleta de Mensagens** | Servidor intermediário faz scrapping e entrega via REST/WebSocket. | O app executa o binário C++ nativo da TDLib (`libtdjni.so`) diretamente no aparelho, conectando-se via protocolo criptografado MTProto aos servidores descentralizados do Telegram. |
| **Persistência de Dados** | Banco de dados relacional na nuvem (PostgreSQL, Firebase, Supabase). | **Jetpack Room Database** com motor SQLite nativo embutido no sandbox privado do Android (`/data/data/com.minhasofertas.app/databases/`), protegido pelo isolamento de processos Linux do sistema. |
| **Armazenamento de Credenciais** | Serviços de autenticação remota (Auth0, Firebase Auth). | Chaves simétricas geradas e armazenadas no **Android Keystore**, garantindo que o hardware do dispositivo (TEE/StrongBox) proteja as credenciais localmente. |
| **Deduplicação de Ofertas** | Worker em nuvem processando filas de mensagens em tempo real. | Algoritmos de parsing com regex de alta performance, descurtamento HTTP HEAD direto e cálculo de similaridade executados no pool de processamento `Dispatchers.Default` da CPU do smartphone. |
| **Notificações Push / Alertas** | Servidor de aplicação disparando mensagens via Firebase Cloud Messaging (FCM). | O próprio `TelegramMonitorService` em execução local identifica o match com a Wishlist e emite notificações nativas via `NotificationManager` local do Android OS. |
| **Limpeza e Retenção** | Tarefas cron rodando em servidores ou lambdas na nuvem. | **Jetpack WorkManager** agendando execuções locais diárias com `PeriodicWorkRequest` gerenciadas pelo próprio scheduler do Android. |

Essa garantia arquitetural assegura **custo zero de manutenção de infraestrutura**, **privacidade integral para o usuário** (seus dados de navegação e credenciais nunca transitam por servidores de terceiros) e **resiliência operacional permanente**.

---

## 6. Referências Oficiais e Diretrizes Técnicas

1. **Modern Android Development (MAD) Architecture Guidelines:**  
   https://developer.android.com/topic/architecture  
   Recomendações formais do Google para arquitetura em camadas (UI, Domain, Data), fluxo unidirecional de dados (UDF) e Fonte Única da Verdade (SSOT).

2. **Documentação Oficial da Biblioteca TDLib (Telegram Database Library):**  
   https://core.telegram.org/tdlib e https://core.telegram.org/tdlib/docs/  
   Guia de referência da máquina de estados assíncrona, parâmetros de inicialização (`TdApi.SetTdlibParameters`), gerenciamento de arquivos e controle de conectividade (`TdApi.SetNetworkType`).

3. **Jetpack Compose Performance & Stability:**  
   https://developer.android.com/develop/ui/compose/performance/stability  
   Regras de imutabilidade, anotações `@Immutable`/`@Stable`, *skipping* de recomposição e otimização de renderização de listas em Compose.

4. **Android Accessibility Guidelines:**  
   https://developer.android.com/guide/topics/ui/accessibility/apps  
   Critérios de conformidade com alvos mínimos de toque (48x48 dp), contraste de cores e rotulagem semântica para leitores de tela.

5. **Android Keystore System & Data Security:**  
   https://developer.android.com/privacy-and-security/keystore  
   Melhores práticas de segurança para proteção de chaves criptográficas em repouso no hardware seguro do dispositivo.
