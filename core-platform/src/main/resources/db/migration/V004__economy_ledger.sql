-- Conti per stagione (spec §4). frozen_at è un'aggiunta allo schema della spec: freeze per conto (E13).
-- I conti di sistema si creano in modo pigro come gli altri (D-19).
CREATE TABLE account (
  id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id  SMALLINT UNSIGNED                NOT NULL,
  owner_type ENUM('PLAYER', 'TEAM', 'SYSTEM') NOT NULL,
  owner_ref  VARBINARY(16)                    NOT NULL,
  balance    BIGINT                           NOT NULL DEFAULT 0,
  version    BIGINT UNSIGNED                  NOT NULL DEFAULT 0,
  frozen_at  DATETIME(3)                      NULL,
  UNIQUE KEY uq_owner (season_id, owner_type, owner_ref),
  CONSTRAINT ck_account_balance CHECK (owner_type = 'SYSTEM' OR balance >= 0),
  CONSTRAINT fk_account_season FOREIGN KEY (season_id) REFERENCES season (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only (I3): il codice non fa mai UPDATE o DELETE su ledger_tx e ledger_entry
CREATE TABLE ledger_tx (
  id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id  SMALLINT UNSIGNED NOT NULL,
  idem_key   CHAR(36)          NOT NULL,
  kind       VARCHAR(32)       NOT NULL,
  actor      BINARY(16)        NULL,
  created_at DATETIME(3)       NOT NULL,
  meta       JSON              NULL,
  UNIQUE KEY uq_ledger_tx_idem (idem_key),
  KEY ix_ledger_tx_season (season_id, created_at),
  CONSTRAINT fk_ledger_tx_season FOREIGN KEY (season_id) REFERENCES season (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ledger_entry (
  tx_id         BIGINT UNSIGNED  NOT NULL,
  seq           TINYINT UNSIGNED NOT NULL,
  account_id    BIGINT UNSIGNED  NOT NULL,
  delta         BIGINT           NOT NULL,
  balance_after BIGINT           NOT NULL,
  PRIMARY KEY (tx_id, seq),
  KEY ix_account (account_id, tx_id),
  CONSTRAINT fk_entry_tx FOREIGN KEY (tx_id) REFERENCES ledger_tx (id),
  CONSTRAINT fk_entry_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
