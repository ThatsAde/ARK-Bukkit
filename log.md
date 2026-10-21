# log.md – Storico modifiche e decisioni

> Voci in ordine cronologico inverso: **la prima voce è lo stato attuale**.
> Formato voce: `## YYYY-MM-DD – titolo`, poi *Cosa*, *File*, *Verifica* (comandi + esito reale), *Decisioni*, *Aperto*.
> Le decisioni sono numerate `D-xx` e non si rinumerano mai; se una decisione cambia, se ne scrive una nuova che la sostituisce ("sostituisce D-xx").

---

## Stato attuale

- **Milestone in corso:** M0 – Scheletro (piano: `docs/superpowers/plans/2026-10-21-m0-scheletro.md`). Task 0, Task 1 e Task 2 completati.
- **Prossimo passo:** Task 3 del piano M0 (`core-domain`: `DependencyResolver`).
- **Bloccanti noti:** Docker non installato (test di integrazione saltati); serve un MariaDB raggiungibile per lo smoke test finale di M0 (Task 9).

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
