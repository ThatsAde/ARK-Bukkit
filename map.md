# map.md – Mappa del codice BreachCore

> **Scopo:** sapere sempre *dove* sta una cosa, *a cosa serve*, *chi la usa* e *quando va toccata*, senza esplorare il codice a tentoni.
> **Regola:** ogni modifica al codice aggiorna questa mappa nello stesso commit (vedi `CLAUDE.md` §1).
>
> Legenda stato: ✅ esiste nel codice · ⏳ da creare (indicato il task del piano in corso, in `docs/superpowers/plans/`) · 🔁 esiste ma il task indicato la modifica.
> Percorsi Java abbreviati: `api/` = `core-api/src/main/java/org/breachsmp/core/api/`, `domain/` = `core-domain/src/main/java/org/breachsmp/core/domain/`, `platform/` = `core-platform/src/main/java/org/breachsmp/core/platform/`. I test stanno nello stesso package sotto `src/test/java/`.

---

## 1. Indice rapido: "devo fare X, dove tocco?"

| Devo… | Tocca | Note |
| --- | --- | --- |
| Aggiungere un nuovo modulo di dominio (`module-economy`, …) | nuovo progetto Gradle in `settings.gradle.kts` + classe che implementa `ARKModule` + **una riga** in `platform/module/ModuleCatalog.all()` + chiave `modules.<id>.enabled` in `config.yml` | Il modulo dipende solo da `core-api` (I7). Aggiungi la dipendenza `implementation(project(":module-x"))` in `core-platform/build.gradle.kts` |
| Far dipendere un modulo da un altro | `ARKModule.dependencies()` del modulo che dipende | Ordine e cicli li gestisce `DependencyResolver` |
| Esporre un servizio a altri moduli | interfaccia in `core-api`, registrazione in `onLoad` con `context.services().register(...)` | Chi lo usa: `context.services().require(...)` in `onLoad`/`onEnable` |
| Aggiungere una tabella / colonna | **nuova** migrazione `core-platform/src/main/resources/db/migration/V0NN__<modulo>_<descrizione>.sql` | Mai modificare un file già applicato. Numerazione globale unica (D-04) |
| Aggiungere una chiave di config | `config.yml` (default) + `ConfigLoader.parse` + record in `platform/config/` + test in `ConfigLoaderTest` | Se rompe la compatibilità: alza `ConfigLoader.CURRENT_VERSION` |
| Aggiungere un sottocomando `/breach x` | nel modulo: classe che implementa `AdminSubcommand`, registrata con `context.services().require(CommandRegistry.class).registerAdmin(...)` in `onLoad`; permesso in `plugin.yml` | `execute` sul main senza bloccare; l'I/O passa da future sul pool DB. Sottocomandi di piattaforma: `HealthSubcommand`, `TunableSubcommand` in `ARKPlugin` |
| Aggiungere un comando giocatore (`/pay`) | nel modulo: `ModuleCommand` + `CommandRegistry.register(...)`; permesso in `plugin.yml` | La piattaforma lo registra in Paper con `ModuleCommandAdapter` |
| Aggiungere una libreria esterna | `gradle/libs.versions.toml` + `core-platform/build.gradle.kts` + `relocate(...)` nello `shadowJar` | Verifica il jar (Task 9 del piano M0) |
| Cambiare cosa mostra `/breach health` | `platform/health/HealthReport.lines()` / `overall()`; righe di un modulo: `ARKModule.healthDetails()` | Raccolta dati: `HealthService.check()` |
| Cambiare dimensione/nomi dei thread | `platform/concurrent/PlatformExecutors` | `MARKET` resta sempre a 1 thread (single-writer) |
| Cambiare l'ordine di avvio/arresto | `platform/ARKPlugin.start()` / `onDisable()` | Arresto = inverso dell'avvio, DB per ultimo |
| Usare un servizio di piattaforma da un modulo | `context.services().require(X.class)` in `onLoad`, con X tra `SeasonService`, `PlayerService`, `AuditService`, `TunableService`, `JobScheduler`, `PlayerHooks`, `CommandRegistry`, `PlayerLock` | Registrati da `ARKPlugin.start()` prima dei moduli |

---

## 2. Moduli Gradle e regole di dipendenza

```
core-api        (nessuna dipendenza, niente Bukkit)
   ▲
core-domain     (api(core-api), niente Bukkit, niente I/O)
   ▲
core-platform   (core-api, core-domain, Hikari, Flyway, MariaDB driver; Paper compileOnly) → jar shadow
   ▲
module-economy  (api core-api, implementation core-domain + Caffeine; I7 imposto da breach.module-conventions)
   ▲
core-platform   (implementation module-economy: unico che collega i moduli, ModuleCatalog)
```

