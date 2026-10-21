# map.md – Mappa del codice BreachCore

> **Scopo:** sapere sempre *dove* sta una cosa, *a cosa serve*, *chi la usa* e *quando va toccata*, senza esplorare il codice a tentoni.
> **Regola:** ogni modifica al codice aggiorna questa mappa nello stesso commit (vedi `CLAUDE.md` §1).
>
> Legenda stato: ✅ esiste nel codice · ⏳ da creare (indicato il task del piano M0 `docs/superpowers/plans/2026-10-21-m0-scheletro.md`) · 🔁 esiste ma il task indicato la modifica.
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
| Aggiungere un sottocomando `/breach x` | `platform/command/BreachCommand` (`execute` + `SUBCOMMANDS`) + permesso in `plugin.yml` | I/O sempre fuori dal main, risposta con `whenCompleteAsync(..., main)` |
| Aggiungere una libreria esterna | `gradle/libs.versions.toml` + `core-platform/build.gradle.kts` + `relocate(...)` nello `shadowJar` | Verifica il jar (Task 9 del piano M0) |
| Cambiare cosa mostra `/breach health` | `platform/health/HealthReport.lines()` / `overall()` | Raccolta dati: `HealthService.check()` |
| Cambiare dimensione/nomi dei thread | `platform/concurrent/PlatformExecutors` | `MARKET` resta sempre a 1 thread (single-writer) |
| Cambiare l'ordine di avvio/arresto | `platform/ARKPlugin.start()` / `onDisable()` | Arresto = inverso dell'avvio, DB per ultimo |

---

## 2. Moduli Gradle e regole di dipendenza

```
core-api        (nessuna dipendenza, niente Bukkit)
   ▲
core-domain     (api(core-api), niente Bukkit, niente I/O)
   ▲
core-platform   (core-api, core-domain, Hikari, Flyway, MariaDB driver; Paper compileOnly) → jar shadow
   ▲
module-*        (futuri: solo core-api [+ core-domain])
```

| File | Stato | Cosa contiene | Quando toccarlo |
| --- | --- | --- | --- |
| `settings.gradle.kts` | ✅ | repository (Maven Central, Paper), `include(...)` dei moduli | Aggiunta di un nuovo progetto Gradle |
| `gradle.properties` | ✅ | parallel, caching, `-Xmx2G` | Raramente |
| `gradle/libs.versions.toml` | ✅ | catalogo versioni (Paper bloccato, Hikari, Flyway, MariaDB, JUnit, Testcontainers) | Aggiunta/aggiornamento librerie (versioni con ≥2 settimane) |
| `build-logic/src/main/kotlin/breach.java-conventions.gradle.kts` | ✅ | `java-library`, toolchain Java 25, UTF-8, `useJUnitPlatform()` | Regole comuni a tutti i moduli |
| `core-api/build.gradle.kts` | ✅ | solo JUnit (dal catalogo) + `junit-platform-launcher` | Mai aggiungere Paper qui |
| `core-domain/build.gradle.kts` | ✅ | `api(project(":core-api"))` + JUnit | Mai aggiungere Paper o librerie I/O |
| `core-platform/build.gradle.kts` | ✅ | dipendenze runtime, Paper compileOnly (+ testImplementation), `processResources` (espande `${version}` in `plugin.yml`), `shadowJar` (`filesMatching("META-INF/services/**")` con `DuplicatesStrategy.INCLUDE` + `mergeServiceFiles()`, esclude slf4j, relocation `hikari`, `flyway`, `mariadb`, `jackson` sotto `org.breachsmp.core.libs.*`), `runServer`, task `verifyShadowJar` (agganciato a `check`/`build`: fallisce se i file di servizio Flyway/`java.sql.Driver` nel jar non contengono le voci rilocate attese) | Nuove librerie, nuovi `module-*`, relocation |

---

## 3. Flusso di avvio e arresto (`ARKPlugin`)

