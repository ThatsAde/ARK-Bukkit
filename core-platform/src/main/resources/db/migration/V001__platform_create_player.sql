-- Giocatori conosciuti dal server. Tabella globale: non ha season_id (spec §12).
CREATE TABLE player (
  uuid       BINARY(16)   NOT NULL PRIMARY KEY,
  name       VARCHAR(16)  NOT NULL,
  first_seen DATETIME(3)  NOT NULL,
  last_seen  DATETIME(3)  NOT NULL,
  flags      INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