| File | Stato | Cosa contiene | Quando toccarlo |
| --- | --- | --- | --- |
| `settings.gradle.kts` | ✅ | repository (Maven Central, Paper), `include(...)`: `core-domain`, `core-api`, `core-platform`, `module-economy` | Aggiunta di un nuovo progetto Gradle |
| `gradle.properties` | ✅ | parallel, caching, `-Xmx2G` | Raramente |
| `gradle/libs.versions.toml` | ✅ | catalogo versioni (Paper bloccato, Hikari, Flyway, MariaDB, JUnit, Testcontainers, jqwik 1.10.1, Caffeine 3.3.0) | Aggiunta/aggiornamento librerie (versioni con ≥2 settimane) |
| `build-logic/src/main/kotlin/breach.java-conventions.gradle.kts` | ✅ | `java-library`, toolchain Java 25, UTF-8, `useJUnitPlatform()`; `BREACH_IT_JDBC_URL` come input dei task `Test` (D-13: niente esito "saltato" ripreso dalla cache); ogni task `Test` usa il build service `ItDatabaseLock` (`build-logic/src/main/kotlin/ItDatabaseLock.kt`, `maxParallelUsages = 1`, D-22): un solo task Test alla volta nella build | Regole comuni a tutti i moduli |
| `core-api/build.gradle.kts` | ✅ | solo JUnit (dal catalogo) + `junit-platform-launcher` | Mai aggiungere Paper qui |
| `core-domain/build.gradle.kts` | ✅ | `api(project(":core-api"))` + JUnit + jqwik (test) | Mai aggiungere Paper o librerie I/O |
| `build-logic/src/main/kotlin/breach.module-conventions.gradle.kts` | ✅ | Convention dei `module-*`: applica `breach.java-conventions` e **fallisce la configurazione** se `api`/`implementation`/`compileOnly`/`runtimeOnly` contengono un progetto diverso da `:core-api`/`:core-domain` (I7, D-21) | Mai allentarla; i test possono usare `testFixtures(project(":core-platform"))` |
| `module-economy/build.gradle.kts` | ✅ | `breach.module-conventions`; `api(core-api)`, `implementation(core-domain)`, Caffeine; test: testFixtures di `core-platform`, JUnit | Nuove librerie del modulo (da rilocare nello shadow di `core-platform`) |
| `core-platform/build.gradle.kts` | ✅ | dipendenze runtime, Paper compileOnly (+ testImplementation), plugin `java-test-fixtures` (`testFixturesApi(hikaricp)`, Flyway, Testcontainers, JUnit), `processResources` (espande `${version}` in `plugin.yml`), `shadowJar` (`filesMatching("META-INF/services/**")` con `DuplicatesStrategy.INCLUDE` + `mergeServiceFiles()`, esclude slf4j, relocation `hikari`, `flyway`, `mariadb`, `jackson` sotto `org.breachsmp.core.libs.*`), jar normale con classifier `plain` (D-28), `runServer`, task `verifyShadowJar` (agganciato a `check`/`build`: fallisce se i file di servizio Flyway/`java.sql.Driver` nel jar non contengono le voci rilocate attese) | Nuove librerie, nuovi `module-*`, relocation |

---

## 3. Flusso di avvio e arresto (`ARKPlugin`)

```
onEnable → start():
  1 saveDefaultConfig() + ConfigLoader.load(...)   valida tutto, fail-closed (ConfigException)
  2 ConfigLoader.readPassword(...)                 da secrets/db-password (D-09)
  3 DataSourceFactory.create(...)                  pool Hikari, fallisce se il DB non risponde
  4 Migrator.migrate()                             Flyway; rifiuta schema più nuovo (SchemaTooNewException)
  5 new PlatformExecutors(main, db, compute)       MAIN / DB / COMPUTE / MARKET + timer
  6 servizi di piattaforma nel ServiceRegistry:    CurrentSeason.load, JdbcPlayerService, JdbcAuditService,
                                                   TunableRegistry, JdbcJobScheduler, PlayerHookRegistry,
                                                   CommandHub, SerialPlayerLock
  7 ModuleRegistry.load(ModuleCatalog.all(), …)    onLoad dei moduli (registrano servizi, tunable, comandi, hook, job)
  8 TunableRegistry.reload()                       valori dal DB (bloccante, fail-closed)
  9 ModuleRegistry.enableAll(); CommandHub.close()
 10 registerEvents(PlayerListener)                 login fail-closed, hook di uscita
 11 registerCommand("breach", BreachCommand)       health + tunable + sottocomandi dei moduli
    registerCommand(<label>, ModuleCommandAdapter) per ogni comando dei moduli
 12 JdbcJobScheduler.start(timer); timer → TunableRegistry.reloadQuietly ogni 30 s
  errore in 1–12 → log SEVERE + disablePlugin(this) → onDisable()

onDisable (inverso):  modules.disableAll() → executors.close() (timer, poi drain) → dataSource.close()
```

---

## 4. `core-api` – contratti (niente Bukkit)

### `api/ARKApi.java` ✅
Interfaccia vuota, segnaposto per il punto d'accesso pubblico usato da plugin esterni. **Non usata in M0.** Toccarla quando un plugin esterno deve leggere servizi di BreachCore (probabile da M1: `ServiceRegistry services()`).

### `api/module/ARKModule.java` ✅ Task 2
Contratto di ogni modulo di dominio (= `BreachModule` della spec, D-01).

| Metodo | A cosa serve | Chi lo chiama | Quando toccarlo |
| --- | --- | --- | --- |
| `String id()` | Id stabile minuscolo; chiave `modules.<id>.enabled` | `ModuleRegistry`, `DependencyResolver` (via grafo) | Mai cambiare l'id di un modulo rilasciato |
| `void onLoad(ModuleContext)` | Registra servizi, prepara stato. Niente job/listener | `ModuleRegistry.load` (ordine di dipendenza) | – (contratto) |
| `void onEnable()` | Avvia job e listener; dipendenze già abilitate | `ModuleRegistry.enableAll` | – |
| `void onDisable()` | Drain e flush; ordine inverso. **Mai** `sync()`/`executors().main()` qui (lo scheduler Bukkit rifiuta task durante lo spegnimento) | `ModuleRegistry.disableAll` | – |
| `HealthStatus health()` | Stato per `/breach health`; **qualsiasi thread, niente Bukkit, niente I/O** | `ModuleRegistry.health` (dal thread DB) | – |
| `default List<String> healthDetails()` | Righe extra per `/breach health` (es. massa monetaria); **thread DB, query brevi ammesse** | `ModuleRegistry.details` | M1a |
| `default Collection<ModuleDependency> dependencies()` | Moduli da avviare prima; default nessuno | `ModuleRegistry.load` | Reso `default` in Task 2 |

### `api/module/ModuleContext.java` ✅ Task 2 (da classe vuota a interfaccia)
Ciò che la piattaforma dà a un modulo in `onLoad`. Implementazione: `platform/module/DefaultModuleContext`.

| Metodo | A cosa serve |
| --- | --- |
| `String moduleId()` | Id del modulo che riceve il contesto |
| `ServiceRegistry services()` | Registry condiviso dei servizi (I7) |
| `CoreExecutors executors()` | Executor MAIN/DB/COMPUTE/MARKET |
| `Logger logger()` | Logger del plugin (java.util.logging) |
| `<T> CompletableFuture<T> sync(Supplier<T>)` | Esegue sul main thread; unico modo per tornare sul main (I2) |
| `DataSource dataSource()` | Pool del DB del gioco (D-16); solo sul thread DB |

