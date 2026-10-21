# log.md – Storico modifiche e decisioni

> Voci in ordine cronologico inverso: **la prima voce è lo stato attuale**.
> Formato voce: `## YYYY-MM-DD – titolo`, poi *Cosa*, *File*, *Verifica* (comandi + esito reale), *Decisioni*, *Aperto*.
> Le decisioni sono numerate `D-xx` e non si rinumerano mai; se una decisione cambia, se ne scrive una nuova che la sostituisce ("sostituisce D-xx").

---

## Stato attuale

- **Milestone in corso:** M0 – Scheletro (piano: `docs/superpowers/plans/2026-10-21-m0-scheletro.md`). Task 0, Task 1, Task 2, Task 3, Task 4, Task 5 e Task 6 completati.
- **Prossimo passo:** Task 7 del piano M0 (`core-platform`: `platform/db/`, DataSourceFactory, Migrator, V001).
- **Bloccanti noti:** Docker non installato (test di integrazione saltati); serve un MariaDB raggiungibile per lo smoke test finale di M0 (Task 9).

---

## 2026-10-21 – Task 6 – configurazione validata (config.yml)

**Cosa**
- `ConfigLoader.load(file, dataDir)`: legge `config.yml` con SnakeYAML (`SafeConstructor`, quello di Paper) e valida tutto raccogliendo **tutti** gli errori in una sola `ConfigException`. Errori coperti: file mancante, YAML malformato, radice non mappa, `config_version` diversa da `1`, sezione `database` mancante, valori mancanti/fuori intervallo (`port` 1–65535, `pool_size` 0–100, `max_lifetime_seconds` 30–28000), `modules` non mappa, id modulo non valido, `modules.<id>.enabled` non booleano.
- `ConfigLoader.readPassword(path)`: legge la password dal file, toglie spazi e a capo finali; file mancante o vuoto → `ConfigException` con messaggio esplicito.
- `DatabaseConfig` (record): `jdbcUrl()` → `jdbc:mariadb://host:port/name`; `effectivePoolSize(cores)` → `poolSize` se > 0, altrimenti `min(10, cores*2)`. Il percorso della password è risolto rispetto alla cartella del plugin.
- `CoreConfig` (record): `configVersion`, `database`, `modules` (copia immutabile); `moduleEnabled(id)` → `false` se il modulo non è configurato (D-08).
- `config.yml` di default in `core-platform/src/main/resources/`: `database` con `localhost`/`breach_game`/`bc_game`, `password_file: secrets/db-password`, `modules: {}`.

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/config/ConfigException.java`, `…/config/DatabaseConfig.java`, `…/config/CoreConfig.java`, `…/config/ConfigLoader.java`, `core-platform/src/main/resources/config.yml`, test `core-platform/src/test/java/org/breachsmp/core/platform/config/ConfigLoaderTest.java` (15 test).
- Modificati: `map.md` (§6 `platform/config/` → ✅, §7 `config.yml` → ✅), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-platform:test --tests "org.breachsmp.core.platform.config.ConfigLoaderTest"` con solo il test scritto → `:core-platform:compileTestJava FAILED`, `20 errors`, `cannot find symbol ... class ConfigException / CoreConfig / DatabaseConfig`, `ConfigLoader`, `BUILD FAILED in 1s`.
- GREEN: stesso comando → `BUILD SUCCESSFUL in 3s`; `ConfigLoaderTest` tests=15 failures=0 errors=0.
- Suite completa: `.\gradlew.bat :core-platform:test` → `BUILD SUCCESSFUL in 2s`; `ConfigLoaderTest` 15, `ModuleRegistryTest` 13, `PlatformExecutorsTest` 6, `SimpleServiceRegistryTest` 4, `DefaultModuleContextTest` 1 (totale 39, 0 failure, 0 error).

**Decisioni**
- Nessuna decisione nuova: implementazione come da piano (Task 6), con D-08 (moduli opt-in, fail-closed) e D-09 (password solo da file).

