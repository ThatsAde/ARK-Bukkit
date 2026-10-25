# Roadmap BreachCore (M0–M10)

Dalla spec §17. Ogni milestone ha un piano dettagliato in `docs/superpowers/plans/` **scritto solo quando la precedente è chiusa**, così parte dal codice reale e non da ipotesi. Questa pagina dice cosa contiene ciascuna e cosa va deciso prima di pianificarla.

Loop minimo giocabile: **M0–M5**. Plot, banca, arene, stagioni e store si aggiungono senza toccare il loop.

| Milestone | Stato | Piano | Dipende da | Definition of Done (spec) |
| --- | --- | --- | --- | --- |
| **M0** Scheletro | ✅ completata (2026-10-22) | `plans/2026-10-21-m0-scheletro.md` | – | Il plugin parte, le migrazioni girano, health OK |
| **M1** Ledger, conti, audit, tunables, `/pay` `/balance` | ✅ completata (2026-10-25) | `plans/2026-10-22-m1a-piattaforma.md`, `plans/2026-10-22-m1b-ledger.md` | M0 | Property test verdi; 1000 tx concorrenti senza violare invarianti; riconciliazione notturna |
| **M2** Claim, Tool Cupboard, tier, protezione eventi | ⏳ | – | M0 | Nessun danno esterno sui claim; indice spaziale entro budget |
| **M3** Oggetti, Breach Core, gate, blast, shield | ⏳ | – | M1, M2 | Test di dupe superato; raid completo su base di prova |
| **M4** Mailbox, Mercato, escrow | ⏳ | – | M1, M3 | Vendita di un Core con trasferimento atomico |
| **M5** Extraction | ⏳ | – | M1, M3 | Run completa con estrazione, morte e reset senza intervento manuale |
| **M6** Plot, affitti, anti-farm | ⏳ | – | M1, M4 | Ciclo affitto → mora → asta funzionante |
| **M7** Banca, azioni, dividendi, bonus | ⏳ | – | M1 | Matching testato con property test; dividendo rilanciabile senza doppio pagamento |
| **M8** Arene e Colosseum (1v1, 2v2) | ⏳ | – | M1 | Torneo di 8 con premi e ripristino inventari |
| **M9** Pipeline di stagione | ⏳ | – | tutti | Reset di prova su staging con snapshot e rollback |
| **M10** Store, ProductPolicy, Tebex | ⏳ | – | M4 | ArchUnit verde; acquisto di prova consegnato una sola volta |

M0 è chiusa (2026-10-22). M1 è chiusa (2026-10-25): DoD verificata con test di proprietà, 1000 transazioni concorrenti e riconciliazione su MariaDB reale (vedi `log.md`).

---

## Note per pianificare ciascuna milestone

### M1 – Ledger ✅ (spec §4, §5, §14 audit, §15)
- Fatto: piattaforma (stagione, profilo, audit, tunable, job con lease, PlayerLock, login fail-closed, comandi dei moduli senza Bukkit) e `module-economy` (ledger JDBC, `/balance`, `/pay`, `/breach eco|tx|rollback|freeze|unfreeze|recon`, riconciliazione notturna). Dettagli in `log.md` e `map.md` §8.
- Rischio §18 (righe calde): con lock ordinati 1100 invii concorrenti → 0 retry per deadlock; shard non necessari ora, da rivalutare con il test di carico su staging.

### M1-shop – Negozio server (prossima milestone economica, design M1 §3)
- Il negozio che compra materiali da farm con prezzo decrescente è la fonte principale di denaro (GDD §3): GUI e curve di prezzo proprie, sopra `LedgerService` (`SYS_FAUCET → giocatore`).
- Prima: brainstorming del design (curve, cap per giocatore/giorno, anti-farm) e un nuovo tipo di transazione in `TxKinds`.

### M2 – Territorio (spec §6)
- Indice spaziale `chunkKey → claims`, AABB; budget lookup ≤ 50 µs (benchmark).
- Tier da `Material` in config; protezione da esplosioni, pistoni, fluidi, wither/crystal/dragon.
- Rischio §18: dimensione del danno nel PDC dei chunk → misurare.
- Introdurre `Metrics.timer(...)` per i budget §14.

### M3 – Breach Core (spec §6 blast/gate, §7)
- Tabella `core_item`, stati, arma con `UPDATE ... WHERE state='HELD'`, scansione serial doppi, limite 3 Core, `pending_craft`.
- `PlayerLock` (spec §15), `action_id` per GUI.

### M4 – Mailbox, Mercato, escrow
- Escrow su `SYS_ESCROW`; item ricreato dal DB all'acquirente nella stessa transazione ledger.
- Da decidere qui (D-33): servono plugin di negozio di terze parti (Vault/ChestShop) o basta il Mercato interno? L'API di Vault è sincrona sul main thread (in conflitto con I2/I3).

### M5 – Extraction (spec §8)
- `zone_cycle`, `extraction_session`, `extraction_point`, `zone_supply`, `loot_spawn`, `loot_event`; scambio del mondo template.
- Decisione aperta: comportamento alla disconnessione (linger 15 s, da validare in beta).

### M6–M10
- M6: `plot`, `rent_contract`, `mailbox`, job con `idem_key` deterministica; limiti anti-farm.
- M7: book in memoria single-writer su `MARKET`, commit-poi-applica, `CHECK (qty <= 1500)`, dividendi idempotenti, `BonusService` con enum chiuso.
- M8: bracket puro in `core-domain`, `inventory_snapshot`, ELO.
- M9: pipeline FREEZE → SNAPSHOT → ARCHIVE → ROTATE → WORLD SWAP → REOPEN.
- M10: schema `monetization.*` separato con utente `bc_store`, ArchUnit "module-store non dipende da LedgerService", `ProductPolicy`, polling `purchase`.

### Trasversali (da inserire nella milestone che li richiede per prima)
- Log strutturati JSON con correlation id (spec §14).
- Regole di anomalia ogni 5 minuti + alert Discord.
- CI (build, test, ArchUnit, test di migrazione, shadowJar versionato): quando esiste un remote git.
- Ricaricamento a caldo della config (D-07).