Quando toccarla: quando un modulo ha bisogno di una nuova capacità di piattaforma (es. registrare listener/comandi, config del modulo). Aggiungere metodi = aggiornare `DefaultModuleContext`.

### `api/module/HealthStatus.java` ✅
Enum `STARTING, HEALTHY, DEGRADED, FAILED, DISABLED`. Usato da `ARKModule.health()`, `ModuleRegistry.health()`, `HealthReport.overall()`. Se aggiungi un valore: aggiorna `HealthReport.overall()` e il colore in `BreachCommand`.

### `api/module/ModuleDependency.java` ✅ Task 2
Record `(String moduleId)`; il costruttore rifiuta id nullo/vuoto. `static ModuleDependency on(String)` per leggibilità: `List.of(ModuleDependency.on("economy"))`. Test: `ModuleDependencyTest`.

### `api/service/ServiceRegistry.java` ✅ Task 2
Registry tipizzato dei servizi tra moduli. Implementazione: `platform/service/SimpleServiceRegistry`.

| Metodo | A cosa serve |
| --- | --- |
| `<T> void register(Class<T>, T)` | Pubblica un servizio; doppia registrazione = `IllegalStateException` |
| `<T> Optional<T> find(Class<T>)` | Servizio opzionale |
| `<T> T require(Class<T>)` | Servizio obbligatorio; assente = `IllegalStateException` con messaggio esplicito |

### Servizi di piattaforma (M1a, D-15…D-18) – interfacce in `core-api`, implementazioni in `core-platform`
Un modulo li ottiene in `onLoad` con `context.services().require(X.class)`. Tutti registrati da `ARKPlugin` prima di caricare i moduli.

| Tipo (`api/…`) | A cosa serve | Implementazione |
| --- | --- | --- |
| `data/DataAccessException` | `RuntimeException` per errori SQL, messaggio italiano + causa | – |
| `util/Uuids` | `toBytes(UUID)` / `fromBytes(byte[])` per colonne `BINARY(16)` | – |
| `util/IdemKeys` | `of(String)` → UUID v3 deterministico per chiavi di job/rollback (E15) | – |
| `util/JsonText` | `write(Map<String,?>)` → JSON piatto (testo, interi, booleani, null; niente decimali) per `audit_log.detail` e `ledger_tx.meta` | – |
| `player/PlayerProfile` (record) | `uuid, name, firstSeen, lastSeen` | – |
| `player/PlayerService` | `findByName` (senza maiuscole, il più recente), `find(uuid)`; future sul thread DB | `platform/player/JdbcPlayerService` ✅ |
| `player/PlayerHooks`, `LoginHook`, `QuitHook` | Hook dei moduli: login sul thread asincrono (I/O ok, eccezione = rifiuto), uscita sul main (niente I/O) | `platform/player/PlayerHookRegistry` ✅ |
| `season/SeasonService` | `currentSeasonId()` | `platform/season/CurrentSeason` ✅ |
| `audit/AuditEntry`, `AuditService` | Audit append-only: `record` (async), `recordNow` (bloccante, solo da thread DB/login) | `platform/audit/JdbcAuditService` ✅ |
| `tunable/Tunable`, `TunableService` | `registerLong(key, default, min, max, descrizione)` in `onLoad`; `get()` in memoria | `platform/tunable/TunableRegistry` ✅ |
| `job/Job`, `NextRun`, `JobScheduler` | `register(jobId, nextRun, job)`: job sul thread DB con lease | `platform/job/JdbcJobScheduler` ✅ |
| `command/Tone`, `ChatLine`, `Notice`, `CommandResult`, `CommandInvocation` | Tipi neutri per i comandi: righe con tono, avvisi ad altri giocatori, mittente (`senderId` null = console), permessi extra | – |
| `command/ModuleCommand`, `AdminSubcommand`, `CommandRegistry` | Comandi giocatore (`/pay`) e sottocomandi `/breach x` dei moduli; `execute` sul main senza bloccare | `platform/command/CommandHub` ✅ |
| `lock/PlayerLock` | `withLock(uuid, azione)`: operazioni dello stesso giocatore una alla volta (spec §15) | `platform/lock/SerialPlayerLock` ✅ |

### `api/economy/` ✅ (M1b Task 1) – contratti del ledger (spec §5)
Unico servizio pubblico dell'economia: `LedgerService` (D-31). Freeze, storico, give/take/rollback restano interni a `module-economy`.

| Tipo | A cosa serve |
| --- | --- |
| `LedgerService` | `execute(TxRequest)` → `CompletableFuture<TxResult>` (thread DB); `balance(AccountRef)` (conto assente = 0); `subscribe(Consumer<LedgerTxCommitted>)` (dopo il COMMIT, thread DB) |
| `TxRequest` (record) | `idemKey` (I4), `kind`, `legs` (somma 0, conti distinti), `meta` (JSON piatto, `null` → vuota), `actor` (`null` = console/sistema) |
| `Leg` (record) | `account` + `delta` in unità minime |
| `TxResult` (record) | `txId`, `idemKey`, `kind`, `createdAt`, `entries` (`EntryView`: conto, delta, saldo dopo), `replayed`; `balanceAfter(AccountRef)` |
| `AccountRef` (record) | `type` (`OwnerType` PLAYER/TEAM/SYSTEM) + `owner` UUID; `player(uuid)`, `system(SystemAccount)`, `mayGoNegative()` (solo FAUCET); `toString` = `giocatore:<uuid>` / `sistema:SINK` |
| `SystemAccount` (enum) | FAUCET, SINK, TAX_POOL, ESCROW con `ref()` = `new UUID(0, 1..4)` (D-19) |
| `TxKinds` | `PAY`, `ADMIN_GIVE`, `ADMIN_TAKE`, `ROLLBACK`, `STARTING_BALANCE`; `KNOWN`; `bypassesFreeze(kind)` (tutti tranne `PAY`) |
| `LedgerTxCommitted` (record) | Evento con il `TxResult` |
| `LedgerException` e sottoclassi | `InsufficientFundsException(account)`, `AccountFrozenException(account)`, `IdempotencyConflictException`, `InvalidTransactionException` |