**Aperto**
- Le modifiche a `config.yml` richiedono riavvio (il reload a caldo arriverà con i primi tunable).
- `ConfigLoader.parse(Object, Path)` è package-private e testato solo indirettamente tramite `load`.

---

## 2026-10-21 – Task 5 – ciclo di vita dei moduli

**Cosa**
- `ModuleRegistry(Logger)`: `load(modules, isEnabled, contextFactory)` costruisce il grafo dei moduli abilitati, lo ordina con `DependencyResolver.order` e chiama `onLoad` in ordine; id duplicati o `load` chiamato due volte → `IllegalStateException`. Moduli disabilitati da config: non caricati, loggati, riportati `DISABLED`.
- `enableAll()`: `onEnable` in ordine di caricamento, al primo errore si ferma con `ModuleStartupException`. `disableAll()`: `onDisable` in ordine inverso solo sui moduli abilitati; un errore viene loggato e non ferma gli altri.
- `health()`: abilitati → `health()` (eccezione o `null` → `FAILED`), caricati ma non abilitati → `STARTING`, altri → `DISABLED`.
- `ModuleStartupException extends RuntimeException`: messaggio `onLoad|onEnable del modulo '<id>' fallito: …`, causa originale preservata.
- `ModuleCatalog.all()`: in M0 restituisce `List.of()`; è l'unico punto che elenca i moduli (I7).

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/module/ModuleRegistry.java`, `…/module/ModuleStartupException.java`, `…/module/ModuleCatalog.java`, test `core-platform/src/test/java/org/breachsmp/core/platform/module/ModuleRegistryTest.java` (13 test).
- Modificati: `map.md` (§6: heading `platform/module/` ✅, `ModuleRegistry`, `ModuleStartupException`, `ModuleCatalog` → ✅), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-platform:test --tests "org.breachsmp.core.platform.module.ModuleRegistryTest"` con solo il test scritto → `:core-platform:compileTestJava` fallito, `4 errors`, `cannot find symbol ... class ModuleRegistry` / `class ModuleStartupException`, `BUILD FAILED in 2s`.
- GREEN: `.\gradlew.bat :core-platform:test` → `BUILD SUCCESSFUL in 3s`; report: `ModuleRegistryTest` tests=13 failures=0 errors=0; `PlatformExecutorsTest` tests=6; `DefaultModuleContextTest` tests=1; `SimpleServiceRegistryTest` tests=4 (totale 24, tutti verdi).

**Decisioni**
- Nessuna decisione nuova: implementazione come da piano (Task 5) e spec §3 / D-08 (fail-closed).

**Aperto**
- `ModuleCatalog.all()` è vuoto finché non esistono i moduli di dominio (`module-*`); `ARKPlugin` (Task 9) lo userà.

---

## 2026-10-21 – Task 4 – registry dei servizi, executor, contesto del modulo

**Cosa**
- `SimpleServiceRegistry`: registry in memoria su `ConcurrentHashMap`; registrare due volte lo stesso tipo → `IllegalStateException`; `require` su tipo mancante → `IllegalStateException` con il nome del tipo e un suggerimento (modulo abilitato e dichiarato in `dependencies()`).
- `PlatformExecutors`: implementa `CoreExecutors` e `AutoCloseable`. `main` è fornito dall'esterno; `db` (N thread), `compute` (N thread) e `market` (1 thread, single-writer) sono pool a coda illimitata con thread daemon nominati `breach-db-N`, `breach-compute-N`, `breach-market-1`. Rifiuta 0 thread. `queueDepths()` restituisce la lunghezza delle code in ordine db, compute, market. `close()` ferma market → compute → db, con attesa massima di 10 s per pool.
- `DefaultModuleContext` (record): implementa `ModuleContext`; `sync(task)` = `CompletableFuture.supplyAsync(task, executors.main())`. Argomenti nulli rifiutati nel costruttore compatto.

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/service/SimpleServiceRegistry.java`, `core-platform/src/main/java/org/breachsmp/core/platform/concurrent/PlatformExecutors.java`, `core-platform/src/main/java/org/breachsmp/core/platform/module/DefaultModuleContext.java`, più i tre test: `SimpleServiceRegistryTest` (4 test), `PlatformExecutorsTest` (6 test), `DefaultModuleContextTest` (1 test).
- Modificati: `map.md` (§6: `PlatformExecutors`, `SimpleServiceRegistry`, `DefaultModuleContext` → ✅; heading `platform/module/` ⏳ Task 5), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-platform:test` con solo i test scritti → `:core-platform:compileTestJava FAILED`, `16 errors`, `cannot find symbol ... class PlatformExecutors / SimpleServiceRegistry / DefaultModuleContext`, `BUILD FAILED in 4s`.
- GREEN: `.\gradlew.bat :core-platform:test` → `BUILD SUCCESSFUL in 9s`; report: `PlatformExecutorsTest` tests=6 failures=0 errors=0; `DefaultModuleContextTest` tests=1 failures=0 errors=0; `SimpleServiceRegistryTest` tests=4 failures=0 errors=0 (totale 11).

