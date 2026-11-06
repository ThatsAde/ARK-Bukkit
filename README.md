# BreachCore

The core plugin for **BreachSMP**, a survival PvP server built around raiding, extraction runs and a player-driven economy. It runs on Paper 26.2 and Java 25.

The repository is called ARK-Bukkit because that's the platform layer's prefix in code (`ARKPlugin`, `ARKModule`, `ARKApi`).

This is a work in progress. The economy and the first half of the territory system are done and tested; raids, extraction, the market and the rest are planned (see [Roadmap](#roadmap)).

## What's in it today

**Economy (M1, done)**
- A double-entry ledger on MariaDB. Every balance change is a transaction with matching debit and credit entries, and nothing is ever updated in place.
- Every operation carries an idempotency key, so a double click, a retry or a crash halfway through can't charge anyone twice.
- Accounts are locked in id order to avoid deadlocks, with up to 3 retries if one happens anyway.
- A nightly reconciliation job recomputes every balance from the ledger. If a player's balance doesn't match, their account is frozen and the mismatch goes to the audit log.
- Player commands: `/balance` (`/bal`), `/pay <player> <amount>`.
- Admin commands: `/breach eco give|take`, `/breach tx`, `/breach rollback tx <id>` (posts a compensating transaction), `/breach freeze|unfreeze`, `/breach recon`.

**Territory (M2a, done; M2b next)**
- Rust-style Tool Cupboards. Placing one opens a cubic claim around it, and several cupboards can make up a single base.
- Access is by PIN, not by owner. Whoever knows the PIN can authorize themselves and manage the list. PINs are stored salted and hashed, and wrong guesses are rate limited and audited.
- Inside a claim, only authorized players can build, break player-placed blocks, open doors or use redstone. Containers stay open to anyone who reaches them, and natural terrain is still diggable by everyone, so the walls you build are your defense.
- Explosions, pistons, fluids, fire, withers, end crystals and the dragon can't touch a claim's structure.
- Block tiers come from the block material (wood, stone, reinforced, metal, armored), and a hammer upgrades a block to the next tier, paid for in materials.
- Claim lookups use an in-memory spatial index. The benchmark (5,000 claims, 200,000 random lookups) averages around 350 ns per lookup, against a budget of 50 µs.
- Admin commands: `/breach claim info`, `/breach claim remove <id> <reason>`.

**Platform**
- Module loading with dependency ordering and per-module feature flags in `config.yml`.
- Database pool (HikariCP) and schema migrations (Flyway) with one global migration sequence.
- Tunables: settings stored in the database that can be changed live with `/breach tunable get|set`. Every change is audited.
- A job scheduler with database leases, so a job runs once even with more than one server process.
- `/breach health` shows modules, database, queues, money supply and the last job runs.

## Design rules

A few rules the whole codebase follows. Most of the interesting decisions come from these.

1. **The database is the source of truth.** Memory is only a cache. Nothing valuable lives only in RAM.
2. **The main thread never does I/O.** Services return `CompletableFuture`s and hop back to the main thread only to touch the Bukkit API.
3. **Money only moves through the ledger.** There is no `UPDATE balance` anywhere else.
4. **Every state change is idempotent.**
5. **Amounts are `long` in minor units.** Never `double`.
6. **Startup fails closed.** If the database or a migration fails, the plugin disables itself with a clear message instead of running half-broken. The same goes for logins: if a profile can't load, the player doesn't get in.
7. **Modules only see the API.** They depend on `core-api` and `core-domain`, never on `core-platform` or on each other. The Gradle build enforces this.

## Project layout

| Module | What it holds |
| --- | --- |
| `core-api` | Interfaces, DTOs and events. No Bukkit. |
| `core-domain` | Pure logic: ledger rules, claim geometry, protection rules, tiers, module ordering. No Bukkit, no I/O, so tests run in milliseconds. |
| `core-platform` | The actual Paper plugin: bootstrap, database, executors, config, commands. The only module that wires implementations together, and the one that produces the final jar. |
| `core-paper-api` | A small bridge that gives modules access to the plugin instance and listeners without depending on the platform. |
| `module-economy` | Ledger, accounts, reconciliation, economy commands. |
| `module-territory` | Claims, Tool Cupboards, protection listeners, tiers, hammer. |
| `build-logic` | Gradle convention plugins (Java 25 toolchain, test setup, module dependency rules). |

## Tests

Around 220 tests across the project:

- **Unit tests** for everything in `core-domain`.
- **Property tests** (jqwik) for the ledger rules: random sequences of transactions must never create or destroy money.
- **Integration tests** (`*IT`) against a real MariaDB 11.4. They include a concurrency test that fires 1,000 random transactions plus 100 duplicate submissions at the pool at once and then checks that every invariant still holds, and a reconciliation test that tampers with a balance behind the ledger's back and expects it to be caught.

The integration tests use an external database if `BREACH_IT_JDBC_URL` is set, fall back to Testcontainers if Docker is available, and are skipped otherwise. A plain `./gradlew build` on a fresh clone passes with the 60 integration tests skipped.

## Building

You need JDK 25 installed. The build uses a Java 25 toolchain, so Gradle itself can run on an older JDK as long as 25 is available on the machine.

```bash
./gradlew build                        # compile, test, build the plugin jar
./gradlew :core-platform:shadowJar     # just the jar: core-platform/build/libs/
./gradlew :core-platform:runServer     # start a local Paper 26.2 test server
```

To run the integration tests against your own MariaDB:

```bash
export BREACH_IT_JDBC_URL=jdbc:mariadb://127.0.0.1:3306/breach_it
export BREACH_IT_DB_USER=bc_it
export BREACH_IT_DB_PASSWORD=...
./gradlew build
```

The tests wipe that schema, so its name has to end in `_it`.

## Running it on a server

1. Drop the jar into `plugins/` and start the server once. BreachCore writes its default `config.yml`, then disables itself because there's no database password yet.
2. Fill in `plugins/BreachCore/config.yml` with your MariaDB connection.
3. Put the database password in `plugins/BreachCore/secrets/db-password`. The password is never stored in `config.yml` and never logged.
4. Restart. Migrations run automatically.

## Roadmap

| Milestone | Status |
| --- | --- |
| M0 Skeleton: plugin boot, migrations, health | Done |
| M1 Ledger, accounts, audit, tunables, `/pay` `/balance` | Done |
| M2a Claims, Tool Cupboards, PIN, protection, tiers, hammer | Done |
| M2b Block damage, upkeep in materials, decay, structural stability | Next |
| M3 Breach Core items, raids, shields | Planned |
| M4 Mailbox, market, escrow | Planned |
| M5 Extraction runs | Planned |
| M6 to M10 Plots and rent, bank and shares, arenas, season resets, store | Planned |

## Docs

The internal docs are in Italian, since that's the language the server team works in:

- `BreachCore – Specifica tecnica del core plugin.md`: the full technical spec.
- `map.md`: a map of every module, package and class.
- `log.md`: a dated log of every change, with the decisions taken and the real test results.
- `docs/roadmap.md`: the milestone plan in more detail.

Code, identifiers and the database schema are in English. Comments, Javadoc and player-facing messages are in Italian.

## License

MIT, see [LICENSE](LICENSE).