Quando toccarlo: nuovo tipo di transazione → costante in `TxKinds` (+ `KNOWN`, e `FREEZE_BYPASS` se è admin/sistema).

### `api/concurrent/CoreExecutors.java` ✅ Task 2
Gli executor della spec §3: `Executor main()`, `db()`, `compute()`, `market()`. Implementazione: `platform/concurrent/PlatformExecutors`. Regola: I/O solo su `db()`, calcolo puro su `compute()`, matching azioni solo su `market()`, Bukkit API solo su `main()`.

---

## 5. `core-domain` – logica pura

### `domain/module/DependencyResolver.java` ✅ Task 3
Ordinamento topologico dei moduli (spec §3 "ordine di avvio segue `dependsOn`").

| Metodo | A cosa serve | Note |
| --- | --- | --- |
| `static List<String> order(Map<String, ? extends Collection<String>> graph)` | Restituisce gli id in ordine: ogni modulo dopo le sue dipendenze; a parità, alfabetico (deterministico) | Dipendenza mancante → `DependencyException("Il modulo 'x' dipende da 'y', che non è registrato o è disabilitato")`; ciclo → `DependencyException("Ciclo di dipendenze tra moduli: a -> b -> a")` |
| `private static List<String> findCycle(...)` | Ricostruisce il percorso del ciclo per il messaggio d'errore | Solo uso interno |

Chi lo chiama: `ModuleRegistry.load`. Test: `DependencyResolverTest`.

### `domain/module/DependencyException.java` ✅ Task 3
`RuntimeException` per grafo dei moduli non valido. Fa fallire l'avvio (D-08).

### `domain/economy/`, `domain/ratelimit/`, `domain/schedule/` ✅ (M1b Task 2)

| Classe | Metodi | Chi la usa |
| --- | --- | --- |
| `economy/Money` | `UNITS_PER_COIN = 100` (E1); `long parse(String)`: cifre con ≤ 2 decimali, `,` o `.`, niente migliaia, > 0, altrimenti `IllegalArgumentException` col messaggio per l'utente; `String format(long)`: `1.234,56 monete`, `1 moneta`, decimali solo se ≠ 0 | comandi di `module-economy` |
| `economy/FeePolicy` | `fee(amount, bp)` = `ceil(amount * bp / 10000)` (bp in [0, 10000]); `split(amount, bp)` → `Split(fee, net)` | `/pay` (tassa trasferimenti) |
| `economy/LedgerRules` | `validate(TxRequest)` (tipo noto, 2–255 gambe, delta ≠ 0, conti distinti, somma 0, meta JSON valida) → `InvalidTransactionException`; `balancesAfter(List<Posting>, bypassFreeze)`: prima il freeze (`AccountFrozenException`), poi saldo < 0 su conto che non può → `InsufficientFundsException`. Record `AccountState(id, ref, balance, frozen)`, `Posting(account, delta)` | `JdbcLedgerService`, test di proprietà |
| `ratelimit/SlidingWindowLimiter` | `SlidingWindowLimiter(Clock, Duration)`; `tryAcquire(UUID, limit)` (thread-safe, limite passato a ogni chiamata); `forget(UUID)` | `/pay` |
| `schedule/Schedules` | `nextDaily(now, hour)`: prossima `hour:00` strettamente dopo `now` | job di riconciliazione |

Test: `MoneyTest`, `FeePolicyTest`, `LedgerRulesTest`, `SlidingWindowLimiterTest`, `SchedulesTest`.

**Test di proprietà (jqwik, DoD M1)** – `core-domain/src/test/.../economy/LedgerProperties` + helper `InMemoryLedger` (stesse regole del ledger JDBC: `validate` → ordine per id → `balancesAfter`): 300 sequenze casuali fino a 200 operazioni (give, pay con tassa, take) → somma dei saldi sempre 0, nessun saldo negativo tranne FAUCET, una transazione rifiutata non cambia nulla; tassa = ceiling esatto; `Money.parse` legge ciò che un importo semplice dice.

---

## 6. `core-platform` – bootstrap e infrastruttura

### `platform/ARKPlugin.java` ✅
`JavaPlugin` principale (`main` in `plugin.yml`). Vedi §3 per il flusso.

| Metodo | A cosa serve | Quando toccarlo |
| --- | --- | --- |
| `onEnable()` | Chiama `start()`; su `RuntimeException`, `LinkageError` o `ServiceConfigurationError` (relocation/linkage rotti) logga SEVERE e disabilita il plugin (fail-closed) | Raramente |
| `onDisable()` | Arresto in ordine inverso: moduli → executor → pool DB; tollera avvio parziale (campi null) | Nuove risorse da chiudere |
| `private start()` | Sequenza di avvio 1–9 | Nuovi componenti di piattaforma |

### `platform/config/` ✅ Task 6

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `CoreConfig` (record) | Config validata: `configVersion`, `database`, `modules` | `boolean moduleEnabled(String id)` → `false` se assente (opt-in, D-08) |
| `DatabaseConfig` (record) | `host, port, name, user, passwordFile (Path assoluto), poolSize, maxLifetimeSeconds` | `String jdbcUrl()` → `jdbc:mariadb://host:port/name`; `int effectivePoolSize(int cores)` → `poolSize` se > 0, altrimenti `min(10, cores*2)` |
| `ConfigLoader` | Lettura YAML (SnakeYAML di Paper, `SafeConstructor`, chiavi duplicate vietate: `setAllowDuplicateKeys(false)` -> `ConfigException` "Impossibile leggere config.yml: ...") + validazione | `static CoreConfig load(Path file, Path dataDir)`; `static CoreConfig parse(Object root, Path dataDir)` (package-private, raccoglie **tutti** gli errori); `static String readPassword(Path)`; `CURRENT_VERSION = 1` |
| `ConfigException` | Errore di configurazione con la lista completa degli errori | `List<String> errors()` |

Chiavi `config.yml` (default in `core-platform/src/main/resources/config.yml`):

