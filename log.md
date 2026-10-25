# log.md – Storico modifiche e decisioni

> Voci in ordine cronologico inverso: **la prima voce è lo stato attuale**.
> Formato voce: `## YYYY-MM-DD – titolo`, poi *Cosa*, *File*, *Verifica* (comandi + esito reale), *Decisioni*, *Aperto*.
> Le decisioni sono numerate `D-xx` e non si rinumerano mai; se una decisione cambia, se ne scrive una nuova che la sostituisce ("sostituisce D-xx").

---

## Stato attuale

- **Milestone chiusa:** M0 – Scheletro ✅ (DoD verificata end-to-end il 2026-10-22 su Paper 26.2 + MariaDB 11.4.13).
- **Milestone in corso:** M2a – Territorio (claim, Tool Cupboard, PIN, protezione, tier, martello), piano `docs/superpowers/plans/2026-10-25-m2a-territorio.md`, branch `m2a-territorio`. M1 è chiusa e unita in `main`.
- **Prossimo passo:** M2a Task 6.
- **Bloccanti noti:** nessuno. Docker resta assente, ma gli IT girano sul MariaDB portatile (D-13, comandi in `CLAUDE.md` §5).

---

## 2026-10-25 – M2a – Task 5 – regole di protezione

**Cosa**
- `core-domain/territory/ProtectionRules`: il claim protegge solo ciò che è costruito (piazzare, rompere la struttura, porte, redstone, martello solo autorizzati; terreno e contenitori liberi); danni esterni, pistoni e fluidi.

**File**
- `core-domain/src/main/java/org/breachsmp/core/domain/territory/ProtectionRules.java`; test `ProtectionRulesTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-domain:test` → `package ProtectionRules does not exist`.
- GREEN: `.\gradlew.bat :core-domain:test` → `BUILD SUCCESSFUL`; `ProtectionRulesTest` 7/7 (incluso `pistonMayNotCrossAClaimBoundary`, Review Focus 4).

**Decisioni**
- Nessuna nuova (aggiunta la costante `NO_CLAIM = 0`).

**Aperto**
- Nessuno.

---

## 2026-10-25 – M2a – Task 4 – registro dei blocchi piazzati e hash del PIN

**Cosa**
- `core-domain/territory`: `PlacedBlockSet` (array ordinato di posizioni impacchettate per il PDC del chunk, D-39), `PinHash` (sale + hash, copie difensive) e `PinHasher` (validazione 4–8 cifre, PBKDF2WithHmacSHA256 60.000 iterazioni, verifica a tempo costante, D-37).

**File**
- `core-domain/src/main/java/org/breachsmp/core/domain/territory/{PlacedBlockSet,PinHash,PinHasher}.java`; test `PlacedBlockSetTest`, `PinHasherTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-domain:test` → `cannot find symbol` (`PinHasher`).
- GREEN: `.\gradlew.bat :core-domain:test` → `BUILD SUCCESSFUL`; `PlacedBlockSetTest` 3, `PinHasherTest` 2.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-25 – M2a – Task 3 – geometria e indice dei claim

**Cosa**
- `core-domain/territory`: `ClaimBox` (cubo attorno al cupboard, contenimento, intersezione con distanza minima, distanza dal cupboard) e `ClaimIndex<T>` (indice per chunk portato da ToolCupboards su coordinate intere, il più vicino vince).

**File**
- `core-domain/src/main/java/org/breachsmp/core/domain/territory/{ClaimBox,ClaimIndex}.java`; test `ClaimBoxTest`, `ClaimIndexTest`, `ClaimIndexPerformanceTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-domain:test` → `cannot find symbol` (`ClaimBox`).
- GREEN: `.\gradlew.bat :core-domain:test` → `BUILD SUCCESSFUL`; `ClaimBoxTest` 4, `ClaimIndexTest` 2, `ClaimIndexPerformanceTest` 1.
- **Benchmark (budget spec §14 ≤ 50 µs):** 5.000 claim di raggio 16 su 4.000×4.000 blocchi, 200.000 lookup casuali → **media 360 ns**.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-25 – M2a – Task 2 – contratti del territorio in `core-api`

**Cosa**
- `api/territory/ClaimView` (cubo attorno al cupboard) e `TerritoryService` (`claimAt`, `isAuthorized`, `tierOf`, `hpOfTier`) per M3.

**File**
- `core-api/src/main/java/org/breachsmp/core/api/territory/{ClaimView,TerritoryService}.java`; test `ClaimViewTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-api:test` → `cannot find symbol` (`ClaimView`).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 182 test, 0 skipped.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-25 – M2a – Task 1 – `core-paper-api` e `PaperBridge`

**Cosa**
- Nuovo progetto `core-paper-api` con `PaperBridge` (plugin, registrazione dei listener, cartella dati), implementato in `core-platform` da `BukkitPaperBridge` e registrato da `ARKPlugin` nel `ServiceRegistry`.
- `breach.module-conventions` ammette `:core-paper-api`; `CLAUDE.md` §2 aggiornato.

**File**
- `core-paper-api/build.gradle.kts`, `core-paper-api/src/main/java/org/breachsmp/core/paper/PaperBridge.java`, `core-platform/.../paper/BukkitPaperBridge.java`, `ARKPlugin.java`, `settings.gradle.kts`, `core-platform/build.gradle.kts`, `build-logic/src/main/kotlin/breach.module-conventions.gradle.kts`, `CLAUDE.md`, `map.md`, `log.md`.

**Verifica**
- `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 180 test, 0 skipped.
- Prova negativa I7: `implementation(project(":core-platform"))` in `module-economy` → `I7: :module-economy non può dipendere da :core-platform in 'implementation' (ammessi solo :core-api, :core-domain e :core-paper-api)`; riga tolta.

**Decisioni** (dal piano M2a)
- **D-34** Nuovo progetto `core-paper-api` con `PaperBridge` (`plugin()`, `registerListener(Listener)`, `dataFolder()`), registrato dalla piattaforma. I `module-*` possono dipendere da `:core-paper-api` e da Paper `compileOnly` (aggiorna D-15 e D-21: resta vietato dipendere da `core-platform` o da altri moduli).
- **D-35** Un claim rimosso si cancella dal DB (`claim` + `claim_member`); la storia sta in `audit_log` (`CLAIM_REMOVE`).
- **D-36** Modifiche a claim, autorizzati e PIN: prima il DB, poi l'indice in memoria sul main. Alla creazione il claim è riservato subito nell'indice; se la scrittura fallisce la riserva si toglie e il cupboard torna al giocatore.
- **D-37** PIN 4–8 cifre, PBKDF2WithHmacSHA256 60.000 iterazioni, sale 16 byte, verifica sul pool COMPUTE; tentativi limitati per giocatore e per claim (`territory.pin_attempts_per_10_min`, default 5); errori → `audit_log` `TC_PIN_FAILED`.
- **D-38** Config del territorio in `plugins/BreachCore/territory.yml` (tier, materiali, porte, upgrade, item, ricette, mondi), validata fail-closed con tutti gli errori. Tunable: `territory.claim_radius` (16), `territory.claim_gap` (0), `territory.max_cupboards_per_player` (50), `territory.pin_attempts_per_10_min` (5). Il raggio si salva nel claim alla creazione.
- **D-39** Registro "piazzato da un giocatore" nel PDC del chunk (`breachcore:placed`, `INTEGER_ARRAY` ordinato di posizioni impacchettate), con cache per i chunk caricati e PDC riscritto a ogni modifica.
- **D-40** Nel DB il mondo è identificato dal suo UUID (`BINARY(16)`).
- **D-41** In M2a il martello migliora solo blocchi pieni; porte, botole e cancelli hanno un tier ma non si migliorano. Il danno ai blocchi (e il suo azzeramento con l'upgrade) arriva con M2b.
- **D-42** Blocco del cupboard: `BARREL` di default; l'interazione è sempre intercettata, il barile non si apre mai come contenitore.

**Aperto**
- Nessuno.

---

## 2026-10-25 – M1 – chiusura (ledger, conti, audit, tunable, `/pay`, `/balance`)

**Cosa**
- Chiusi i piani M1a (10 task) e M1b (12 task). Roadmap: M1 ✅; aggiunta la milestone M1-shop (negozio server, design M1 §3) e la decisione aperta su Vault/ChestShop nelle note di M4 (D-33). `CLAUDE.md` §2 aggiornato (`module-economy`, `breach.module-conventions`).

**File**
- `docs/roadmap.md`, `CLAUDE.md`, `map.md`, `log.md`.

**Verifica – Definition of Done di M1 (spec §17)**
- Test di proprietà verdi: `LedgerProperties` 3/3 (conservazione del denaro su 300 sequenze casuali, tassa = ceiling, importi).
- 1000 transazioni concorrenti senza violare le invarianti: `LedgerConcurrencyIT` verde, skipped=0 — ultima esecuzione: 960 riuscite, 41 saldo insufficiente, 0 retry per deadlock.
- Riconciliazione notturna che rileva un saldo manomesso: `ReconciliationIT` 4/4 (incluso il job eseguito dallo scheduler alle 04:00).
- `.\gradlew.bat clean build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; **180 test, 0 failure, 0 error, 0 skipped**; `verifyShadowJar` passato. Smoke test su Paper: voce del Task 11.
- `map.md`: nessuna voce ⏳ residua.