**Decisioni**
- Nessuna decisione nuova: implementazione come da piano (Task 4) e spec §3.

**Aperto**
- Nessuno per questo task. `ModuleRegistry`, `ModuleStartupException` e `ModuleCatalog` arrivano nel Task 5.

---

## 2026-10-21 – Task 3 – ordinamento dei moduli per dipendenze

**Cosa**
- `DependencyResolver.order(graph)`: ordinamento topologico (Kahn) dei moduli; a parità di livello ordine alfabetico, quindi avvio deterministico. Funzione pura, nessuna dipendenza da Bukkit o I/O.
- Dipendenza mancante → `DependencyException` con messaggio esplicito; ciclo (anche auto-dipendenza) → `DependencyException` con il percorso del ciclo (`a -> b -> a`), ricostruito da `findCycle`.
- Dipendenze duplicate contate una volta sola (`TreeSet`).
- `DependencyException extends RuntimeException`: fa fallire l'avvio (fail-closed, D-08).

**File**
- Creati: `core-domain/src/main/java/org/breachsmp/core/domain/module/DependencyResolver.java`, `core-domain/src/main/java/org/breachsmp/core/domain/module/DependencyException.java`, `core-domain/src/test/java/org/breachsmp/core/domain/module/DependencyResolverTest.java` (8 test).
- Modificati: `map.md` (§5: le due voci ⏳ → ✅), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-domain:test` con solo il test scritto → `:core-domain:compileTestJava FAILED`, `11 errors`, `cannot find symbol ... variable DependencyResolver` / `class DependencyException`, `BUILD FAILED in 3s`.
- GREEN: `.\gradlew.bat :core-domain:test` → `BUILD SUCCESSFUL in 2s`; report `DependencyResolverTest`: `tests="8" skipped="0" failures="0" errors="0"`.

**Decisioni**
- Nessuna decisione nuova: algoritmo e messaggi di errore come da piano (Task 3) e spec §3.

**Aperto**
- Nessuno per questo task. `ModuleRegistry` (che chiama `order`) arriva nel Task 5.

---

## 2026-10-21 – Task 2 – contratti di modulo, servizi, executor

**Cosa**
- `ARKModule`: `dependencies()` diventa `default` (lista vuota); Javadoc del ciclo di vita (ordine di dipendenza, fail-closed).
- `ModuleDependency`: il costruttore compatto rifiuta id nullo o vuoto; aggiunto `static on(String)`.
- `ModuleContext`: da classe vuota a interfaccia (`moduleId()`, `services()`, `executors()`, `logger()`, `sync(Supplier)`).
- Nuove: `service/ServiceRegistry` (`register`, `find`, `require`) e `concurrent/CoreExecutors` (`main`, `db`, `compute`, `market`).
- Nessun codice fuori da `core-api` usava questi tipi: nessun effetto sugli altri moduli.

**File**
- Modificati: `core-api/.../module/ARKModule.java`, `core-api/.../module/ModuleDependency.java`, `core-api/.../module/ModuleContext.java`, `map.md` (§4), `log.md`.
- Creati: `core-api/.../service/ServiceRegistry.java`, `core-api/.../concurrent/CoreExecutors.java`, `core-api/src/test/java/org/breachsmp/core/api/module/ModuleDependencyTest.java`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-api:test` con solo il test scritto → `:core-api:compileTestJava FAILED`, `ModuleDependencyTest.java:22: error: cannot find symbol ... method on(String) location: class ModuleDependency`, `BUILD FAILED`.
- GREEN: `.\gradlew.bat :core-api:test` → `BUILD SUCCESSFUL`; report `ModuleDependencyTest`: `tests="3" skipped="0" failures="0" errors="0"`.