| Chiave | Tipo / vincolo | Default |
| --- | --- | --- |
| `config_version` | int, deve essere `1` | `1` |
| `database.host` / `.name` / `.user` | testo non vuoto | `localhost` / `breach_game` / `bc_game` |
| `database.port` | 1–65535 | `3306` |
| `database.password_file` | percorso relativo alla cartella del plugin | `secrets/db-password` |
| `database.pool_size` | 0–100 (`0` = automatico) | `0` |
| `database.max_lifetime_seconds` | 30–28000 (sotto il `wait_timeout` di MariaDB) | `1500` |
| `modules.<id>.enabled` | boolean; id `[a-z][a-z0-9-]*` | sezione vuota |

Test: `ConfigLoaderTest` (incluso: chiavi duplicate rifiutate; il `config.yml` spedito nel jar è valido).

### `platform/db/` ✅

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `DataSourceFactory` | Pool HikariCP secondo spec §4 | `static HikariConfig hikariConfig(DatabaseConfig, String password, int cores)` (testabile senza DB); `static HikariDataSource create(...)` |
| `Migrator` | Flyway su `classpath:db/migration`, `cleanDisabled(true)` | `int migrate()` → migrazioni eseguite ora; lancia `SchemaTooNewException` se il DB ha migrazioni sconosciute al jar (stati `FUTURE_*`) |
| `SchemaTooNewException` | Il plugin non parte su uno schema più nuovo del codice (spec §4) | – |

Impostazioni Hikari: nome pool `breach-db`, driver `org.mariadb.jdbc.Driver`, `maximumPoolSize = minimumIdle = effectivePoolSize`, `maxLifetime` da config, `connectionTimeout` 5 s, `initializationFailTimeout` 5 s (fail-closed), `REPEATABLE READ`, `useServerPrepStmts`/`cachePrepStmts`.

Test: `DataSourceFactoryTest` (unit), `MigratorIT` (integrazione, DB da `TestDatabase`), `TestDatabaseTest`.

**`TestDatabase`** (testFixtures di `core-platform`: `core-platform/src/testFixtures/java/org/breachsmp/core/platform/testkit/`, D-13/D-22): punto unico da cui ogni `*IT` ottiene MariaDB. I `module-*` lo usano con `testImplementation(testFixtures(project(":core-platform")))`.

| Metodo | A cosa serve |
| --- | --- |
| `static HikariDataSource migrated()` | `open()` + Flyway `clean` + tutte le migrazioni (`Migrator`): per gli IT che non testano le migrazioni |
| `static HikariDataSource open()` | DB esterno se `BREACH_IT_JDBC_URL` (+ `BREACH_IT_DB_USER`, `BREACH_IT_DB_PASSWORD`) è impostata; altrimenti container `mariadb:11.4` condiviso (Testcontainers) se c'è Docker; altrimenti `assumeTrue` → test saltato. Schema esterno non `*_it` → `IllegalStateException` |
| `static boolean isDisposableSchema(String jdbcUrl)` | `true` se lo schema dell'URL finisce con `_it` (i test fanno Flyway `clean`) |

Quando toccarlo: nuovi IT. Chi scrive un IT: `dataSource = TestDatabase.migrated()` (o `open()` per testare le migrazioni) in `@BeforeEach`, `close()` null-guardato in `@AfterEach`.

**`MutableClock`** (stesso package `testkit`): `Clock` in UTC controllabile nei test – `MutableClock(LocalDateTime start)`, `set(LocalDateTime)`, `advance(Duration)`.

### `platform/season/`, `platform/player/`, `platform/audit/` ✅ (M1a Task 4)

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `CurrentSeason` (record, implementa `SeasonService`) | Stagione corrente | `static CurrentSeason load(DataSource)`: bloccante, solo all'avvio; `current_season` vuota → `IllegalStateException` (fail-closed) |
| `JdbcPlayerService` (implementa `PlayerService`) | Tabella `player` | `JdbcPlayerService(DataSource, Executor db, Clock)`; `void upsert(UUID, String)` bloccante (login): `INSERT … ON DUPLICATE KEY UPDATE name, last_seen`; `findByName` (`ORDER BY last_seen DESC LIMIT 1`, collation `_ci`), `find(uuid)` sul thread DB |
| `JdbcAuditService` (implementa `AuditService`) | Tabella `audit_log`, append-only | `JdbcAuditService(DataSource, Executor db, Clock)`; `record`/`recordNow`; `static void insert(Connection, AuditEntry, Instant)` per scrivere nella transazione del chiamante |

Test: `CurrentSeasonIT`, `JdbcPlayerServiceIT`, `JdbcAuditServiceIT` (MariaDB reale via `TestDatabase.migrated()`).

**Login e uscita (M1a Task 7)** – flusso: `PlayerListener.onPreLogin` (`AsyncPlayerPreLoginEvent`, priorità HIGH, saltato se già rifiutato) → `LoginGate.admit` → `JdbcPlayerService.upsert` → `PlayerHookRegistry.runLogin` (hook dei moduli in ordine). Qualsiasi errore → `disallow(KICK_OTHER, LoginGate.KICK_MESSAGE)` (fail-closed). Uscita: `PlayerQuitEvent` (MONITOR) → `PlayerHookRegistry.runQuit` (un hook che fallisce non ferma gli altri).

| Classe | Ruolo |
| --- | --- |
| `PlayerHookRegistry` (implementa `PlayerHooks`) | `runLogin(uuid, name)`: primo errore → `LoginRejectedException` con l'id del modulo; `runQuit(uuid)`: errori loggati WARNING |
| `LoginGate` | `LoginGate(BiConsumer<UUID,String> upsert, PlayerHookRegistry, Logger)`; `Optional<String> admit(uuid, name)` |
| `LoginRejectedException` | Hook di login fallito |
| `PlayerListener` | Listener Paper (unica classe Bukkit del flusso) |

### `platform/lock/SerialPlayerLock.java` ✅ (M1a Task 7)
Implementa `PlayerLock` con una coda FIFO per giocatore (`ConcurrentHashMap<UUID, future di coda>`): la mappa contiene solo le code attive. Un'azione che lancia rilascia comunque il lock. Test: `SerialPlayerLockTest`.

