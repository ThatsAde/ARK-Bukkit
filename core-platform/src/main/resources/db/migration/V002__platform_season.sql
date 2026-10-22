-- Stagioni (spec §12): il reset apre un nuovo season_id, non cancella nulla. La pipeline di cambio stagione è M9.
CREATE TABLE season (
  id         SMALLINT UNSIGNED NOT NULL PRIMARY KEY,
  name       VARCHAR(32)       NOT NULL,
  started_at DATETIME(3)       NOT NULL,
  ended_at   DATETIME(3)       NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Una sola riga: la stagione corrente
CREATE TABLE current_season (
  singleton TINYINT UNSIGNED  NOT NULL PRIMARY KEY DEFAULT 1,
  season_id SMALLINT UNSIGNED NOT NULL,
  CONSTRAINT ck_current_season_singleton CHECK (singleton = 1),
  CONSTRAINT fk_current_season FOREIGN KEY (season_id) REFERENCES season (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO season (id, name, started_at) VALUES (1, 'Stagione 1', NOW(3));
INSERT INTO current_season (singleton, season_id) VALUES (1, 1);

-- Ricerca per nome (/pay, /balance <giocatore>); la collation _ci rende il confronto senza maiuscole
CREATE INDEX ix_player_name ON player (name);