**Decisioni**
- Nessuna decisione nuova: contratti implementati come da piano (Task 2) e spec §3.

**Aperto**
- Nessuno per questo task. Le implementazioni (`SimpleServiceRegistry`, `PlatformExecutors`, `DefaultModuleContext`) arrivano nei Task 4–5.

---

## 2026-10-21 – Task 1 – versioni bloccate

**Cosa**: catalogo `gradle/libs.versions.toml` con Paper `26.2.build.134-stable` (non più dinamico), JUnit 5.14.4, Testcontainers 1.21.4, HikariCP 7.1.0, Flyway 13.9.0 (+ `flyway-mysql`), MariaDB 3.5.10; aggiunti `junit-platform-launcher` e `testcontainers-junit`. Dipendenze nei tre `build.gradle.kts` come da piano.

**File**: `gradle/libs.versions.toml`, `core-api/build.gradle.kts`, `core-domain/build.gradle.kts`, `core-platform/build.gradle.kts`.

**Verifica**: `.\gradlew.bat build` → BUILD SUCCESSFUL; `.\gradlew.bat :core-platform:dependencies --configuration runtimeClasspath` → HikariCP 7.1.0, flyway-core 13.9.0, flyway-mysql 13.9.0, mariadb-java-client 3.5.10.

**Dipendenze transitive di runtime (per le relocation del Task 9)**
- `com.zaxxer:HikariCP:7.1.0` → `org.slf4j:slf4j-api:2.0.17` (fornito da Paper: da escludere/non impacchettare)
- `org.flywaydb:flyway-core:13.9.0` → `com.fasterxml.jackson.core:jackson-annotations:2.22`
- `org.flywaydb:flyway-mysql:13.9.0` → `flyway-core` (già presente)
- `org.mariadb.jdbc:mariadb-java-client:3.5.10` → nessuna transitiva

**Aperto**: nessuno.

---

## 2026-10-21 – Analisi della spec, documentazione di lavoro e piano M0

**Cosa**
- Letta la spec `BreachCore – Specifica tecnica del core plugin.md` (v0.1).
- Analizzato lo scheletro esistente: 3 moduli Gradle (`core-api`, `core-domain`, `core-platform`) + `build-logic`; in `core-api` esistono `ARKApi` (vuota), `ARKModule`, `HealthStatus`, `ModuleContext` (classe vuota), `ModuleDependency`. `core-domain` e `core-platform` senza sorgenti.
- Verificato che `.\gradlew.bat build` passa (BUILD SUCCESSFUL, nessun test).
- Verificate sul jar reale le API Paper 26.2 usate nel piano (`JavaPlugin.registerCommand(String, String, BasicCommand)`, `BasicCommand`, `CommandSourceStack`; `getDataPath()` NON esiste → si usa `getDataFolder().toPath()`), Flyway 13.9.0 (`MigrationState.FUTURE_SUCCESS/FUTURE_FAILED`, `MigrateResult.migrationsExecuted`, MariaDB in `flyway-mysql`) e HikariCP 7.1.0.
- Creati `CLAUDE.md`, `log.md`, `map.md`, `docs/roadmap.md`, `docs/superpowers/plans/2026-10-21-m0-scheletro.md`.

**File**: `CLAUDE.md`, `log.md`, `map.md`, `docs/roadmap.md`, `docs/superpowers/plans/2026-10-21-m0-scheletro.md` (nessun file di codice toccato).