```
onEnable → start():
  1 saveDefaultConfig()                      copia config.yml se manca
  2 ConfigLoader.load(...)                   valida tutto, fail-closed (ConfigException)
  3 ConfigLoader.readPassword(...)           da secrets/db-password (D-09)
  4 DataSourceFactory.create(...)            pool Hikari, fallisce se il DB non risponde
  5 Migrator.migrate()                       Flyway; rifiuta schema più nuovo (SchemaTooNewException)
  6 new PlatformExecutors(main, db, compute) MAIN / DB / COMPUTE / MARKET
  7 ModuleRegistry.load(ModuleCatalog.all(), config::moduleEnabled, ctxFactory)
  8 ModuleRegistry.enableAll()
  9 registerCommand("breach", ..., new BreachCommand(new HealthService(...), main))
  errore in 1–9 → log SEVERE + disablePlugin(this) → onDisable()

onDisable (inverso):  modules.disableAll() → executors.close() → dataSource.close()
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
| `void onDisable()` | Drain e flush; ordine inverso | `ModuleRegistry.disableAll` | – |
| `HealthStatus health()` | Stato per `/breach health`; **qualsiasi thread, niente Bukkit, niente I/O** | `ModuleRegistry.health` (dal thread DB) | – |
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

Test: `DataSourceFactoryTest` (unit), `MigratorIT` (Testcontainers, saltato senza Docker: su questa macchina 3 test skipped).

### `platform/concurrent/PlatformExecutors.java` ✅
Implementa `CoreExecutors` e `AutoCloseable`.

| Metodo | A cosa serve |
| --- | --- |
| `PlatformExecutors(Executor main, int dbThreads, int computeThreads)` | `main` = scheduler Bukkit (in test: `Runnable::run`); DB = pool fisso (= dimensione pool Hikari), COMPUTE fisso, MARKET **1 thread** |
| `main()/db()/compute()/market()` | Executor della spec §3; thread daemon `breach-db-N`, `breach-compute-N`, `breach-market-1` |
| `Map<String,Integer> queueDepths()` | Task in coda per `db`, `compute`, `market` (in quest'ordine) per `/breach health` |
| `close()` | Shutdown in ordine market → compute → db, attesa max 10 s ciascuno, poi `shutdownNow` |

Test: `PlatformExecutorsTest`.

### `platform/service/SimpleServiceRegistry.java` ✅
Implementa `ServiceRegistry` con `ConcurrentHashMap<Class<?>, Object>`. Test: `SimpleServiceRegistryTest`.

### `platform/module/` ✅

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `DefaultModuleContext` (record) ✅ | Implementa `ModuleContext` | `sync(Supplier)` = `CompletableFuture.supplyAsync(task, executors.main())` |
| `ModuleRegistry` ✅ | Ciclo di vita dei moduli | `load(Collection<? extends ARKModule>, Predicate<String> isEnabled, Function<String, ModuleContext>)`: id duplicati → `IllegalStateException`; ordina con `DependencyResolver`; `onLoad` in ordine. `enableAll()`: `onEnable` in ordine, errore → `ModuleStartupException`. `disableAll()`: `onDisable` in ordine inverso solo sui moduli abilitati, un errore non ferma gli altri. `List<String> order()`. `Map<String, HealthStatus> health()`: abilitati → `health()` (eccezione → `FAILED`), caricati non abilitati → `STARTING`, altri → `DISABLED` |
| `ModuleStartupException` ✅ | Errore in `onLoad`/`onEnable`; il messaggio contiene l'id del modulo | – |
| `ModuleCatalog` ✅ | **Unico posto** che elenca le implementazioni dei moduli (I7) | `static List<ARKModule> all()` → in M0 `List.of()` |

Test: `DefaultModuleContextTest`, `ModuleRegistryTest`.

### `platform/health/` ✅ Task 8

| Classe | Ruolo | Metodi |
| --- | --- | --- |
| `HealthReport` (record) | Fotografia: `databaseUp`, `modules` (ordinati), `queueDepths` | `HealthStatus overall()`: DB giù o modulo `FAILED` → `FAILED`; `DEGRADED`/`STARTING` → `DEGRADED`; altrimenti `HEALTHY` (`DISABLED` ignorato). `List<String> lines()`: righe per la chat |
| `HealthService` | Raccoglie il report sul thread DB | `CompletableFuture<HealthReport> check()`; `boolean pingDatabase()` (package-private, `isValid(2)`) |

Test: `HealthReportTest`, `HealthServiceTest` (DataSource finti via `java.lang.reflect.Proxy`).

### `platform/command/BreachCommand.java` ✅ Task 8
`BasicCommand` di Paper per `/breach` (registrato in `ARKPlugin.start()` con `registerCommand`).

| Metodo | A cosa serve |
| --- | --- |
| `execute(CommandSourceStack, String[])` | `health` → `health(sender)`; altrimenti messaggio d'uso |
| `private health(CommandSender)` | Controlla `breach.admin.health`, chiama `HealthService.check()`, risponde sul main (`whenCompleteAsync(..., main)`) con colore per stato |
| `suggest(...)` / `static List<String> suggestions(String[])` | Tab-completion dei sottocomandi |
| `static String describe(Throwable)` | Testo d'errore per il sender: scarta il wrapper `CompletionException` (usa la causa), fallback al nome semplice della classe |
| `permission()` | `breach.command` (accesso al comando) |

Costanti: `PERMISSION = "breach.command"`, `PERMISSION_HEALTH = "breach.admin.health"`, `SUBCOMMANDS = List.of("health")`. Test: `BreachCommandTest` (`suggestions`, `describe`).

---

## 7. Risorse di `core-platform`

| File | Stato | Contenuto | Quando toccarlo |
| --- | --- | --- | --- |
| `src/main/resources/plugin.yml` | ✅ (`api-version` non verificato su Paper reale) | `name: BreachCore`, `main`, `version` (espansa da Gradle), `api-version: '26.2'`, permessi | Nuovi permessi/sottocomandi |
| `src/main/resources/config.yml` | ✅ Task 6 | Config di default (vedi tabella §6) | Nuove chiavi |
| `src/main/resources/db/migration/V001__platform_create_player.sql` | ✅ Task 7 | Tabella `player` (globale) | **Mai** dopo l'applicazione |

Migrazioni applicate (aggiorna a ogni nuova migrazione):

| Versione | File | Modulo | Contenuto |
| --- | --- | --- | --- |
| V001 | `V001__platform_create_player.sql` | platform | `player(uuid, name, first_seen, last_seen, flags)` |

Test di risorse: `PluginYmlTest` (Task 9) verifica che `main` punti a `ARKPlugin` e che la versione sia stata espansa.
