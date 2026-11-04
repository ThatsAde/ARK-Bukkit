# BreachCore – Specifica tecnica del core plugin

*Versione 0.1 · Bozza tecnica · Pensata per sviluppo incrementale a tempo perso: ogni modulo è utilizzabile da solo.*

---

## 0. Ambito e assunzioni

- **"Core"** indica il plugin principale `BreachCore`, che implementa tutto il dominio proprietario: ledger/economia, territorio (Tool Cupboard), Breach Core item, Extraction, plot, banca e azioni, tornei, stagioni, entitlements. Plugin terzi (permessi, anti-cheat, chat) restano esterni.
- **Piattaforma:** Paper (versione stabile da bloccare all'avvio dello sviluppo e mantenuta per tutto il ciclo), solo **Paper API** (niente NMS o reflection, salvo eccezioni documentate).
- **Processi:** un solo processo di gioco al lancio, ma **nessuno stato autoritativo in memoria**, così la divisione in due processi (Main + Frattura) è solo configurazione (vedi documento infrastruttura, D3).
- **Fuori ambito:** sito, store Tebex e bot Discord: comunicano con il core tramite il contratto in sez. 13.
- **Nomi:** codice e schema in inglese, documentazione in italiano.

---

## 1. Invarianti architetturali

Regole che non si violano mai; ogni scelta successiva ne discende.

| ID | Invariante | Motivo | Conseguenza |
| --- | --- | --- | --- |
| I1 | Il **database è la fonte di verità**; la memoria è una copia | Crash, restart e divisione in processi non devono perdere stato | Nessun dato di valore solo in RAM |
| I2 | Il **main thread** esegue solo Bukkit API, mai I/O | Un'attesa sul DB blocca il tick di tutti i giocatori | API asincrone con `CompletableFuture`, ritorno su main |
| I3 | Il denaro si muove **solo** tramite `LedgerService`, append-only, a partita doppia | Impedisce soldi dal nulla e rende verificabile ogni saldo | Nessun `UPDATE balance` fuori dal ledger |
| I4 | Ogni operazione che cambia stato ha una **chiave di idempotenza** | Retry, doppio click e riavvii non devono duplicare | Colonna `idem_key UNIQUE` |
| I5 | Un oggetto di valore **esiste solo se ha una riga viva nel DB** (serial) | Rende i dupe inutili: la copia non ha riga valida | L'`ItemStack` è una *rappresentazione* del DB |
| I6 | **Muro monetizzazione:** nessun percorso di codice da denaro reale o Rift Credits al ledger di gioco | Garantisce la regola "never pay to win" a livello di codice | Schemi, utenti DB e moduli separati (sez. 13) |
| I7 | I moduli dipendono solo da `core-api`, mai dall'implementazione di altri moduli | Si può sviluppare un modulo alla volta | Interfacce + registry |
| I8 | Tutto ciò che scala con i giocatori ha un **budget di tick** | A 100 giocatori un O(n²) uccide il TPS | Budget in sez. 14 |

---

## 2. Struttura del progetto

```
breach/
 ├ core-api/        interfacce, DTO, eventi (senza Bukkit dove possibile)
 ├ core-domain/     logica pura: ledger rules, matching engine, bracket, state machine
 ├ core-platform/   bootstrap, DB, scheduler, config, service registry, adapter Paper
 ├ module-economy/  module-territory/  module-items/  module-extraction/
 ├ module-plots/    module-bank/       module-arena/  module-season/  module-store/
 └ build-logic/     convenzioni Gradle, shadow, test
```

- **Perché `core-domain` separato:** la logica pura (ledger, matching, bracket) si testa senza avviare un server Minecraft, con test in millisecondi.
- **Build:** Gradle (Kotlin DSL), un solo jar finale con shadow e *relocation* delle librerie (HikariCP, Flyway, Caffeine, driver DB) per evitare conflitti con altri plugin.
- **Regola dipendenze:** `module-*` → `core-api`; solo `core-platform` conosce le implementazioni e le collega.

---

## 3. Runtime, threading e ciclo di vita

### Moduli e registry

Injection manuale tramite costruttori, senza framework: meno magia, errori chiari in avvio.

```java
public interface BreachModule {
  String id();
  void onLoad(ModuleContext ctx);   // registra servizi, comandi, migrazioni
  void onEnable();                  // avvia job e listener
  void onDisable();                 // drain e flush
  HealthStatus health();            // usato dal comando /breach health
}
```

L'ordine di avvio segue il grafo delle dipendenze dichiarate (`dependsOn`); un ciclo fa fallire l'avvio con un messaggio esplicito. Ogni modulo ha un **feature flag** (`modules.<id>.enabled`) per sviluppare e rilasciare a pezzi.

### Modello dei thread

| Executor | Uso | Dimensione |
| --- | --- | --- |
| `MAIN` | Bukkit API, modifica di blocchi e inventari | Il thread del server |
| `DB` | Query e transazioni | Uguale al pool Hikari |
| `COMPUTE` | Calcoli puri (bracket, statistiche) | Piccolo, fisso |
| `MARKET` | Matching engine delle azioni | **Un solo thread** (single-writer) |

- **Regola:** i servizi pubblici restituiscono `CompletableFuture<T>`; il ritorno sul main passa da `ctx.sync(...)`.
- **Login:** in `AsyncPlayerPreLoginEvent` si carica profilo e conti nella cache (qui il blocco è lecito, il thread è asincrono). Se il DB non risponde, **fail-closed**: il giocatore non entra, perché un'economia con saldi non caricati è peggio di un server pieno di attese.
- **Cache:** Caffeine con TTL; i saldi si aggiornano solo con il risultato di una transazione ledger, mai in modo ottimistico.
- **Arresto:** `onDisable` svuota le code in ordine inverso di dipendenza. Dopo un crash il DB resta coerente per I1.

### Configurazione

- YAML validato con schema, versionato (`config_version`), con ricaricamento a caldo.
- I **tunable operativi** (cap delle zone, aliquote, prezzi di base) stanno nella tabella `tunable` del DB: si cambiano a server acceso con comando admin, con riga di audit, senza restart.

---

## 4. Persistenza

- **MariaDB / InnoDB**, `utf8mb4`, isolamento `REPEATABLE READ` (default).
- **Locking esplicito:** nelle transazioni sul ledger si usa `SELECT ... FOR UPDATE` sulle righe conto **in ordine crescente di id**, così due transazioni non si bloccano a vicenda. Su deadlock: retry fino a 3 volte con jitter.
- **Pool:** HikariCP, dimensione `min(10, core*2)`, `maxLifetime` inferiore al `wait_timeout` del DB, statement preparati.
- **Migrazioni:** Flyway, file `V001__nome.sql`, mai modificati dopo l'applicazione. All'avvio il plugin **rifiuta di partire** se lo schema è più recente del codice.
- **Accesso ai dati:** JDBC semplice con classi `Dao` sottili, niente ORM: query prevedibili e profilabili.
- **Stagioni:** ogni tabella economica ha `season_id`. Il reset di stagione non cancella nulla: apre una nuova partizione logica (sez. 12).
- **Utenti DB separati** per schema, come da documento infrastruttura.

### Schema base (estratto)

```sql
CREATE TABLE player (
  uuid       BINARY(16) PRIMARY KEY,
  name       VARCHAR(16) NOT NULL,
  first_seen DATETIME(3) NOT NULL,
  last_seen  DATETIME(3) NOT NULL,
  flags      INT UNSIGNED NOT NULL DEFAULT 0
);

CREATE TABLE account (
  id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id SMALLINT UNSIGNED NOT NULL,
  owner_type ENUM('PLAYER','TEAM','SYSTEM') NOT NULL,
  owner_ref VARBINARY(16) NOT NULL,
  balance   BIGINT NOT NULL DEFAULT 0,
  version   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uq_owner (season_id, owner_type, owner_ref),
  CHECK (owner_type = 'SYSTEM' OR balance >= 0)
);

CREATE TABLE ledger_tx (
  id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id  SMALLINT UNSIGNED NOT NULL,
  idem_key   CHAR(36) NOT NULL UNIQUE,
  kind       VARCHAR(32) NOT NULL,
  actor      BINARY(16) NULL,
  created_at DATETIME(3) NOT NULL,
  meta       JSON NULL
);

CREATE TABLE ledger_entry (
  tx_id         BIGINT UNSIGNED NOT NULL,
  seq           TINYINT UNSIGNED NOT NULL,
  account_id    BIGINT UNSIGNED NOT NULL,
  delta         BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  PRIMARY KEY (tx_id, seq),
  KEY ix_account (account_id, tx_id)
);

CREATE TABLE audit_log (
  id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  at         DATETIME(3) NOT NULL,
  actor      BINARY(16) NULL,
  action     VARCHAR(48) NOT NULL,
  subject    VARCHAR(64) NULL,
  detail     JSON NULL,
  KEY ix_action_at (action, at)
);
```

**Importi** sempre come interi in unità minime; la formattazione è solo livello UI. Niente `double`.

---

## 5. Ledger economico

**Obiettivo:** nessun giocatore può creare, perdere o duplicare denaro per un bug; ogni variazione è tracciabile.

```java
public interface LedgerService {
  CompletableFuture<TxResult> execute(TxRequest req);
  CompletableFuture<Long> balance(AccountRef ref);
}
record Leg(AccountRef account, long delta) {}
record TxRequest(UUID idemKey, String kind, List<Leg> legs, Map<String,Object> meta) {}
```

### Algoritmo di esecuzione

1. Valida: somma dei `delta` = 0, tutte le gambe distinte, `kind` noto.
2. Ordina le gambe per `account.id` crescente.
3. `BEGIN`; `INSERT ledger_tx` (se `idem_key` esiste, **restituisce il risultato già salvato** e termina).
4. `SELECT ... FOR UPDATE` sui conti; verifica che i conti non di sistema restino ≥ 0.
5. `UPDATE account SET balance=?, version=version+1` e `INSERT ledger_entry` con `balance_after`.
6. `COMMIT`; solo dopo, aggiorna la cache e pubblica l'evento `LedgerTxCommitted`.

### Conti di sistema

| Conto | Funzione | Può andare in negativo |
| --- | --- | --- |
| `SYS_FAUCET` | Origine dei soldi creati (premi, vendite al negozio) | Sì |
| `SYS_SINK` | Destinazione dei soldi distrutti (biglietti, mantenimento) | No |
| `SYS_TAX_POOL` | Tasse raccolte, fonte dei dividendi | No |
| `SYS_ESCROW` | Fondi bloccati per affitti, aste e ordini | No |

**Massa monetaria** = `-saldo(FAUCET) - saldo(SINK)`: è il numero da monitorare per l'inflazione (sez. 14).

### Tasse e commissioni

Un `FeePolicy` per tipo di operazione trasforma un'operazione in gambe: per una vendita di prezzo P e aliquota t in punti base, la tassa è `ceil(P * t / 10000)` (mai zero nelle microtransazioni), il resto va al venditore.

### Verifica

- **Test di proprietà** (property-based): per qualsiasi sequenza casuale di transazioni, la somma di tutti i saldi resta costante e nessun saldo non di sistema è negativo.
- **Riconciliazione notturna:** ricalcola ogni saldo dalle `ledger_entry` e lo confronta con `account.balance`; mismatch → alert immediato e blocco dell'account.
- **Rate limit** per giocatore sui comandi di pagamento.

---

## 6. Territorio e Tool Cupboard

### Modello

```sql
CREATE TABLE claim (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id   SMALLINT UNSIGNED NOT NULL,
  world_id    SMALLINT UNSIGNED NOT NULL,
  team_id     BIGINT UNSIGNED NOT NULL,
  cup_x INT NOT NULL, cup_y INT NOT NULL, cup_z INT NOT NULL,
  radius      SMALLINT UNSIGNED NOT NULL,
  upkeep_until DATETIME NOT NULL,
  shield_until DATETIME NULL,
  raid_window_start TINYINT NULL,
  raid_window_changed_at DATETIME NULL
);
CREATE TABLE claim_member (claim_id BIGINT UNSIGNED, uuid BINARY(16), role TINYINT, PRIMARY KEY (claim_id, uuid));
```

- **Indice spaziale in memoria:** mappa `chunkKey (long) → lista di claim` per tutti i chunk toccati. Ogni claim è un cubo attorno al cupboard, quindi tocca pochi chunk. Verifica di un blocco: lookup del chunk → pochi test AABB, O(k) con k piccolo.
- **Regola di non sovrapposizione:** un nuovo cupboard non può avere un cubo che si interseca con altri claim espansi di una distanza minima.
- **Basi condivise tramite PIN** (design M2, `docs/superpowers/specs/2026-10-25-m2-territorio-design.md`): chi conosce il PIN del Tool Cupboard si autorizza da solo, gli autorizzati sono in `claim_member`. Non c'è una tabella `team` del territorio: il limite di 6 è della squadra Extraction (M5).

### Tier dei blocchi

I cinque tier sono **basati su `Material`** (config), così non serve resource pack:

| Tier | Nome | Esempio di materiale (config) |
| --- | --- | --- |
| 1 | Legno | Famiglia legno |
| 2 | Pietra | Pietra, mattoni |
| 3 | Rinforzato | Deepslate lavorato |
| 4 | Metallo | Blocchi di metallo |
| 5 | Armored | Materiale raro + componente (config) |

L'upgrade di tier avviene con un item "martello" che sostituisce il blocco e addebita il costo dal ledger.

### Danno ai blocchi

Il danno è **sparso** e salvato nel `PersistentDataContainer` del chunk (come array di byte), così segue il mondo nei backup e non pesa sul DB.

- Rappresentazione: `blockPosLocal → permille (0..1000)` + timestamp ultimo colpo.
- Un blocco si rompe a ≥ 1000.
- **Decadimento:** i danni più vecchi di 30 minuti vengono scartati in modo pigro al primo accesso (nessun job periodico).
- Dato un Core di livello L e un blocco di tier T: se T ≤ L → danno 1000; se T = L+1 → danno 334 (servono 3 colpi); altrimenti 0.

### Algoritmo di blast

```java
void detonate(CoreItem core, Location origin) {
  int r = radiusFor(core.level());          // 1 per L1 ... 2 per L4
  List<BlockPos> targets = collectInCube(origin, r);
  budget.run(targets, pos -> {              // max N blocchi per tick
    Claim c = claims.at(pos);
    if (c == null) return;                   // solo blocchi in claim
    int tier = tiers.of(world.getType(pos));
    int dmg  = damageFor(core.level(), tier);
    if (dmg > 0 && damageStore.add(pos, dmg) >= 1000) breakNoDrops(pos);
  });
  shield.scheduleAfterQuiet(origin, Duration.ofMinutes(10)); // shield post-raid
}
```

- **Budget di tick:** massimo \~2000 blocchi per tick, il resto nei tick successivi.
- **Shield post-raid:** parte **10 minuti dopo l'ultimo blast**, non subito, così non si interrompe un raid in corso.

### Gate del raid

Catena `RaidGate`, ogni anello restituisce un codice motivo mostrato al giocatore:

`ClaimExists → NotInSpawnWorld → TargetNotShielded → WithinRaidWindow → AttackerCoreValid (serial, sez. 7) → CarryLimitOk`

### Protezione dai danni esterni

Tutto ciò che non è un Core viene annullato dentro un claim:

| Origine | Regola |
| --- | --- |
| `EntityExplodeEvent`, `BlockExplodeEvent` | Rimuove i blocchi in claim dalla lista |
| Pistoni | Annulla se muovono blocchi attraverso il confine del claim |
| Fuoco, lava, acqua | Annulla propagazione verso blocchi in claim non propri |
| Withers, End Crystal, Ender Dragon | Come le esplosioni |
| Break/Place/Interact | Solo membri autorizzati |

---

## 7. Sottosistema oggetti (Breach Core)

**Principio (I5):** l'oggetto vive nel DB; l'`ItemStack` è solo la sua immagine.

```sql
CREATE TABLE core_item (
  serial     BINARY(16) PRIMARY KEY,
  season_id  SMALLINT UNSIGNED NOT NULL,
  kind       VARCHAR(24) NOT NULL,          -- FRACTURE, BREACH, RUPTURE, CATACLYSM
  level      TINYINT UNSIGNED NOT NULL,
  creator    BINARY(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,          -- +14 giorni
  state      ENUM('HELD','ESCROW','ARMED','USED','EXPIRED','VOID') NOT NULL,
  state_at   DATETIME(3) NOT NULL
);
```

- **PDC dell'item:** `breach:serial`, `breach:kind`, `breach:level`, `breach:expires`. Il nome e la lore sono generati dal codice, mai fidati.
- **Armare:** `UPDATE core_item SET state='ARMED' WHERE serial=? AND state='HELD' AND expires_at > NOW(3)`. Se le righe modificate sono 0, l'item è invalido e viene rimosso. In un dupe, **il primo che arma vince, la copia fallisce** e fa scattare un alert con il serial.
- **Scansione serial doppi:** a login, logout e apertura di contenitori si conta lo stesso serial più volte; se compare due volte, entrambe le copie vengono invalidate (`VOID`) e l'audit registra chi le deteneva.
- **Limite di trasporto (3 Core):** controllato su pickup, click inventario e login.
- **Scambio tra giocatori:** solo tramite Mercato/Contratto. L'item passa in `ESCROW`, esce dall'inventario del venditore e **viene ricreato dal DB** all'acquirente; la vendita e il trasferimento di stato stanno nella stessa transazione ledger.
- **Scadenza:** job orario che porta a `EXPIRED` e rimuove gli item scaduti ancora in inventari online.

### Crafting (stazione con GUI)

1. Il main thread verifica e **riserva** gli ingredienti.
2. Il DB inserisce la riga `core_item` e scrive `ledger_tx` per le eventuali commissioni.
3. Se il DB fallisce, un record `pending_craft` **restituisce** gli ingredienti (compensazione).

La finestra tra riserva e conferma è breve e coperta da compensazione: preferibile a un rischio di duplicazione.

---

## 8. Extraction

### Entità e stati

| Entità | Stati | Note |
| --- | --- | --- |
| `zone_cycle` | `CLOSED → OPEN → RESETTING → CLOSED` | Un ciclo = una settimana, con `cycle_seed` per la generazione deterministica |
| `extraction_session` | `IN_ZONE → EXTRACTING → EXTRACTED` oppure `DIED` / `LOST` | Una sessione per ingresso, persistita: sopravvive ai restart |
| `extraction_point` | `INACTIVE → ANNOUNCED → ACTIVE → INACTIVE` | Rotazione calcolata dal `cycle_seed`, quindi riproducibile dopo un riavvio |

### Flusso di una run

1. **Ingresso:** transazione ledger `ZONE_TICKET` verso `SYS_SINK` con `idem_key = session_id`; se fallisce, nessuna sessione. Il giocatore entra con l'equipaggiamento che porta.
2. **Loot instabile:** ogni componente raccolto riceve il tag PDC `breach:unsecured=<session_id>`.
3. **Estrazione:** channeling di 8–10 s su un punto attivo, con boss bar. Si annulla su danno, movimento oltre 1,5 blocchi o teleport. A fine channel il tag `unsecured` viene rimosso e la sessione passa a `EXTRACTED`.
4. **Morte:** drop standard, i tag restano sugli oggetti caduti (chi li raccoglie ne eredita l'instabilità e il nuovo `session_id`).
5. **Timeout di sessione:** dopo 20–30 minuti scatta una tempesta con danno crescente, così il camping infinito non conviene.

### Regole anti-exploit

- Gli oggetti `unsecured` **non possono** essere messi in contenitori, shulker, ender chest, item frame o ricevuti da altri giocatori in modo diverso da drop/morte.
- Tutti i teleport verso l'esterno sono bloccati tranne quelli dei punti di estrazione.
- **Disconnessione in zona:** il comportamento predefinito è un `linger` di 15 s (tunable); se il giocatore non è rientrato, la sessione diventa `LOST` e il loot instabile viene lasciato cadere nella posizione dell'uscita. Da validare in beta.

### Supply cap (regolatore dell'inflazione)

```sql
CREATE TABLE zone_supply (
  zone_id  SMALLINT UNSIGNED NOT NULL,
  cycle_id INT UNSIGNED NOT NULL,
  tier     TINYINT UNSIGNED NOT NULL,
  cap      INT UNSIGNED NOT NULL,
  used     INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (zone_id, cycle_id, tier)
);

UPDATE zone_supply SET used = used + 1
WHERE zone_id = ? AND cycle_id = ? AND tier = ? AND used + 1 <= cap;
```

Se le righe modificate sono 0, la cassa genera solo loot comune. Il cap è un tunable: si regola in base al prezzo di mercato dei Core.

### Loot

Le casse sono posizioni predefinite (`loot_spawn`). Il contenuto si genera **al primo accesso**, con RNG seminato da `hash(cycle_seed, posizione)`, così il risultato è riproducibile e non dipende dal timing. Ogni componente raro concesso scrive una riga in `loot_event` (audit e statistiche di drop).

### Reset settimanale (scambio del mondo template)

1. Annuncio e blocco ingressi; estrazione forzata o rimozione dei presenti.
2. `Bukkit.unloadWorld(world, false)`.
3. Cancellazione della cartella del mondo e **copia del mondo template**.
4. `createWorld` sul mondo copiato, rigenerazione delle `loot_spawn`.
5. Nuova riga `zone_cycle` con nuovo seed e nuovi cap.

Il mondo ha una cartella dedicata: spostarlo su un secondo processo non richiede modifiche al codice.

---

## 9. Plot e affitti

```sql
CREATE TABLE plot (
  id SMALLINT UNSIGNED PRIMARY KEY, tier ENUM('S','M','L','LANDMARK') NOT NULL,
  season_id SMALLINT UNSIGNED NOT NULL, owner BINARY(16) NULL,
  state ENUM('AVAILABLE','OWNED','LISTED','RENTED','DELINQUENT','AUCTION') NOT NULL,
  paid_until DATETIME NULL
);
CREATE TABLE rent_contract (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, plot_id SMALLINT UNSIGNED NOT NULL,
  tenant BINARY(16) NOT NULL, price BIGINT NOT NULL, period_days TINYINT UNSIGNED NOT NULL,
  paid_until DATETIME NOT NULL, state ENUM('ACTIVE','ENDED','DEFAULTED') NOT NULL
);
```

- **Mantenimento e affitti** sono operazioni ledger con `idem_key` deterministica (`upkeep:<plot>:<settimana>`), quindi un job eseguito due volte non addebita due volte.
- **Mancato pagamento:** `OWNED → DELINQUENT` (14 giorni di tolleranza) `→ AUCTION → AVAILABLE`. Il contenuto va nella **cassetta postale** del proprietario (`mailbox`), che ricrea gli oggetti dal DB.
- **Job pianificati:** tabella `scheduled_job` con *lease* (`locked_by`, `locked_until`), così con più processi un job gira una sola volta.

### Limiti anti-farm

| Meccanismo | Implementazione |
| --- | --- |
| Colture | Annulla `BlockGrowEvent` e `BlockFertilizeEvent` nei plot |
| Spawner, hopper, redstone di produzione | Denylist di materiali in `BlockPlaceEvent` + contatori per plot |
| Allevamento e mob | Annulla `CreatureSpawnEvent` ed enti oltre il cap per plot |
| Audit periodico | `ChunkSnapshot` presa sul main, analizzata in async |

---

## 10. Banca e azioni

```sql
CREATE TABLE share_holding (
  season_id SMALLINT UNSIGNED, uuid BINARY(16), qty INT UNSIGNED NOT NULL,
  PRIMARY KEY (season_id, uuid), CHECK (qty <= 1500)
);
CREATE TABLE share_order (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, season_id SMALLINT UNSIGNED NOT NULL,
  owner BINARY(16) NOT NULL, side ENUM('BUY','SELL') NOT NULL,
  price BIGINT NOT NULL, qty INT UNSIGNED NOT NULL, remaining INT UNSIGNED NOT NULL,
  status ENUM('OPEN','FILLED','CANCELLED') NOT NULL, created_at DATETIME(3) NOT NULL
);
```

### Matching engine

- **Single-writer** sul thread `MARKET`: il book (per lato, ordinato per prezzo e poi tempo) è in memoria e ricostruito dal DB all'avvio.
- **Pattern commit-poi-applica:** il motore calcola i *fill*, il persistence layer li committa in **una sola transazione** (ledger + holdings + ordini), e solo dopo il book in memoria viene aggiornato. Se il commit fallisce, il book resta com'era.
- **Tetto 15% (1.500 azioni)** controllato prima del matching e imposto dal `CHECK` del DB come seconda barriera.
- **IPO:** tranche a prezzo fisso, acquisto con `SELECT ... FOR UPDATE` sul contatore della tranche.

### Dividendi settimanali

1. Snapshot del saldo di `SYS_TAX_POOL` e delle holding in `dividend_run` (stato `RUNNING`).
2. Quota ai soci = 40% del pool, pro-rata sulle azioni emesse; arrotondamento per difetto, il resto va a `SYS_SINK`.
3. Pagamenti in batch da 500, `idem_key = div:<run>:<uuid>`: rilanciare il job dopo un crash è sicuro.
4. Il restante 60% finanzia il montepremi eventi/tornei o viene distrutto, secondo il tunable.

### Bonus per soglia

```java
enum ModifierType { MARKET_FEE, PLOT_UPKEEP, STORAGE_PAGES, EXTRACTION_TICKET }  // niente tipi di combattimento
```

Un `BonusService` restituisce solo valori di questi tipi, con **clamp totale a -1000 punti base (-10%)**. Poiché l'enum non contiene tipi di danno, difesa o raid, un bonus di potere non è *esprimibile* nel codice: la regola "no pay-to-win / no imbattibili" è garantita dal compilatore.

---

## 11. Arene e Colosseum

| Componente | Progetto |
| --- | --- |
| Bracket | Eliminazione diretta, partecipanti riempiti a potenza di 2 con *bye*, seeding per ELO; funzione pura in `core-domain` |
| Match | `PENDING → READY → PREP → LIVE → FINISHED` oppure `FORFEIT` / `ABORTED` |
| Pool arene | Arene predefinite con lock: un match prende un'arena libera, la rilascia a fine partita |
| Kit | Kit fisso per modalità, uguale per tutti |
| Inventario | **Snapshot salvato nel DB prima del teleport** (`inventory_snapshot`) e ripristinato a fine match; se il server cade, al login successivo lo snapshot pendente viene applicato prima di tutto |
| ELO | `K` configurabile, un rating per modalità |
| Premi | Transazioni ledger con `idem_key = prize:<torneo>:<rank>` |
| Spettatori | Modalità spettatore con tribune, teleport limitato e nascosti dalla tab list |

---

## 12. Stagioni

### Dati stagionali e globali

| Stagionali (`season_id`) | Globali (persistono) |
| --- | --- |
| `account`, `ledger_*`, `claim`, `plot`, `core_item`, `share_*`, `zone_*` | `player`, `cosmetic_unlock`, `title`, `trophy`, `entitlement`, `rift_credit_ledger`, statistiche storiche |

Il reset **non cancella**: apre un nuovo `season_id` e i conti si creano in modo pigro al primo accesso. Lo storico resta interrogabile.

### Pipeline di reset

| Passo | Azione | Punto di ripristino |
| --- | --- | --- |
| 1 FREEZE | Modalità manutenzione, ingressi bloccati, job sospesi | – |
| 2 SNAPSHOT | Backup completo forzato (DB + mondo), vedi infrastruttura D9 | Sì |
| 3 ARCHIVE | Statistiche e classifiche scritte in `season_archive_*` | Sì |
| 4 ROTATE | Nuova riga `season`, aggiornamento `current_season` | Sì |
| 5 WORLD SWAP | Nuovo mondo survival e Frattura da template | No (usa lo snapshot) |
| 6 REOPEN | Verifica di salute, riapertura | – |

Bonus di partenza per titoli passati: piccola transazione `SYS_FAUCET → giocatore`, con tetto fisso e tunable. Nessun vantaggio di potere.

---

## 13. Monetizzazione e muro di separazione

**Obiettivo (I6):** rendere *impossibile in codice* ciò che la Monetization Policy vieta.

| Livello | Misura |
| --- | --- |
| Schema | `monetization.*` separato da `game.*`, con utente DB dedicato (`bc_store`) senza permessi su `game` |
| Modulo | `module-store` **non dipende** da `LedgerService`: verificato da un test ArchUnit nel CI |
| Valuta | `rift_credit_ledger` è un ledger a parte, senza percorso verso `account` |
| Prodotti | `ProductPolicy` valida ogni prodotto al caricamento |

### ProductPolicy

Ogni prodotto dichiara una `category` (`COSMETIC`, `SOCIAL`, `STARTER`) e una lista di `effects` da una whitelist. L'avvio del modulo **fallisce** se un prodotto contiene un effetto vietato (valuta, azioni, plot, componenti Extraction/Raid, statistiche di combattimento) o se un `STARTER` non ha `max_per_account`, soglia di progressione, `bound = true` e assenza di casualità.

### Flusso d'acquisto

1. Webhook di Tebex sul **server web separato**, con verifica HMAC.
2. Scrittura in `purchase` con `external_id UNIQUE` (idempotente) e stato `PAID`.
3. Il core fa polling ogni \~10 s, consegna (cosmetico, bundle in `mailbox`) e passa a `DELIVERED`.
4. Rimborso o chargeback: stato `REVOKED`, revoca dell'entitlement; i Rift Credits non consentono nuovi acquisti finché il saldo non torna positivo.

### Oggetti legati all'account

Tag PDC `breach:bound=<uuid>`. Gli eventi di drop, pickup, trasferimento e inserimento nel Mercato vengono annullati se l'oggetto non appartiene al giocatore; non può essere venduto a NPC.

---

## 14. Audit, anomalie e prestazioni

### Audit e anomalie

- `audit_log` append-only per ogni azione admin, ogni cambio di tunable e ogni evento di sicurezza.
- **Regole di anomalia** eseguite ogni 5 minuti con query SQL: flusso netto in entrata per account oltre soglia, Core creati da un giocatore oltre il cap, serial duplicati, picchi di transazioni, scambi di azioni circolari tra gli stessi giocatori.
- Alert su un canale Discord privato dello staff.

### Strumenti admin

| Comando | Funzione |
| --- | --- |
| `/breach tx <uuid>` | Storico transazioni di un giocatore |
| `/breach rollback tx <id>` | Crea una **transazione compensativa**, non cancella mai righe |
| `/breach freeze <uuid>` | Blocca i movimenti di un account in attesa di verifica |
| `/breach health` | Stato dei moduli, DB, code, ultimo job |

### Budget prestazionali (obiettivo 100 giocatori)

| Voce | Budget |
| --- | --- |
| Tempo medio del plugin per tick | ≤ 5 ms (10% del tick) |
| Lookup di un claim | ≤ 50 µs |
| Blast | ≤ 2000 blocchi per tick |
| Query sincrone sul main | Zero |
| Handler caldi (`BlockBreak`, `Interact`, `Explode`) | O(1) o O(k) piccolo; niente `PlayerMoveEvent` salvo necessità |

Misura: profiler Spark e wrapper `Metrics.timer(...)` interni, log strutturati in JSON con correlation id per seguire una transazione dall'inizio alla fine.

---

## 15. Sicurezza applicativa

- Query **sempre parametrizzate**, nessuna concatenazione di SQL.
- **Lock per giocatore** (`PlayerLock`) che serializza le operazioni critiche (acquisto, craft, arma Core): il doppio click non duplica nulla.
- Ogni azione da GUI porta un `action_id` usato come chiave di idempotenza.
- Permessi per comando e per sottocomando; operazioni su economia e oggetti solo Admin/Owner, con audit.
- Segreti solo da file con permessi ristretti, mai nel repository né nei log.
- Rate limit su comandi di pagamento, Mercato e scambio.

---

## 16. Test e CI

| Livello | Cosa | Strumento |
| --- | --- | --- |
| Unit | `core-domain` (ledger rules, bracket, matching) | JUnit |
| Property | Invarianti del ledger e del matching con sequenze casuali | jqwik |
| Integrazione | Migrazioni, DAO, ledger con N thread concorrenti | Testcontainers + MariaDB |
| Server | Listener e flussi su Paper | MockBukkit (limitato) + server di staging |
| Carico | 120 bot sul server di staging | Bot swarm, misura di MSPT |
| Caos | `kill -9` durante craft, estrazione e match, poi verifica del recupero | Script manuale |

**CI:** build, test, ArchUnit (regole di dipendenza e muro monetizzazione), test di migrazione dalla versione precedente, shadowJar versionato. **Rilascio:** staging → snapshot pre-modifica → produzione.

---

## 17. Piano di sviluppo a milestone

Pensato per sessioni brevi: ogni milestone è utilizzabile da sola. Stime in taglie (S/M/L) e non in ore, perché il tempo a disposizione varia.

| Milestone | Contenuto | Dipende da | Definition of Done | Taglia |
| --- | --- | --- | --- | --- |
| **M0** | Scheletro: Gradle, moduli, Hikari, Flyway, config, `/breach health` | – | Il plugin parte, le migrazioni girano, health OK | S |
| **M1** | Ledger, conti, audit, tunables, `/pay` `/balance` | M0 | Test di proprietà verdi; 1000 transazioni concorrenti senza violare le invarianti; riconciliazione notturna | M |
| **M2** | Claim, Tool Cupboard, tier, protezione dagli eventi esterni | M0 | Nessun danno esterno sui claim; indice spaziale entro budget | M–L |
| **M3** | Oggetti, Breach Core, gate, blast, shield | M1, M2 | Test di dupe superato; raid completo su base di prova | L |
| **M4** | Mailbox, Mercato, escrow | M1, M3 | Vendita di un Core con trasferimento atomico | M |
| **M5** | Extraction: cicli, sessioni, loot, cap, scambio del mondo | M1, M3 | Run completa con estrazione, morte e reset senza intervento manuale | L |
| **M6** | Plot, affitti, limiti anti-farm | M1, M4 | Ciclo affitto→mora→asta funzionante | M |
| **M7** | Banca, azioni, dividendi, bonus | M1 | Matching testato con property test; dividendo rilanciabile senza doppio pagamento | M |
| **M8** | Arene e Colosseum minimo (1v1, 2v2) | M1 | Torneo di 8 giocatori completato con premi e ripristino inventari | M–L |
| **M9** | Pipeline di stagione | Tutti i moduli | Reset di prova su staging con snapshot e rollback | M |
| **M10** | Store, ProductPolicy, Tebex | M4 | ArchUnit verde; acquisto di prova consegnato una sola volta | M |

**Loop minimo giocabile:** M0–M5 (produzione → Core → raid → Extraction → vendita). Plot, banca, arene, stagioni e store si aggiungono senza toccare il loop.

---

## 18. Rischi tecnici e decisioni aperte

| Rischio | Mitigazione |
| --- | --- |
| **Righe calde nel ledger:** `SYS_SINK` e `SYS_TAX_POOL` ricevono quasi ogni transazione e creano contesa sui lock | Suddividere i conti di sistema in shard (`SINK_0..SINK_15`, scelti per hash) e sommarli solo per le metriche; da aggiungere a §5 se il test di concorrenza (M1) mostra contesa |
| Danno blocchi nel PDC dei chunk: dimensione e costo di serializzazione | Struttura sparsa con limite per chunk; misura in M2 |
| Scambio del mondo Frattura: errori di unload o chunk ancora caricati | Prova ripetuta su staging, rimozione forzata dei giocatori prima dello scambio |
| Consistenza tra processi dopo la divisione | Job con lease, idempotenza ovunque, Redis solo se serve la notifica in tempo reale |
| Complessità per un solo sviluppatore | Milestone indipendenti, loop minimo in M0–M5, funzioni opzionali dietro feature flag |

### Decisioni ancora aperte

- Versione Paper e Java da bloccare.
- Comportamento definitivo alla disconnessione in zona (sez. 8).
- Costi in valuta degli upgrade di tier e valori di base dei tunable (da calibrare in beta).
- Soglia a cui introdurre Redis e divisione in due processi (vedi documento infrastruttura, sez. 11).
- Governance dei soci della Banca (voto sulle aliquote): rimandata a dopo il lancio.