### `platform/tunable/` ✅ (M1a Task 5)

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `TunableRegistry` (implementa `TunableService`) | Tunable interi (D-17) in memoria + tabella `tunable` | `TunableRegistry(DataSource, Executor db, Clock, Logger)`; `registerLong` (chiave `[a-z][a-z0-9_.]{0,63}`, default in [min, max], duplicato → `IllegalStateException`); `reload()` bloccante (assente → default, valore non valido → default + WARNING; usato all'avvio, fail-closed); `reloadQuietly()` (timer ogni 30 s); `CompletableFuture<TunableChange> set(key, raw, actor)`: valida, poi upsert + audit `TUNABLE_SET {old,new}` nella **stessa** transazione (D-18); `list()` ordinata per chiave; `find(key)` |
| `TunableView` (record) | `key, value, defaultValue, min, max, description` | – |
| `TunableChange` (record) | `now` (`TunableView`), `previous` | – |

Test: `TunableRegistryTest` (senza DB), `TunableRegistryIT`.

### `platform/job/JdbcJobScheduler.java` ✅ (M1a Task 6)
Implementa `JobScheduler` su `scheduled_job` con lease (spec §9, E19).

| Metodo | A cosa serve |
| --- | --- |
| `JdbcJobScheduler(DataSource, Executor db, Clock, String processId, Logger)` | `processId` finisce in `locked_by` |
| `static String newProcessId()` | `"breach-" + UUID` |
| `register(jobId, nextRun, job)` | Solo prima di `start` (poi `IllegalStateException`); id 1–64 caratteri, unico |
| `start(ScheduledExecutorService timer)` | Bloccante: `INSERT … ON DUPLICATE KEY UPDATE job_id = job_id` (un job esistente mantiene `next_run_at`), poi controllo ogni 60 s sul timer → lavoro sul thread DB |
| `List<String> tick()` | Per ogni job: lease con `UPDATE … WHERE next_run_at <= now AND (locked_until IS NULL OR locked_until < now)` (lease 10 min); se preso esegue, poi rilascia con `last_run_at`, `last_status` (`OK`/`FAILED`), `last_error` (≤ 255) e il prossimo `next_run_at`. Restituisce gli id eseguiti |
| `List<String> healthLines()` | `Ultimo job: <id> <yyyy-MM-dd HH:mm:ss> <stato> [(<errore>)]`, `Ultimo job: nessuno` o `non disponibile` |

Le date sono `LocalDateTime.now(clock)` (ora locale del server). Test: `JdbcJobSchedulerIT` (due processi → una sola esecuzione; lease scaduto ripreso; job fallito registrato).

### `platform/concurrent/PlatformExecutors.java` ✅
Implementa `CoreExecutors` e `AutoCloseable`.

| Metodo | A cosa serve |
| --- | --- |
| `PlatformExecutors(Executor main, int dbThreads, int computeThreads)` | `main` = scheduler Bukkit (in test: `Runnable::run`); DB = pool fisso (= dimensione pool Hikari), COMPUTE fisso, MARKET **1 thread** |
| `main()/db()/compute()/market()` | Executor della spec §3; thread daemon `breach-db-N`, `breach-compute-N`, `breach-market-1` |
| `Map<String,Integer> queueDepths()` | Task in coda per `db`, `compute`, `market` (in quest'ordine) per `/breach health` |
| `ScheduledExecutorService timer()` | Timer di piattaforma, 1 thread daemon `breach-timer` (D-26): **solo** per passare lavoro agli altri pool a intervalli (scheduler job, ricarica tunable) |
| `close()` | `shutdownNow` del timer, poi shutdown in ordine market → compute → db con attesa max 10 s ciascuno (poi `shutdownNow`); un interrupt del chiamante **non** salta il drain dei pool successivi, il flag è ripristinato alla fine (D-28) |

Test: `PlatformExecutorsTest`.

### `platform/service/SimpleServiceRegistry.java` ✅
Implementa `ServiceRegistry` con `ConcurrentHashMap<Class<?>, Object>`. Test: `SimpleServiceRegistryTest`.

### `platform/module/` ✅

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `DefaultModuleContext` (record) ✅ | Implementa `ModuleContext`; componenti `(moduleId, services, executors, logger, dataSource)` | `sync(Supplier)` = `CompletableFuture.supplyAsync(task, executors.main())` |
| `ModuleRegistry` ✅ | Ciclo di vita dei moduli; catalogo immutabile assegnato in `load()` (letto da altri thread in `health()`) | `load(Collection<? extends ARKModule>, Predicate<String> isEnabled, Function<String, ModuleContext>)`: id duplicati → `IllegalStateException`; ordina con `DependencyResolver`; `onLoad` in ordine. `enableAll()`: `onEnable` in ordine, errore → `ModuleStartupException`. `disableAll()`: `onDisable` in ordine inverso solo sui moduli abilitati, un errore non ferma gli altri. `List<String> order()`. `Map<String, HealthStatus> health()`: abilitati → `health()` (eccezione → `FAILED`), caricati non abilitati → `STARTING`, altri → `DISABLED` |
| `ModuleStartupException` ✅ | Errore in `onLoad`/`onEnable`; il messaggio contiene l'id del modulo | – |
| `ModuleCatalog` ✅ | **Unico posto** che elenca le implementazioni dei moduli (I7) | `static List<ARKModule> all()` → in M0 `List.of()` |

Test: `DefaultModuleContextTest`, `ModuleRegistryTest`.

### `platform/health/` ✅

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `HealthReport` (record) | Fotografia: `databaseUp`, `modules` (ordinati), `details` (righe dei moduli e della piattaforma), `queueDepths` | `HealthStatus overall()`: DB giù o modulo `FAILED` → `FAILED`; `DEGRADED`/`STARTING` → `DEGRADED`; altrimenti `HEALTHY` (`DISABLED` ignorato). `List<String> lines()`: stato, database, moduli, **dettagli**, code |
| `HealthService` | Raccoglie il report sul thread DB | `HealthService(ModuleRegistry, DataSource, PlatformExecutors, Supplier<List<String>> platformDetails)`; `check()`: `details` = `modules.details()` + `platformDetails` (es. `JdbcJobScheduler::healthLines`); `pingDatabase()` (package-private) |

`ModuleRegistry.details()`: `healthDetails()` dei moduli abilitati in ordine di avvio; un'eccezione diventa `Modulo <id>: dettagli non disponibili (<msg>)`.

Test: `HealthReportTest`, `HealthServiceTest` (DataSource finti via `java.lang.reflect.Proxy`).

### `platform/command/` ✅ (riscritto in M1a Task 8, D-15)
I moduli scrivono comandi con i tipi neutri di `api/command`; qui vengono adattati a Paper. Ogni risposta torna sul main con `whenCompleteAsync(..., main)` (I2).

| Classe | Ruolo |
| --- | --- |
| `BreachCommand` (`BasicCommand`) | `/breach <sottocomando>`: `BreachCommand(Collection<AdminSubcommand>, CommandDelivery, Server)`. Sottocomando sconosciuto o assente → WARNING con `usage(visibili)` (solo quelli permessi); senza permesso → ERROR; altrimenti `delivery.deliver` con gli argomenti senza il nome. `static suggestions(args, subs, has, online)`: primo argomento = nomi permessi per prefisso, poi delega al sottocomando. `PERMISSION = "breach.command"` |
| `ModuleCommandAdapter` (`BasicCommand`) | Comando di primo livello di un modulo (`/pay`, `/balance`): `playerOnly` dalla console → ERROR; `permission()` del modulo; suggerimenti con i nomi online |
| `CommandDelivery` | `deliver(sender, supplier)`: righe al mittente, `Notice` ai giocatori online (`server.getPlayer`), eccezione → log WARNING + `Errore: <describe>`. Statici: `describe(Throwable)` (scarta `CompletionException`), `granted(perms, has)`, `invocation(sender, args, extra)` (`senderId` null = console), `onlinePlayerNames(server)` |
| `CommandHub` (implementa `CommandRegistry`) | Raccoglie i comandi dei moduli: label/alias `[a-z][a-z0-9_-]*` univoci; sottocomandi admin univoci e non riservati (`RESERVED = health, tunable`); dopo `close()` ogni registrazione → `IllegalStateException`. `commands()`, `adminSubcommands()` |
| `ChatRenderer` | `ChatLine` → `Component`: INFO bianco, SUCCESS verde, WARNING giallo, ERROR rosso, MUTED grigio |
| `HealthSubcommand` | `/breach health` (`breach.admin.health`); `static toResult(HealthReport)`: prima riga SUCCESS/WARNING/ERROR, le altre MUTED |
| `TunableSubcommand` | `/breach tunable [list \| get <chiave> \| set <chiave> <valore>]` (`breach.admin.tunable`); `set` → `TunableRegistry.set` con actor = mittente; valore rifiutato → `Valore non accettato: …` |

Test: `BreachCommandTest`, `CommandDeliveryTest`, `CommandHubTest`, `HealthSubcommandTest`, `TunableSubcommandTest`.

---

## 7. Risorse di `core-platform`

| File | Stato | Contenuto | Quando toccarlo |
| --- | --- | --- | --- |
| `src/main/resources/plugin.yml` | ✅ (`api-version: '26.2'` accettato da Paper 26.2, smoke test 2026-10-22) | `name: BreachCore`, `main`, `version` (espansa da Gradle), `api-version: '26.2'`, permessi `breach.command`, `breach.admin.health`, `breach.admin.tunable` (tutti `op`) | Nuovi permessi/sottocomandi |
| `src/main/resources/config.yml` | ✅ Task 6 | Config di default (vedi tabella §6) | Nuove chiavi |
| `src/main/resources/db/migration/V001__platform_create_player.sql` | ✅ Task 7 | Tabella `player` (globale) | **Mai** dopo l'applicazione |

Migrazioni applicate (aggiorna a ogni nuova migrazione):

| Versione | File | Modulo | Contenuto |
| --- | --- | --- | --- |
| V001 | `V001__platform_create_player.sql` | platform | `player(uuid, name, first_seen, last_seen, flags)` |
| V002 | `V002__platform_season.sql` | platform | `season(id, name, started_at, ended_at)`, `current_season(singleton=1, season_id)` con stagione 1, indice `ix_player_name` |
| V004 | `V004__economy_ledger.sql` | economy | `account(id, season_id, owner_type, owner_ref, balance, version, frozen_at)` con `uq_owner` e CHECK saldo ≥ 0 tranne SYSTEM; `ledger_tx(id, season_id, idem_key UNIQUE, kind, actor, created_at, meta JSON)`; `ledger_entry(tx_id, seq, account_id, delta, balance_after)` con FK |
| V003 | `V003__platform_audit_tunable_job.sql` | platform | `audit_log(id, at, actor, action, subject, detail JSON)`, `tunable(tkey, value, updated_at, updated_by)`, `scheduled_job(job_id, next_run_at, locked_by, locked_until, last_run_at, last_status, last_error)` |

Test di risorse: `PluginYmlTest` (Task 9) verifica che `main` punti a `ARKPlugin` e che la versione sia stata espansa.

---

## 8. `module-economy` – ledger, conti, comandi economici (M1b)
Percorsi abbreviati: `economy/` = `module-economy/src/main/java/org/breachsmp/core/economy/`. Dipende solo da `core-api`, `core-domain`, Caffeine (I7). Niente Paper (D-15).

### `economy/ledger/` ✅ (M1b Task 4)

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `AccountDao` | Tabella `account` | `AccountDao(DataSource)`; `ensure(season, refs)` / `ensure(season, ref)`: crea i conti mancanti in **autocommit** (`INSERT … ON DUPLICATE KEY UPDATE id = id` + `SELECT id`), id in cache (D-20); `read(season, ref)` → `Optional<Snapshot(id, balance, version, frozen)>`; `lockForUpdate(c, sortedIds)`: un solo `SELECT … WHERE id IN (?, …) ORDER BY id FOR UPDATE`; `updateBalances(c, locked, balances)`: **unico** `UPDATE account SET balance` del codice (I3), `version + 1`; `setFrozen(id, frozen, at)` → `true` se è cambiato |
| `LedgerDao` (statico) | `ledger_tx`, `ledger_entry` (solo INSERT/SELECT) | `insertTx(c, season, request, createdAt)` → id, oppure vuoto su chiave duplicata (errore 1062); `insertEntries(c, txId, locked, legs, balances)` (`seq` 1..n); `findByIdemKey`, `findById` → `StoredTx`; `history(c, accountId, limit, offset)` dal più recente |
| `LockedAccount` (record) | `LedgerRules.AccountState` + `version` | – |
| `StoredTx` (record) | Transazione salvata con le entry in ordine di `seq` | `toResult(replayed)` |
| `HistoryRow` (record) | `txId, createdAt, kind, delta, balanceAfter` | – |

Test: `AccountDaoIT` (creazione idempotente anche con 16 thread in parallelo, ordine del lock, freeze).

**`JdbcLedgerService`** (implementa `LedgerService`, M1b Task 5) – algoritmo D-20:
1. `LedgerRules.validate` (niente DB se la richiesta è malformata);
2. `AccountDao.ensure` in autocommit (conti creati in modo pigro, anche quelli di sistema, D-19);
3. `BEGIN`; `LedgerDao.insertTx`: chiave già usata → rollback e replay (`StoredTx.toResult(true)`), `kind` diverso → `IdempotencyConflictException`;
4. `AccountDao.lockForUpdate` (gambe ordinate per id del conto);
5. `LedgerRules.balancesAfter` (freeze saltato per `TxKinds.bypassesFreeze`);
6. `updateBalances` + `insertEntries`; `COMMIT`;
7. dopo il commit: `BalanceCache.offer` con la nuova `version`, poi `LedgerTxCommitted` ai listener (errori loggati).
SQLException 1213/1205/1020 → fino a 3 retry con jitter (`deadlockRetries()` le conta); altre → `LedgerException`. Le eccezioni di dominio escono dopo il rollback.

| Metodo | A cosa serve |
| --- | --- |
| `JdbcLedgerService(DataSource, Executor db, SeasonService, AccountDao, BalanceCache, Clock, Logger)` | – |
| `execute(request)` / `executeNow(request)` | Async sul pool DB / bloccante (login, job, `EconomyAdmin`) |
| `balance(ref)` / `balanceNow(ref)` | Dalla cache se presente, altrimenti dal DB (aggiorna la cache); conto assente = 0 |
| `subscribe(listener)`, `deadlockRetries()` | – |

**`LedgerConcurrencyIT`** (DoD M1): 20 giocatori, 1000 transazioni casuali (70% pay, 15% take, 15% give) + 100 doppi invii con la stessa chiave, tutte in parallelo sul pool DB; verifica una riga per chiave riuscita, somma dei saldi 0, nessun giocatore negativo, SINK ≥ 0, saldo = somma delle entry, ogni transazione a somma 0. Logga riuscite / insufficienti / retry.

**`BalanceCache`** – Caffeine per `AccountRef` (`expireAfterAccess` 30 min, max 10.000, D-29): `get`, `offer(ref, version, balance)` (vince la versione più alta), `evict`.

Test: `JdbcLedgerServiceIT` (11: movimenti ed entry, saldo insufficiente senza tracce, idempotenza e conflitto di tipo, freeze vs tipi admin, SINK non negativo, richiesta invalida senza DB, evento e cache, conto assente = 0, meta e actor, versioni della cache). Helper di test `module-economy/src/test/.../LedgerFixture` (DB migrato, executor, `MutableClock`, ledger reale; `give`, `tx`, `dbBalance`, `count`, `queryString`, `queryBytes`, `execute`).

### `economy/admin/` ✅ (M1b Task 7)

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `EconomyAdmin` | Operazioni dello staff (spec §14, E20). Ogni operazione = **un** task sul pool DB con soli metodi bloccanti; audit dopo il commit, un audit fallito → SEVERE (D-18). Motivo obbligatorio (1–200 caratteri) e importo > 0 controllati in modo **sincrono** | `EconomyAdmin(DataSource, Executor db, SeasonService, AccountDao, JdbcLedgerService, AuditService, Clock, Logger)`; `give(actor, target, amount, reason)` = `ADMIN_GIVE` FAUCET → giocatore, audit `ECO_GIVE {amount, reason, tx}`; `take(...)` = `ADMIN_TAKE` giocatore → SINK (fallisce se non basta), audit `ECO_TAKE`; `rollback(actor, txId, reason)`: solo stagione corrente, non su un `ROLLBACK`, gambe invertite, chiave `IdemKeys.of("rollback:<id>")` (D-25), audit `TX_ROLLBACK` solo se non replay; `freeze`/`unfreeze(actor, target, reason)` → `true` se cambiato, audit `ACCOUNT_FREEZE`/`ACCOUNT_UNFREEZE`; `history(player, page)` (pagine da 10, dal più recente); `moneySupplyNow()` = `-FAUCET - SINK` |
| `RollbackRefusedException` (`LedgerException`) | Rollback non ammesso, nessun movimento | – |

Test: `EconomyAdminIT` (8).

### `economy/recon/ReconciliationJob.java` ✅ (M1b Task 8, DoD)
Implementa `Job` (id `economy.reconciliation`, registrato ogni notte all'ora del tunable `economy.recon_hour` con `Schedules.nextDaily`). `runNow()` (bloccante, thread DB): in **una** transazione di sola lettura (stessa fotografia REPEATABLE READ) trova i conti con `balance ≠ SUM(ledger_entry.delta)` e la somma di tutti i saldi della stagione; per ogni discrepanza log SEVERE + audit `RECON_MISMATCH {account, stored, computed}`; conto non di sistema → congelato (`AccountDao.setFrozen`); somma ≠ 0 → SEVERE + audit `RECON_TOTAL`. `lastReport()`, `systemProblem()` (discrepanza su conto di sistema o somma ≠ 0 → modulo DEGRADED, E14), `healthLines()` (`Riconciliazione: mai eseguita | OK (<n> conti, dd/MM HH:mm) | <k> discrepanze (…)[, somma saldi <x>]`). Record `Mismatch(accountId, account, stored, computed)`, `Report(at, accounts, totalBalance, mismatches)` con `clean()`.

Test: `ReconciliationIT` (4: pulito, giocatore manomesso → congelato e `/pay` bloccato, conto di sistema manomesso → non congelato ma `systemProblem`, job eseguito dallo scheduler alle 04:00).