**Verifica**: `.\gradlew.bat build` → BUILD SUCCESSFUL (prima di qualsiasi modifica).

**Decisioni**
- **D-01 Nomi:** si mantiene il prefisso `ARK` già presente nel codice. `BreachModule` della spec = `ARKModule`. Plugin in `plugin.yml`: `name: BreachCore`. Classe principale: `org.breachsmp.core.platform.ARKPlugin`.
- **D-02 Piattaforma bloccata:** Paper API `26.2.build.134-stable` (era `26.2.build.+`, dinamico), Java 25 (toolchain). `api-version: '26.2'` in `plugin.yml` – da confermare allo smoke test (Task 9).
- **D-03 Librerie (tutte con ≥2 settimane di anzianità al 2026-10-21):** HikariCP `7.1.0`, Flyway `13.9.0` + `flyway-mysql 13.9.0`, MariaDB Connector/J `3.5.10`, JUnit `5.14.4` (+ `junit-platform-launcher`, obbligatorio con Gradle 9), Testcontainers `1.21.4`. SnakeYAML NON si include: lo fornisce Paper (`snakeyaml 2.2`, compileOnly). slf4j NON si include: lo fornisce Paper. Caffeine, jqwik, ArchUnit entrano nella milestone che li usa (M1+).
- **D-04 Migrazioni:** una sola sequenza globale Flyway in `core-platform/src/main/resources/db/migration/`, file `V001__<modulo>_<descrizione>.sql`. Niente range di versioni per modulo (Flyway segnalerebbe le migrazioni "out of order").
- **D-05 Test di integrazione:** classi `*IT` con `@Testcontainers(disabledWithoutDocker = true)`: senza Docker vengono saltate. Docker al momento non è installato → la verifica DB di M0 passa dallo smoke test manuale.
- **D-06 Comandi:** `plugin.yml` classico (non `paper-plugin.yml`); `/breach` registrato con `JavaPlugin.registerCommand(...)` + `BasicCommand` di Paper. Permessi: `breach.command` (accesso), `breach.admin.health` (sottocomando).
- **D-07 Config:** `config_version: 1`, validazione che raccoglie TUTTI gli errori e li mostra insieme. Il ricaricamento a caldo è rimandato alla prima milestone con impostazioni ricaricabili (le impostazioni DB richiedono comunque un riavvio).
- **D-08 Fail-closed in avvio:** qualsiasi errore in config, DB, migrazioni, `onLoad`/`onEnable` di un modulo → il plugin si disabilita da solo con messaggio esplicito. Un modulo non configurato in `modules.<id>.enabled` è **disabilitato** (opt-in).
- **D-09 Segreti:** password DB letta da `plugins/BreachCore/secrets/db-password` (percorso configurabile `database.password_file`, relativo alla cartella del plugin). Mai in `config.yml`, mai nei log.
- **D-10 Bootstrap bloccante:** pool DB e migrazioni girano sincroni in `onEnable` (eccezione documentata a I2: il server è in avvio, nessun giocatore connesso).
- **D-11 Shadow:** relocation di Hikari, Flyway, driver MariaDB e loro dipendenze sotto `org.breachsmp.core.libs.*`, con `mergeServiceFiles()` (Flyway e il driver usano `ServiceLoader`). slf4j escluso dal jar.
- **D-12 ArchUnit:** in M0 non serve: i confini tra `core-api`/`core-domain`/`core-platform` sono già imposti dal classpath Gradle (nessuno dei due vede Paper). Entra quando arrivano i `module-*` (regola I7) e in M10 (muro I6).

**Aperto**
- Repository git non inizializzato: il Task 0 del piano lo inizializza (serve per i commit dei task).
- Docker assente: installare Docker Desktop per eseguire i test `*IT`, oppure accettare la verifica manuale.
- Serve un MariaDB (locale o remoto) per lo smoke test di M0.
- Decisioni aperte della spec §18 (disconnessione in zona, costi tier, soglia Redis, governance Banca) restano aperte: non toccano M0.