**Decisioni**
- Rischio §18: shard dei conti di sistema **non** necessari ora (0 retry per deadlock con lock ordinati); da rivalutare con il test di carico su staging.

**Aperto**
- Non verificati con un client reale: login (fail-closed), `/pay`, `/balance` da giocatore.
- Avvisi WSREP/1243 di Flyway all'avvio (cosmetici); UUID invece del nome nel messaggio di rollback rifiutato.
- Design §8 ancora aperto: Vault/ChestShop (M4), calibrazione di saldo iniziale e tassa `/pay` (beta).

---

## 2026-10-25 – M1b – Task 11 – `EconomyModule`, cablaggio nel plugin, jar e smoke test

**Cosa**
- `EconomyModule` (`ARKModule` `economy`): in `onLoad` registra ledger (`LedgerService`), tunable, hook di login/uscita, `/balance`, `/pay`, sottocomandi `eco|tx|rollback|freeze|unfreeze|recon`, job notturno di riconciliazione; `health()` DEGRADED se la riconciliazione segnala un problema di sistema; `healthDetails()` con massa monetaria, riconciliazione e retry.
- `ModuleCatalog.all()` = `List.of(new EconomyModule())`; `config.yml` di default con `economy` abilitato; `plugin.yml` con i permessi economici; shadow: Caffeine rilocata, jspecify/errorprone esclusi, `verifyShadowJar` controlla Caffeine.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/EconomyModule.java`, `core-platform/.../module/ModuleCatalog.java`, `core-platform/src/main/resources/{config.yml,plugin.yml}`, `core-platform/build.gradle.kts`; test `EconomyModuleIT`, `ModuleCatalogTest`, `PluginYmlTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat test` → `cannot find symbol EconomyModule`.
- GREEN: `.\gradlew.bat clean build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`, `verifyShadowJar` passato; 180 test, 0 skipped. Jar shadow: nessuna classe fuori da `org/breachsmp/`.
- **Smoke test** (Paper 26.2, MariaDB 11.4.13; giocatore `Tester` inserito a mano in `breach_game.player`, perché da console non si entra in gioco):
  1. `migrazioni applicate ora: 1` (V004), `Modulo 'economy' abilitato`, `Moduli attivi: [economy]`. ✅
  2. `breach health` → `Modulo economy: HEALTHY`, `Massa monetaria: 0 monete`, `Riconciliazione: mai eseguita`, `Retry deadlock del ledger: 0`. ✅
  3. `breach tunable` → le 4 chiavi `economy.*` con i default di D-32. ✅
  4. `breach eco give Tester 12,50 prova smoke` → `Dati 12,50 monete a Tester (tx #1).`; `balance Tester` → `Saldo di Tester: 12,50 monete`; `breach tx Tester` → `#1 … ADMIN_GIVE +12,50 monete → 12,50 monete`. ✅
  5. `breach freeze Tester test` → congelato; `breach eco take Tester 1 test` → `Tolti 1 moneta a Tester (tx #2).` (i tipi admin passano il freeze); `breach unfreeze` → sbloccato. ✅
  6. `breach rollback tx 1 test` → **rifiutato** con `Fondi insufficienti sul conto giocatore:<uuid>.`: dopo il take Tester ha 11,50 e annullare il give di 12,50 lo manderebbe in negativo (E10). Comportamento corretto; l'ordine dei passi del piano era sbagliato.
  7. `breach rollback tx 2 test` → `Rollback eseguito: tx #5 annulla #2.`; di nuovo → `Rollback già eseguito in precedenza: tx #5.`; saldo di nuovo 12,50. ✅ (gli id 3 e 4 li hanno consumati i due rollback rifiutati: `AUTO_INCREMENT` non si riavvolge.)
  8. `breach recon` → `Riconciliazione: OK (3 conti, …)`; `breach health` → `Massa monetaria: 12,50 monete`. ✅
  9. Riavvii: `migrazioni applicate ora: 0`; `stop` → `breach-db - Shutdown completed`, nessuna eccezione. ✅
  - SQL su `breach_game`: somma dei saldi 0; `ledger_tx` = 1 ADMIN_GIVE, 2 ADMIN_TAKE, 5 ROLLBACK; `audit_log` = ECO_GIVE, ACCOUNT_FREEZE, ECO_TAKE, ACCOUNT_UNFREEZE, TX_ROLLBACK (nessuna riga per i rollback rifiutati).
- **Non verificato:** `/pay` e `/balance` da giocatore in gioco (serve un client): coperti da `PayCommandTest`, `BalanceCommandTest` e dagli IT del ledger.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nella console Windows `–`, `→` e `à` escono come `?`: è la codifica del terminale (il log di Paper è UTF-8), non un errore del plugin.
- Il messaggio di rollback rifiutato mostra l'UUID del conto e non il nome: migliorabile.
- La sessione si è interrotta durante la seconda prova; ripresa il 2026-10-25 (MariaDB riavviato), esiti sopra.

---

## 2026-10-22 – M1b – Task 10 – sottocomandi admin di `/breach`

**Cosa**
- `AdminArgs` (parsing puro), `EcoSubcommand`, `TxSubcommand`, `RollbackSubcommand`, `FreezeSubcommand` (freeze/unfreeze), `ReconSubcommand`; helper `AdminCommands`.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/command/{AdminArgs,AdminCommands,EcoSubcommand,TxSubcommand,RollbackSubcommand,FreezeSubcommand,ReconSubcommand}.java`; test `AdminArgsTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :module-economy:test --tests "*AdminArgsTest"` → `package AdminArgs does not exist`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 178 test, 0 skipped; `AdminArgsTest` 4/4.

**Decisioni**
- Nessuna nuova. Deviazione dal piano: helper `AdminCommands` (package-private) per non copiare la stessa logica in quattro sottocomandi.

**Aperto**
- I sottocomandi non hanno test unitari oltre al parsing: verifica nello smoke test del Task 11.

---

## 2026-10-22 – M1b – Task 9 – login economico, `/balance`, `/pay`

**Cosa**
- `EconomyTunables` (4 tunable di D-32), `EconomyLogin` (conto, saldo iniziale idempotente, cache), `PayCommand` (parsing sincrono, minimo, rate limit, tassa verso TAX_POOL, lock per giocatore, avviso al destinatario), `BalanceCommand`, `EconomyMessages`.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/{EconomyTunables,EconomyLogin}.java`, `.../command/{PayCommand,BalanceCommand,EconomyMessages}.java`; test `command/{Fakes,PayCommandTest,BalanceCommandTest}`, `EconomyLoginIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :module-economy:test` → `cannot find symbol` (`BalanceCommand`, `EconomyTunables`, `PayCommand`).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 174 test, 0 skipped; `PayCommandTest` 6, `BalanceCommandTest` 2, `EconomyLoginIT` 2.

**Decisioni**
- Nessuna nuova.

**Aperto**
- `/pay` non è verificabile da console nello smoke test (serve un giocatore): coperto da `PayCommandTest` e dagli IT del ledger.

---

## 2026-10-22 – M1b – Task 8 – riconciliazione notturna (DoD M1)

**Cosa**
- `ReconciliationJob` (`Job`): ricalcolo dei saldi dalle entry e somma dei saldi in un'unica fotografia di sola lettura; giocatore manomesso → congelato + audit `RECON_MISMATCH`; conto di sistema → solo alert e `systemProblem()` (DEGRADED); somma ≠ 0 → audit `RECON_TOTAL`; righe per `/breach health`.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/recon/ReconciliationJob.java`; test `recon/ReconciliationIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :module-economy:test --tests "*ReconciliationIT"` → `cannot find symbol ReconciliationJob`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 164 test, 0 skipped; `ReconciliationIT` 4/4: saldo di un giocatore aumentato di 100 fuori dal ledger → rilevato (600 contro 500), conto congelato, `/pay` rifiutato con `AccountFrozenException`; job eseguito da `JdbcJobScheduler` alle 04:00 del giorno dopo.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1b – Task 7 – operazioni admin (give, take, rollback, freeze, storico)

**Cosa**
- `EconomyAdmin`: give/take con audit dopo il commit, rollback compensativo idempotente (solo stagione corrente, non su un rollback), freeze/unfreeze con audit solo se cambia, storico a pagine, massa monetaria. `RollbackRefusedException`.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/admin/{EconomyAdmin,RollbackRefusedException}.java`; test `admin/EconomyAdminIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :module-economy:test --tests "*EconomyAdminIT"` → `cannot find symbol EconomyAdmin`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 160 test, 0 skipped; `EconomyAdminIT` 8/8 (rollback ripetuto = stesso tx, rollback di un rollback / tx inesistente / altra stagione rifiutati, rollback che manderebbe un giocatore in negativo → `InsufficientFundsException` senza movimenti).

**Decisioni**
- Nessuna nuova.

**Aperto**
- Il test "altra stagione" fa un `UPDATE ledger_tx` **solo nel test** per simulare la condizione; il codice di produzione non modifica mai `ledger_tx`.

---

## 2026-10-22 – M1b – Task 6 – 1000 transazioni concorrenti (DoD M1)

**Cosa**
- `LedgerConcurrencyIT`: 20 giocatori con 1.000 monete; 1000 transazioni casuali (seed 42: 70% `PAY`, 15% `ADMIN_TAKE` verso SINK, 15% `ADMIN_GIVE` da FAUCET) più 100 doppi invii con la stessa chiave, mescolati e inviati tutti insieme al pool DB (8 thread, pool Hikari 10).

**File**
- `module-economy/src/test/java/org/breachsmp/core/economy/ledger/LedgerConcurrencyIT.java`; `map.md`, `log.md`.

**Verifica**
- `.\gradlew.bat :module-economy:test --tests "*LedgerConcurrencyIT"` (con `BREACH_IT_*`) → verde, skipped=0, 1,13 s. **Riuscite: 960 chiavi, saldo insufficiente: 41, retry deadlock: 0.** Invarianti verificate in SQL: una riga `ledger_tx` per chiave riuscita (+20 iniziali), somma dei saldi 0, nessun giocatore negativo, SINK ≥ 0, saldo = somma delle entry per ogni conto, ogni transazione a somma 0; doppi invii → stesso `txId`.
- **Prova negativa:** lock un conto alla volta nell'ordine delle gambe (invece di un solo `SELECT … ORDER BY id FOR UPDATE`) → **retry deadlock: 6** (contro 0); i retry li assorbono e le invarianti tengono. Codice ripristinato (`git checkout`).

**Decisioni**
- **Rischio §18 (righe calde SYS_SINK/SYS_FAUCET):** con 8 thread e lock ordinati nessun deadlock e 1100 invii in ~1 s → **shard dei conti di sistema non necessari ora**; da rivalutare con il test di carico su staging.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1b – Task 5 – ledger JDBC e cache dei saldi

**Cosa**
- `JdbcLedgerService` (algoritmo D-20: validazione, conti in autocommit, `INSERT ledger_tx` idempotente con replay, lock ordinato, regole pure, `UPDATE`+entry, `COMMIT`, poi cache ed evento; retry su 1213/1205/1020).
- `BalanceCache` (Caffeine con versione, D-29). Helper di test `LedgerFixture`.

**File**
- `module-economy/src/main/java/org/breachsmp/core/economy/ledger/{JdbcLedgerService,BalanceCache}.java`; test `LedgerFixture`, `ledger/JdbcLedgerServiceIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :module-economy:test --tests "*JdbcLedgerServiceIT"` → `cannot find symbol` (`BalanceCache`, `JdbcLedgerService`).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 151 test, 0 skipped; `JdbcLedgerServiceIT` 11/11 su MariaDB 11.4.13.

**Decisioni**
- Nessuna nuova.

**Aperto**
- La concorrenza reale si verifica nel Task 6.

---

## 2026-10-22 – M1b – Task 4 – `module-economy`, regola I7, schema V004, DAO dei conti

**Cosa**
- Convention plugin `breach.module-conventions` (I7, D-21); nuovo progetto `module-economy` incluso in `settings.gradle.kts`; `core-platform` dipende da `module-economy`.
- Migrazione `V004__economy_ledger.sql`: `account` (con `frozen_at`, CHECK saldo ≥ 0 tranne SYSTEM), `ledger_tx` (`idem_key` UNIQUE), `ledger_entry` (FK).
- `AccountDao`, `LedgerDao`, record `LockedAccount`, `StoredTx`, `HistoryRow`.

**File**
- `build-logic/src/main/kotlin/breach.module-conventions.gradle.kts`, `settings.gradle.kts`, `module-economy/build.gradle.kts`, `core-platform/build.gradle.kts`, `core-platform/src/main/resources/db/migration/V004__economy_ledger.sql`, `module-economy/src/main/java/org/breachsmp/core/economy/ledger/*`; test `AccountDaoIT`; `map.md` (§2, §7, nuova §8), `log.md`.

**Verifica**
- `.\gradlew.bat build` con il nuovo progetto → `BUILD SUCCESSFUL` (nessun ciclo tra progetti con `testFixtures(project(":core-platform"))`).
- **Prova negativa I7:** con `implementation(project(":core-platform"))` in `module-economy` → configurazione fallita: `I7: :module-economy non può dipendere da :core-platform in 'implementation' (ammessi solo :core-api e :core-domain)`; riga tolta.
- RED: `.\gradlew.bat :module-economy:test` → `cannot find symbol AccountDao`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 140 test, 0 skipped; `AccountDaoIT` 4/4, `MigratorIT` 3/3 (4 migrazioni).

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1b – Task 3 – test di proprietà (jqwik)

**Cosa**
- `core-domain`: dipendenza di test `jqwik` 1.10.1; helper `InMemoryLedger` (stesse regole pure del ledger JDBC); `LedgerProperties` con 3 proprietà: conservazione del denaro e solo FAUCET in negativo (300 sequenze), tassa = ceiling esatto, `Money.parse` coerente.

**File**
- `core-domain/build.gradle.kts`, `core-domain/src/test/java/org/breachsmp/core/domain/economy/{InMemoryLedger,LedgerProperties}.java`; `map.md`, `log.md`.

**Verifica**
- `.\gradlew.bat :core-domain:test` → `BUILD SUCCESSFUL`; `LedgerProperties` 3/3, 0 failure. Totale build: 136 test, 0 skipped.
- **Prova negativa:** con il controllo `after < 0` di `LedgerRules.balancesAfter` disattivato, `moneyIsNeitherCreatedNorDestroyedAndOnlyFaucetGoesNegative` fallisce con controesempio ristretto `giocatore:00000000-0000-0001-0000-000000000001 = -1`; codice ripristinato (`git checkout`), di nuovo verde.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1b – Task 2 – importi, tassa, regole del ledger, rate limit, orario

**Cosa**
- `core-domain`: `economy/Money` (parse e format degli importi, E1/E21), `economy/FeePolicy` (`ceil(P*t/10000)`), `economy/LedgerRules` (`validate`, `balancesAfter`), `ratelimit/SlidingWindowLimiter`, `schedule/Schedules.nextDaily`.

**File**
- `core-domain/src/main/java/org/breachsmp/core/domain/{economy,ratelimit,schedule}/*` (nuovi); test `MoneyTest`, `FeePolicyTest`, `LedgerRulesTest`, `SlidingWindowLimiterTest`, `SchedulesTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-domain:test` → `cannot find symbol` (FeePolicy, Money, …).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 133 test, 0 skipped (`MoneyTest` 3, `FeePolicyTest` 2, `LedgerRulesTest` 5, `SlidingWindowLimiterTest` 1, `SchedulesTest` 1).

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1b – Task 1 – contratti del ledger

**Cosa**
- Nuovo package `core-api/.../economy/`: `LedgerService`, `TxRequest`, `Leg`, `TxResult`, `EntryView`, `AccountRef`, `OwnerType`, `SystemAccount`, `TxKinds`, `LedgerTxCommitted`, `LedgerException`, `InsufficientFundsException`, `AccountFrozenException`, `IdempotencyConflictException`, `InvalidTransactionException`.

**File**
- `core-api/src/main/java/org/breachsmp/core/api/economy/*` (nuovi); test `AccountRefTest`, `TxModelTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-api:test` → `package SystemAccount does not exist`, `cannot find symbol`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 121 test, 0 skipped (`AccountRefTest` 2, `TxModelTest` 3).

**Decisioni** (dal piano `docs/superpowers/plans/2026-10-22-m1b-ledger.md`)
- **D-19** Conti di sistema creati in modo pigro come quelli dei giocatori, con `owner_ref` = `new UUID(0, n)` (FAUCET 1, SINK 2, TAX_POOL 3, ESCROW 4). Sostituisce E7.
- **D-20** Esecuzione del ledger: validazione pura; id dei conti risolti/creati in autocommit prima della transazione; `INSERT ledger_tx` (1062 = replay, `kind` diverso → `IdempotencyConflictException`); un solo `SELECT … WHERE id IN (…) ORDER BY id FOR UPDATE`; regole pure; `UPDATE` + `INSERT ledger_entry`; `COMMIT`, poi cache ed evento. Retry fino a 3 volte con jitter su 1213, 1205, 1020. `created_at` troncato ai millisecondi.
- **D-21** La regola I7 è imposta dal convention plugin `breach.module-conventions`. Aggiorna D-12: ArchUnit resta per M10.
- **D-23** jqwik 1.10.1, Caffeine 3.3.0 rilocata in `org.breachsmp.core.libs.caffeine`; jspecify ed errorprone esclusi dal jar.
- **D-24** Saldo iniziale al login con `STARTING_BALANCE` e chiave `IdemKeys.of("start:<stagione>:<uuid>")`, una volta per stagione (lo riceve anche chi è già entrato se il tunable sale).
- **D-25** Rollback solo della stagione corrente, gambe invertite, chiave `IdemKeys.of("rollback:<id>")`; un rollback non si annulla.
- **D-27** `/breach recon` esegue subito la riconciliazione (`breach.admin.recon`).
- **D-29** Cache dei saldi Caffeine per `AccountRef` con la `version` del conto (vince la più alta), `expireAfterAccess` 30 min, max 10.000; aggiornata solo dopo commit o lettura dal DB.
- **D-31** In `core-api` per l'economia solo `LedgerService` (E8 parziale): il resto è interno a `module-economy`.
- **D-32** Tunable economici in unità minime: `economy.starting_balance` (0, [0, 100000000]), `economy.transfer_fee_bp` (0, [0, 5000]), `economy.pay_rate_per_minute` (10, [1, 1000]), `economy.recon_hour` (4, [0, 23]).
- **D-33** Restano aperti senza bloccare: niente Vault in M1 (decisione con M4); saldo iniziale e tassa `/pay` a 0 finché non si calibra in beta.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1a – chiusura (fondamenta di piattaforma)

**Cosa**
- Chiusi i 10 task di M1a: correzioni rinviate da M0, IT serializzati e `TestDatabase` nei testFixtures, contratti di piattaforma in `core-api`, stagione/profilo/audit (V002, V003), tunable a caldo, job con lease, `PlayerLock` e login fail-closed, comandi dei moduli senza Bukkit, cablaggio in `ARKPlugin`.
- Roadmap: M1 "🟡 M1a completata, M1b in corso". Legenda della mappa generalizzata (non più legata al piano M0).

**File**
- `docs/roadmap.md`, `map.md`, `log.md`.

**Verifica**
- `.\gradlew.bat clean build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 116 test, 0 failure, 0 error, **0 skipped**.
- Smoke test su Paper: voce del Task 9.
- `map.md`: nessuna voce ⏳ residua.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Login di un giocatore reale non verificato (serve un client).
- Avvisi WSREP/1243 di Flyway all'avvio (cosmetici, vedi Task 9).

---

## 2026-10-22 – M1a – Task 9 – cablaggio dei servizi e smoke test

**Cosa**
- `ARKPlugin.start()` riscritto: servizi di piattaforma registrati nel `ServiceRegistry` prima dei moduli, `DefaultModuleContext` con il `DataSource`, `TunableRegistry.reload()` dopo `onLoad`, `CommandHub.close()` dopo `enableAll()`, `PlayerListener` registrato, `/breach` con `health`, `tunable` e i sottocomandi dei moduli, comandi dei moduli con `ModuleCommandAdapter`, scheduler dei job e ricarica dei tunable ogni 30 s sul timer.
- `plugin.yml`: permesso `breach.admin.tunable` (`op`).

**File**
- `core-platform/src/main/java/org/breachsmp/core/platform/ARKPlugin.java`, `core-platform/src/main/resources/plugin.yml`, test `PluginYmlTest`; `map.md` (§1, §3, §7), `log.md`.

**Verifica**
- RED: `PluginYmlTest.adminPermissionsDefaultToOp` → `NullPointerException` (permesso assente).
- GREEN: `.\gradlew.bat clean build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`, `verifyShadowJar` passato; 116 test, 0 skipped.
- **Smoke test** (`:core-platform:runServer`, Paper 26.2, MariaDB 11.4.13, comandi da stdin):
  1. `Database pronto, migrazioni applicate ora: 2` (V002, V003), `BreachCore avviato. Moduli attivi: []`. ✅
  2. `breach health` → `BreachCore: HEALTHY`, `Database: OK`, `Moduli: nessuno`, `Ultimo job: nessuno`, `Code: db=0 compute=0 market=0`. ✅
  3. `breach tunable` → `Nessun tunable registrato.` ✅
  4. `breach boh` → `Uso: /breach <health|tunable>`. ✅
  5. Riavvio: `migrazioni applicate ora: 0`, HEALTHY; `stop` → `breach-db - Shutdown completed`, nessuna eccezione. ✅
- **Non verificato:** il login di un giocatore reale (serve un client): coperto solo da `LoginGateTest`.

**Decisioni**
- Nessuna nuova.

**Aperto**
- All'avvio il driver logga WARN `1193 Unknown system variable 'WSREP_ON'` e `1243 Unknown prepared statement handler (4294967295)`: vengono dalla sonda di Flyway per Galera (c'erano già nello smoke test di M0, 6 righe). Sono innocui; da silenziare solo se danno fastidio nei log.

---

## 2026-10-22 – M1a – Task 8 – comandi dei moduli e `/breach` estendibile

**Cosa**
- `BreachCommand` riscritto come dispatcher di `AdminSubcommand` (uso e suggerimenti filtrati per permesso); nuovi `ModuleCommandAdapter`, `CommandDelivery` (consegna sul main, avvisi ai giocatori online, `describe` spostato qui), `CommandHub` (`CommandRegistry`), `ChatRenderer`, `HealthSubcommand`, `TunableSubcommand` (`/breach tunable list|get|set`).
- `HealthReport` ha le righe `details`; `HealthService` riceve le righe di piattaforma; `ModuleRegistry.details()` raccoglie `healthDetails()` dei moduli abilitati.
- `ARKPlugin` adattato in modo minimo (cablaggio completo nel Task 9).

**File**
- `core-platform/.../command/{BreachCommand,ModuleCommandAdapter,CommandDelivery,CommandHub,ChatRenderer,HealthSubcommand,TunableSubcommand}.java`, `.../health/{HealthReport,HealthService}.java`, `.../module/ModuleRegistry.java`, `ARKPlugin.java`; test `BreachCommandTest` (riscritto), `CommandDeliveryTest`, `CommandHubTest`, `HealthSubcommandTest`, `TunableSubcommandTest`, `HealthReportTest`, `HealthServiceTest`, `ModuleRegistryTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test` → errori di compilazione (`cannot find symbol CommandHub`, `TunableSubcommand`; firma di `BreachCommand.suggestions`).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 116 test, 0 skipped.

**Decisioni**
- Nessuna nuova.

**Aperto**
- `BreachCommand.execute`, `ModuleCommandAdapter` e `CommandDelivery.deliver` usano tipi Bukkit e non hanno test unitari: verifica nello smoke test del Task 9.

---

## 2026-10-22 – M1a – Task 7 – PlayerLock, hook di login/uscita, listener Paper

**Cosa**
- `SerialPlayerLock` (coda FIFO per giocatore), `PlayerHookRegistry`, `LoginRejectedException`, `LoginGate` (upsert del profilo poi hook; ogni errore → espulsione con messaggio italiano), `PlayerListener` (`AsyncPlayerPreLoginEvent`, `PlayerQuitEvent`).

**File**
- `core-platform/.../lock/SerialPlayerLock.java`, `.../player/{PlayerHookRegistry,LoginRejectedException,LoginGate,PlayerListener}.java`; test `SerialPlayerLockTest`, `PlayerHookRegistryTest`, `LoginGateTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test --tests "*Lock*" --tests "*Login*" --tests "*Hook*"` → `cannot find symbol`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 108 test, 0 skipped; `SerialPlayerLockTest` 3, `PlayerHookRegistryTest` 2, `LoginGateTest` 3.

**Decisioni**
- Nessuna nuova.

**Aperto**
- `PlayerListener` non ha test unitari (tipi Bukkit): verificabile solo con un client reale.

---

## 2026-10-22 – M1a – Task 6 – job pianificati con lease

**Cosa**
- `JdbcJobScheduler` (implementa `JobScheduler`): registrazione prima dell'avvio, `start` (righe mancanti in `scheduled_job` + controllo ogni 60 s sul timer), `tick` con lease di 10 minuti via `UPDATE` condizionato, rilascio con esito e prossima esecuzione, `healthLines` con l'ultimo job.

**File**
- `core-platform/.../job/JdbcJobScheduler.java`; test `JdbcJobSchedulerIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test --tests "*JdbcJobSchedulerIT"` → `cannot find symbol JdbcJobScheduler`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 100 test, 0 skipped; `JdbcJobSchedulerIT` 6/6 (incluso: due scheduler in parallelo sullo stesso job → una sola esecuzione).

**Decisioni**
- Nessuna nuova. Aggiunta rispetto al piano: se al rilascio il lease non è più nostro (job durato oltre 10 minuti e preso da un altro processo) si logga un WARNING.

**Aperto**
- Un job che dura più del lease può essere eseguito due volte da processi diversi: i job devono restare idempotenti (lo sono per costruzione: chiavi deterministiche).

---

## 2026-10-22 – M1a – Task 5 – tunable modificabili a caldo

**Cosa**
- `TunableRegistry` (implementa `TunableService`): registrazione validata, `reload`/`reloadQuietly`, `set` con upsert e audit `TUNABLE_SET` nella stessa transazione, `list`/`find`. Record `TunableView`, `TunableChange`.

**File**
- `core-platform/.../tunable/{TunableRegistry,TunableView,TunableChange}.java`; test `TunableRegistryTest`, `TunableRegistryIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test --tests "*Tunable*"` → `cannot find symbol TunableRegistry`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 94 test, 0 skipped; `TunableRegistryTest` 2, `TunableRegistryIT` 3 (incluso il rifiuto di `abc`, `5001`, `-1`, `1.5`, vuoto e chiave sconosciuta senza scritture né audit).

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1a – Task 4 – stagione, profilo giocatore, audit (V002, V003)

**Cosa**
- Migrazioni V002 (`season`, `current_season` con stagione 1, indice `ix_player_name`) e V003 (`audit_log`, `tunable`, `scheduled_job`).
- `CurrentSeason.load` (fail-closed se manca la stagione corrente), `JdbcPlayerService` (upsert al login, ricerca per nome senza maiuscole, il più recente vince), `JdbcAuditService` (async, bloccante e `insert` sulla connessione del chiamante).

**File**
- `core-platform/src/main/resources/db/migration/V002__platform_season.sql`, `V003__platform_audit_tunable_job.sql`; `core-platform/.../season/CurrentSeason.java`, `.../player/JdbcPlayerService.java`, `.../audit/JdbcAuditService.java`; test `CurrentSeasonIT`, `JdbcPlayerServiceIT`, `JdbcAuditServiceIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test` → `compileTestJava` fallito, `cannot find symbol` (le tre classi).
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 89 test, 0 skipped; `CurrentSeasonIT` 2, `JdbcPlayerServiceIT` 2, `JdbcAuditServiceIT` 2, `MigratorIT` 3 (ora 3 migrazioni) su MariaDB 11.4.13.

**Decisioni**
- Nessuna nuova.

**Aperto**
- Nessuno.

---

## 2026-10-22 – M1a – Task 3 – contratti di piattaforma in `core-api`

**Cosa**
- Nuovi contratti senza Bukkit: `data/DataAccessException`; `util/Uuids`, `IdemKeys`, `JsonText`; `player/PlayerProfile`, `PlayerService`, `PlayerHooks`, `LoginHook`, `QuitHook`; `season/SeasonService`; `audit/AuditEntry`, `AuditService`; `tunable/Tunable`, `TunableService`; `job/Job`, `NextRun`, `JobScheduler`; `command/Tone`, `ChatLine`, `Notice`, `CommandResult`, `CommandInvocation`, `ModuleCommand`, `AdminSubcommand`, `CommandRegistry`; `lock/PlayerLock`.
- `ModuleContext.dataSource()` (D-16) e `ARKModule.healthDetails()`; `DefaultModuleContext` riceve il `DataSource` (ultimo componente), `ARKPlugin` lo passa.

**File**
- `core-api/src/main/java/org/breachsmp/core/api/{data,util,player,season,audit,tunable,job,command,lock}/…` (nuovi), `module/ModuleContext.java`, `module/ARKModule.java`; test `util/UuidsTest`, `IdemKeysTest`, `JsonTextTest`, `command/CommandResultTest`; `core-platform/.../module/DefaultModuleContext.java`, `ARKPlugin.java`, test `DefaultModuleContextTest`, `ModuleRegistryTest`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-api:test` → `compileTestJava` fallito, `cannot find symbol`.
- GREEN: `.\gradlew.bat build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 83 test, 0 skipped, 0 failure (`core-api`: 13).
- Nota: un primo GREEN aveva 2 failure in `JsonTextTest`, dovute al test scritto con una heredoc di bash che aveva alterato le sequenze `\\`; test riscritto identico al piano, poi verde.

**Decisioni**
- Nessun ID nuovo. Deviazione dal piano: anche `ModuleRegistryTest` costruiva `DefaultModuleContext` e ora gli passa un `DataSource` proxy che lancia su ogni chiamata (`NO_DATABASE`).

**Aperto**
- Le implementazioni arrivano nei Task 4–8 (voci ⏳ in `map.md` §4).

---

## 2026-10-22 – M1a – Task 2 – versioni M1, IT serializzati, TestDatabase nei testFixtures

**Cosa**
- Catalogo: jqwik `1.9.2` → `1.10.1` (allineata a JUnit Platform 1.14.4; la 1.10.2 ha meno di 2 settimane), aggiunta Caffeine `3.3.0` (usate in M1b).
- Build service `ItDatabaseLock` (`maxParallelUsages = 1`) usato da tutti i task `Test`: con più progetti gli IT non si contendono lo schema `breach_it`.
- `core-platform` applica `java-test-fixtures`; `TestDatabase` spostato in `src/testFixtures/.../testkit/` (pubblico) con il nuovo `migrated()`; nuovo `MutableClock`; `TestDatabaseTest` spostato nel package `testkit`.

**File**
- `gradle/libs.versions.toml`, `build-logic/src/main/kotlin/ItDatabaseLock.kt` (nuovo), `build-logic/src/main/kotlin/breach.java-conventions.gradle.kts`, `core-platform/build.gradle.kts`, `core-platform/src/testFixtures/java/org/breachsmp/core/platform/testkit/{TestDatabase,MutableClock}.java`, `core-platform/src/test/java/org/breachsmp/core/platform/testkit/TestDatabaseTest.java`, `.../db/MigratorIT.java`; `map.md`, `log.md`.

**Verifica**
- `.\gradlew.bat clean build` con `BREACH_IT_*` → `BUILD SUCCESSFUL`; 73 test, 0 skipped, 0 failure.
- Senza variabili: `.\gradlew.bat :core-platform:test` → verde, `MigratorIT` tests=3 skipped=3.

**Decisioni**
- Nessuna nuova (D-22 già registrata nel Task 1).

**Aperto**
- `TestDatabase.migrated()` sarà esercitato dagli IT dei Task 4–6.

---

## 2026-10-22 – M1a – Task 1 – correzioni rinviate da M0

**Cosa**
- `PlatformExecutors`: nuovo `timer()` (1 thread daemon `breach-timer`, D-26); `close()` ferma il timer e poi drena market → compute → db anche se il chiamante è interrotto (prima un interrupt saltava il drain di compute e db), ripristinando il flag alla fine (review M0, M2).
- `ModuleRegistry`: catalogo immutabile assegnato in `load()` (review M5).
- `ARKModule.onDisable`: Javadoc che vieta lavoro sul main durante lo spegnimento (review M3).
- `core-platform/build.gradle.kts`: jar normale con classifier `plain`, così non sovrascrive lo shadow; tolto `mustRunAfter(jar)` da `verifyShadowJar`.
- Test aggiunti (review M6): permessi admin `op` in `PluginYmlTest`, messaggio `V999` in `MigratorIT`.

**File**
- `core-platform/.../concurrent/PlatformExecutors.java`, `.../module/ModuleRegistry.java`, `core-api/.../module/ARKModule.java`, `core-platform/build.gradle.kts`, test `PlatformExecutorsTest`, `PluginYmlTest`, `MigratorIT`; `map.md`, `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test --tests "...PlatformExecutorsTest"` → `compileTestJava` fallito, `cannot find symbol` (`timer()`).
- GREEN: `.\gradlew.bat clean build` (con `BREACH_IT_*`) → `BUILD SUCCESSFUL`; 73 test, 0 skipped, 0 failure; in `core-platform/build/libs/` ci sono `core-platform-1.0-SNAPSHOT.jar` (shadow) e `core-platform-1.0-SNAPSHOT-plain.jar`.

**Decisioni** (dal piano `docs/superpowers/plans/2026-10-22-m1a-piattaforma.md`)
- **D-14** M1 è diviso in due piani: M1a (piattaforma) e M1b (ledger, `module-economy`, comandi, riconciliazione). M1 è chiusa solo a fine M1b.
- **D-15** Comandi e hook dei moduli senza Bukkit: in `core-api` `ModuleCommand`, `AdminSubcommand`, `CommandInvocation`, `CommandResult`, `ChatLine`/`Tone`, `Notice`, `LoginHook`/`QuitHook`; la piattaforma li adatta a Paper. I moduli non dipendono da Paper.
- **D-16** `ModuleContext.dataSource()`: i moduli ricevono il pool (`javax.sql`, JDK).
- **D-17** I tunable di M1 sono solo interi (`registerLong`); BOOLEAN/STRING quando servono.
- **D-18** Audit: tunable e `audit_log` nella stessa transazione; operazioni di denaro (M1b) audit dopo il commit (la riga `ledger_tx` con `actor` e `meta.reason` è la traccia autorevole, audit fallito → SEVERE). `AuditService.record` (asincrono) e `recordNow` (bloccante): mai attendere un future del pool DB da dentro il pool DB.
- **D-22** `TestDatabase` (+ `MutableClock`) nei testFixtures di `core-platform` (package `testkit`); `TestDatabase.migrated()`; build service `ItDatabaseLock` (`maxParallelUsages = 1`) serializza i task `Test` (schema `breach_it` condiviso).
- **D-26** Il timer di piattaforma `breach-timer` passa solo lavoro al pool DB: scheduler job ogni 60 s, ricarica tunable ogni 30 s; lease dei job 10 minuti.
- **D-28** Correzioni rinviate dalla review M0: jar `plain`, drain con interrupt, catalogo congelato, Javadoc `onDisable`, asserzioni di `PluginYmlTest` e `MigratorIT`.

**Aperto**
- Nessuno per questo task.

---

## 2026-10-22 – Piani di M1 (M1a piattaforma, M1b ledger)

**Cosa**
- Scritti i piani di implementazione di M1 a partire dal design `docs/superpowers/specs/2026-10-21-m1-ledger-design.md`, diviso in due: **M1a** (correzioni rinviate da M0, servizi di piattaforma: stagione, profilo giocatore, audit, tunable, job con lease, PlayerLock, hook di login/quit, comandi dei moduli senza Bukkit) e **M1b** (contratti e regole pure del ledger con jqwik, `module-economy`, ledger JDBC, 1000 transazioni concorrenti, admin, riconciliazione, `/balance`, `/pay`, smoke test).
- Verificati prima di scrivere: API Paper 26.2 (`AsyncPlayerPreLoginEvent.disallow(Result, Component)`, `PlayerQuitEvent`, `JavaPlugin.registerCommand(label, descrizione, alias, BasicCommand)`); su MariaDB 11.4.13 reale: colonne `at`/`value` ammesse, JSON validato (errore 4025), CHECK → 4025, chiave duplicata → 1062, `REPEATABLE-READ`, `innodb_snapshot_isolation=0`; versioni jqwik 1.10.1 (JUnit Platform 1.14.4) e Caffeine 3.3.0 con almeno 2 settimane di anzianità.

**File**
- Creati: `docs/superpowers/plans/2026-10-22-m1a-piattaforma.md`, `docs/superpowers/plans/2026-10-22-m1b-ledger.md`. Modificati: `docs/roadmap.md`, `log.md`. Nessun file di codice.

**Verifica**
- Nessun codice: niente build.

**Decisioni**
- Le decisioni D-14…D-33 sono **proposte nei piani** e vengono registrate qui dai rispettivi Task 1 quando l'esecuzione parte.

**Aperto**
- Design §8: Vault/ChestShop e default 0 per saldo iniziale e tassa restano aperti (D-33, non bloccano).

---

## 2026-10-22 – M0 – smoke test end-to-end e IT su MariaDB reale (M0 chiusa)

**Cosa**
- Docker non installabile senza WSL (assente; richiede admin e riavvio) → installato **MariaDB 11.4.13 portatile** (zip ufficiale, SHA-256 verificato `d62986d4…`) in `%LOCALAPPDATA%\breach-mariadb`, fuori dal repo: porta 3307, bind `127.0.0.1`, schemi `breach_game` (utente `bc_game`, server di prova) e `breach_it` (utente `bc_it`, test di integrazione). Password casuali in `%LOCALAPPDATA%\breach-mariadb\secrets\`, mai stampate.
- Nuovo helper di test `TestDatabase`: DB esterno da `BREACH_IT_JDBC_URL`/`BREACH_IT_DB_USER`/`BREACH_IT_DB_PASSWORD`, altrimenti Testcontainers se c'è Docker, altrimenti `assumeTrue` → test saltato. Rifiuta uno schema esterno che non finisce con `_it` (i test fanno Flyway `clean`).
- `MigratorIT` usa `TestDatabase` invece di `@Testcontainers`/`@Container`; `tearDown` tollera il pool null (test saltato).
- Convention plugin: `BREACH_IT_JDBC_URL` è un input dei task `Test`, così la build cache non riusa un esito "saltato" quando il DB c'è.
- `CLAUDE.md` §5: comandi per avviare MariaDB ed eseguire gli IT.

**File**
- Creati: `core-platform/src/test/java/org/breachsmp/core/platform/db/TestDatabase.java`, `…/db/TestDatabaseTest.java`.
- Modificati: `…/db/MigratorIT.java`, `build-logic/src/main/kotlin/breach.java-conventions.gradle.kts`, `CLAUDE.md`, `docs/roadmap.md`, `map.md`, `log.md`.

**Verifica**
- TDD `TestDatabaseTest`: RED (`compileTestJava` fallito, 6 errori `cannot find symbol TestDatabase`), poi GREEN 2/2.
- Senza variabili: `MigratorIT` tests=3 skipped=3 failures=0 (comportamento di prima).
- Con variabili: `MigratorIT` tests=3 skipped=0 failures=0 errors=0 su MariaDB 11.4.13 → **prima esecuzione reale** di migrazione su schema vuoto, seconda esecuzione = 0, rifiuto schema futuro.
- `.\gradlew.bat clean build` con variabili → `BUILD SUCCESSFUL`, `verifyShadowJar` passato; 70 test, 0 skipped, 0 failure, 0 error.
- **Smoke test M0** (`:core-platform:runServer`, Paper 26.2, comandi inviati da stdin; script in scratchpad):
  1. Senza `secrets/db-password`: `Avvio di BreachCore fallito, il plugin viene disabilitato: Configurazione non valida: - File della password DB mancante: plugins\BreachCore\secrets\db-password ...`, poi `Disabling BreachCore`; server avviato normalmente. ✅
  2. Con MariaDB: `Database pronto, migrazioni applicate ora: 1`, `BreachCore avviato. Moduli attivi: []`; `breach health` → `BreachCore: HEALTHY` / `Database: OK` / `Moduli: nessuno` / `Code: db=0 compute=0 market=0`. ✅
  3. Riavvio: `migrazioni applicate ora: 0`, HEALTHY; `stop` → `breach-db - Shutdown completed`, nessuna eccezione. ✅
- Verificati quindi a runtime: `api-version: '26.2'` accettato da Paper (nessun avviso), Flyway e driver MariaDB rilocati (ServiceLoader + scansione `classpath:db/migration`), `TRANSACTION_REPEATABLE_READ` accettato dal driver reale. Nel DB: tabelle `flyway_schema_history`, `player`; V001 `success=1`.

**Decisioni**
- **D-13 DB per i test di integrazione** (integra D-05): gli `*IT` ottengono il DB da `TestDatabase` — esterno via `BREACH_IT_*` (schema obbligatoriamente `*_it`), altrimenti Testcontainers, altrimenti saltati. Su questa macchina: MariaDB 11.4.13 portatile su 127.0.0.1:3307.

**Aperto**
- Gli IT saltano in silenzio se le variabili mancano: per il codice DB controllare `skipped=0` nei report prima di dire "fatto".
- Paper segnala di essere 2 release dietro la stabile (26.3): resta bloccato su 26.2 (D-02), da rivalutare a inizio stagione.
- Differiti dalla review M0 (M2 drain/interrupt, M3 main in `onDisable`, M5 freeze catalogo, plain jar): entrano nel primo task di M1 (E12).

---

## 2026-10-21 – M0 – correzioni della review finale (C1, I1, M1, M4)

**Cosa**
- **C1 (critico)**: lo `shadowJar` di Shadow 9.6.1 usa `DuplicatesStrategy.EXCLUDE`, quindi `mergeServiceFiles()` vedeva solo la prima copia di ogni `META-INF/services/*`. Nel jar `org.breachsmp.core.libs.flyway.core.extensibility.Plugin` aveva solo le 3 voci di `flyway-mysql`: le voci di `flyway-core` (`ClasspathLocationHandlerImpl`, `CoreResourceTypeProvider`, ...) andavano perse, quindi `classpath:db/migration` non sarebbe stato risolvibile a runtime. Fix: `filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }` (solo sui servizi, non su tutto il jar) + `inputs.property` per invalidare la cache dello `shadowJar` (la closure non è un input). Nuovo task `verifyShadowJar` agganciato a `check`/`build`: apre il jar e fallisce se il file di servizio Flyway non contiene `...flyway.core.internal.scanner.classpath.ClasspathLocationHandlerImpl` e `...flyway.database.mysql.mariadb.MariaDBDatabaseType`, o se `java.sql.Driver` non contiene `org.breachsmp.core.libs.mariadb.Driver`.
- **Correzione della voce Task 9**: l'affermazione "i file `META-INF/services` ... sono uniti" era **FALSA** per Flyway (e l'avviso `DuplicatesStrategy ... EXCLUDE` della voce "M0 – chiusura" era il sintomo). Ora è vero e verificato dal task `verifyShadowJar`.
- **I1**: `ARKPlugin.onEnable` cattura `RuntimeException | LinkageError | ServiceConfigurationError`, così relocation/linkage rotti producono il SEVERE italiano e il disable esplicito.
- **M1**: `BreachCommand.describe(Throwable)` scarta il `CompletionException` e mostra il messaggio della causa (fallback: nome semplice della classe).
- **M4**: `ConfigLoader` usa `LoaderOptions.setAllowDuplicateKeys(false)`; una chiave duplicata dà `ConfigException` "Impossibile leggere config.yml: ...".

**File**
- `core-platform/build.gradle.kts`, `ARKPlugin.java`, `BreachCommand.java`, `ConfigLoader.java`, `BreachCommandTest.java`, `ConfigLoaderTest.java`, `map.md`, `log.md`.

**Verifica**
- TDD M4: `ConfigLoaderTest.duplicateKeysAreRejected` rosso (`AssertionFailedError`) prima del fix, verde dopo.
- `.\gradlew.bat :core-platform:test` -> `BUILD SUCCESSFUL`.
- `.\gradlew.bat clean build` -> `BUILD SUCCESSFUL`; `:core-platform:verifyShadowJar` eseguito e passato; nessun avviso `DuplicatesStrategy` ne' duplicati. Test: 68 totali, 0 failure, 0 error, 3 skipped (`MigratorIT`, Docker assente).
- Prova negativa: con `ClasspathLocationHandlerImplXX` nel task, `verifyShadowJar` fallisce ("Jar shadow non valido: ... non contiene ...XX"); ripristinato.
- Jar: file di servizio Flyway = 41 righe (2 commenti + 39 voci, tutte `org.breachsmp.core.libs.flyway.*`); `java.sql.Driver` = `org.breachsmp.core.libs.mariadb.Driver`; servizi mariadb: AuthenticationPluginFactory 7, Codec 37, CredentialPlugin 3, TlsSocketPlugin 1. Nessuna classe fuori da `org/breachsmp/` (esclusi gli ingressi `META-INF/versions/*/org/breachsmp/`).

**Decisioni**
- Nessun ID `D-xx` nuovo.

**Aperto**
- Smoke test manuale invariato (vedi "Stato attuale"). Differiti (nel ledger): M2 drain/interrupt in close, M3 lavoro sul main in `onDisable`, M5 freeze del catalogo, M6 asserzioni dei test.
- Nota: la closure `filesMatching` non è un input di Gradle; se cambia, usare `clean` o `--rerun-tasks` (un primo `shadowJar` incrementale dopo la modifica ha restituito il jar vecchio).

---

## 2026-10-21 – M0 – chiusura (codice completo, smoke test manuale in attesa)

**Cosa**
- Chiusura del codice di M0 (Task 10): roadmap aggiornata a "🟡 codice completo, smoke test manuale in attesa"; mappa controllata (nessuna voce ⏳/🔁 residua su M0, rimane solo la legenda).
- M0 **non** è dichiarata completata: la Definition of Done dipende dallo smoke test manuale dell'utente.

**File**
- Modificati: `docs/roadmap.md`, `map.md` (nessuna modifica necessaria), `log.md`.

**Verifica**
- `.\gradlew.bat clean build` -> `BUILD SUCCESSFUL`.
- Test (da `*/build/test-results/test/*.xml`): 66 test, 63 passati, 3 skipped (`MigratorIT`, Docker assente), 0 failure, 0 error. Il task `test` è stato ripreso dalla build cache (`FROM-CACHE`); i risultati sono quelli della esecuzione in cache con gli stessi input.
- Avviso non bloccante dallo `shadowJar`: `DuplicatesStrategy` su `META-INF/services/org.mariadb.jdbc.plugin.*` è `EXCLUDE` (il messaggio consiglia `INCLUDE` o `WARN`). Da verificare nello smoke test che i servizi MariaDB siano caricati.

**NON verificato** (richiede lo smoke test manuale dell'utente):
- `MigratorIT` (richiede Docker).
- Runtime delle librerie rilocate (Flyway/MariaDB: ServiceLoader, scansione `db/migration`).
- `api-version: '26.2'` accettato da Paper.
- Output di `/breach health`.

**Smoke test da eseguire** (piano, Task 9 step 8–9):
1. Senza DB: `.\gradlew.bat :core-platform:runServer` (EULA in `core-platform/run/eula.txt`). Atteso: `Avvio di BreachCore fallito ... File della password DB mancante`, plugin disabilitato. Poi `stop`.
2. Con MariaDB: creare DB e utente (`CREATE DATABASE breach_game CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`, `CREATE USER 'bc_game'@'localhost' IDENTIFIED BY '<password>';`, `GRANT ALL PRIVILEGES ON breach_game.* TO 'bc_game'@'localhost';`), scrivere la password in `core-platform/run/plugins/BreachCore/secrets/db-password`, poi `.\gradlew.bat :core-platform:runServer`. Atteso: `Database pronto, migrazioni applicate ora: 1`, `BreachCore avviato. Moduli attivi: []`.
3. In console: `breach health`. Atteso: `BreachCore: HEALTHY`, `Database: OK`, `Moduli: nessuno`, `Code: db=0 compute=0 market=0`.
4. `stop`, poi di nuovo `runServer`: atteso `migrazioni applicate ora: 0`; `stop` senza eccezioni.
- Se Paper rifiuta `api-version`, correggere `plugin.yml` e registrare una nuova decisione.

**Decisioni**
- Nessun ID `D-xx` nuovo.

**Aperto**
- Smoke test manuale di Task 9 step 8–9 (a cura dell'utente).
- Dopo lo smoke test: chiudere M0 (roadmap "✅ completata") e scrivere il piano di M1 (Ledger).

---

## 2026-10-21 – Task 9 – bootstrap `ARKPlugin`, `plugin.yml`, jar shadow

**Cosa**
- `ARKPlugin` (bootstrap fail-closed: config → pool → migrazioni → executor → moduli → `/breach`), `plugin.yml` con `version` espansa da Gradle, blocco `tasks` di `core-platform/build.gradle.kts` (`processResources`, `shadowJar` con `mergeServiceFiles()`, esclusione slf4j, relocation, `runServer`).
- Aggiunta relocation non prevista dal brief: `com.fasterxml.jackson` -> `org.breachsmp.core.libs.jackson` (transitiva di Flyway; nel jar comparivano 79 classi `com/fasterxml/jackson/annotation/*` non rilocate).
- Verificato `onDisable` dopo avvio fallito: se `start()` fallisce dopo `modules.load`, `modules` è già assegnato e `disableAll()` viene chiamato; i campi `executors`/`dataSource` sono null-guardati e azzerati, quindi lo spegnimento è sicuro in ogni punto di fallimento. Nessuna modifica necessaria.

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/ARKPlugin.java`, `core-platform/src/main/resources/plugin.yml`, `core-platform/src/test/java/org/breachsmp/core/platform/PluginYmlTest.java`.
- Modificati: `core-platform/build.gradle.kts`, `map.md` (ARKPlugin, plugin.yml, build.gradle.kts -> ✅), `log.md`.

**Verifica**
- RED: `.\gradlew.bat :core-platform:test --tests "...PluginYmlTest"` -> `cannot find symbol class ARKPlugin`, `BUILD FAILED`.
- GREEN: stesso comando -> `BUILD SUCCESSFUL`. `.\gradlew.bat build --rerun-tasks` -> `BUILD SUCCESSFUL`; 66 test in totale, 3 skipped (`MigratorIT`, Docker assente), 0 failure.
- Step 7 (jar `core-platform-1.0-SNAPSHOT.jar`): dopo la relocation jackson nessuna classe fuori da `org/breachsmp/`; le uniche 3 righe segnalate sono `META-INF/versions/{11,15}/org/breachsmp/core/libs/mariadb/...` (multi-release, già rilocate). `plugin.yml` contiene `version: '1.0-SNAPSHOT'`; presenti `config.yml` e `db/migration/V001__platform_create_player.sql`. I file `META-INF/services` (`java.sql.Driver`, flyway `...Plugin`, mariadb `AuthenticationPluginFactory/Codec/CredentialPlugin/TlsSocketPlugin`) sono uniti e puntano a nomi rilocati `org.breachsmp.core.libs.*`. **[CORREZIONE: falso per Flyway, vedi la voce "correzioni della review finale (C1...)": il file Plugin perdeva le voci di flyway-core.]**
- Step 8 – in attesa: smoke test manuale a cura dell'utente. Comando: `.\gradlew.bat :core-platform:runServer` (EULA in `core-platform/run/eula.txt`; atteso errore "File della password DB mancante" e disabilitazione del plugin).
- Step 9 – in attesa: smoke test manuale a cura dell'utente con MariaDB (creare DB/utente come da brief, password in `core-platform/run/plugins/BreachCore/secrets/db-password`), poi `.\gradlew.bat :core-platform:runServer`; atteso `migrazioni applicate ora: 1`, `breach health` -> HEALTHY, al secondo avvio `migrazioni applicate ora: 0`.

**Decisioni**
- Nessun ID `D-xx` nuovo. Relocation jackson aggiunta (vedi sopra).

**Aperto**
- La Definition of Done di M0 NON è verificata end-to-end: step 8–9 non eseguibili qui (niente server interattivo, MariaDB, Docker).
- `api-version: '26.2'` non verificato: se Paper lo rifiuta, correggere secondo il messaggio e registrare una nuova decisione.
- Non verificato a runtime che Flyway/MariaDB rilocati funzionino (ServiceLoader, scansione `db/migration`): solo ispezione statica del jar.

---

## 2026-10-21 – Task 8 – `/breach health`

**Cosa**
- `HealthReport` (record): `overall()` (DB giù o modulo `FAILED` → `FAILED`; `DEGRADED`/`STARTING` → `DEGRADED`; `DISABLED` ignorato) e `lines()` (prima riga = stato complessivo, poi database, moduli o "Moduli: nessuno", code).
- `HealthService(ModuleRegistry, DataSource, PlatformExecutors)`: `check()` raccoglie DB (`isValid(2)`, errore SQL = DB giù), moduli e code sul thread DB.
- `BreachCommand(HealthService, Executor main) implements BasicCommand`: `/breach health` con permesso `breach.admin.health`, risposta sul main via `whenCompleteAsync`, colore per stato; tab-completion dei sottocomandi (`suggestions`).

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/health/{HealthReport,HealthService}.java`, `core-platform/src/main/java/org/breachsmp/core/platform/command/BreachCommand.java`, test `…/health/HealthReportTest.java` (6), `…/health/HealthServiceTest.java` (3), `…/command/BreachCommandTest.java` (1).
- Modificati: `map.md` (§6 `platform/health/` e `platform/command/BreachCommand.java` → ✅), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-platform:test` con soli test → `:core-platform:compileTestJava FAILED`, `14 errors`, `cannot find symbol class HealthReport / HealthService`, `BUILD FAILED in 1s`.
- GREEN: `.\gradlew.bat :core-platform:test` → `BUILD SUCCESSFUL in 4s`. Per classe: `HealthReportTest` 6/6, `HealthServiceTest` 3/3, `BreachCommandTest` 1/1.
- Suite completa `core-platform`: 54 test, 3 skipped (`MigratorIT`, Docker assente), 0 failure, 0 error. Le API Paper (`BasicCommand`, `CommandSourceStack`, Adventure) compilano senza modifiche rispetto al brief.

**Decisioni**
- Nessuna decisione nuova; nessuna deviazione dal brief.

**Aperto**
- `BreachCommand.execute` e `health()` (rami con `CommandSender` reale, permessi, `whenCompleteAsync`) non hanno test: richiedono un server Paper; coperti solo da `suggestions` e dallo smoke test del Task 9.

---

## 2026-10-21 – Task 7 – pool DB e migrazioni Flyway

**Cosa**
- `DataSourceFactory.hikariConfig/create`: pool Hikari `breach-db`, driver MariaDB, pool size = min idle = `effectivePoolSize`, `maxLifetime` da config, timeout connessione e `initializationFailTimeout` 5 s (fail-closed), `REPEATABLE READ`, prepared statement server-side.
- `Migrator(DataSource, ClassLoader).migrate()`: Flyway su `classpath:db/migration`, `cleanDisabled(true)`; se `info()` contiene stati `FUTURE_SUCCESS/FUTURE_FAILED` lancia `SchemaTooNewException`; ritorna il numero di migrazioni eseguite.
- `V001__platform_create_player.sql`: tabella globale `player`.

**File**
- Creati: `core-platform/src/main/java/org/breachsmp/core/platform/db/{DataSourceFactory,SchemaTooNewException,Migrator}.java`, `core-platform/src/main/resources/db/migration/V001__platform_create_player.sql`, test `…/db/DataSourceFactoryTest.java` (2 test), `…/db/MigratorIT.java` (3 test).
- Modificati: `map.md` (§6 `platform/db/` e §7 V001 → ✅), `log.md`.

**Verifica** (TDD)
- RED: `.\gradlew.bat :core-platform:test` con soli test → `:core-platform:compileTestJava FAILED`, `5 errors` (cannot find symbol Migrator / DataSourceFactory / SchemaTooNewException), `BUILD FAILED in 3s`.
- GREEN: `.\gradlew.bat :core-platform:test` → `BUILD SUCCESSFUL in 5s`. `DataSourceFactoryTest` 2/2 passati.
- **Esito MigratorIT: SALTATO** (Docker non installato): XML `MigratorIT` tests=3 skipped=3 failures=0 errors=0. Le 3 verifiche di integrazione (migrazione su schema vuoto, seconda esecuzione = 0, rifiuto schema futuro) NON sono state eseguite contro un DB reale.
- Totale suite: 44 test, 3 skipped (MigratorIT), 0 failure, 0 error.

**Decisioni**
- Nessuna decisione nuova; nessuna deviazione dal brief (Flyway 13.9.0, HikariCP 7.1.0, Testcontainers 1.21.4 compilano il codice del brief senza modifiche).

**Aperto**
- `MigratorIT` e la stringa `TRANSACTION_REPEATABLE_READ` accettata dal driver MariaDB reale vanno verificati su una macchina con Docker / MariaDB (al più tardi allo smoke test del Task 9).

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
