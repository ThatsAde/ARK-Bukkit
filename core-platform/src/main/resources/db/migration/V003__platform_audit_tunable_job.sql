-- Audit append-only (spec §14): azioni admin, cambi di tunable, eventi di sicurezza
CREATE TABLE audit_log (
  id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  at      DATETIME(3)  NOT NULL,
  actor   BINARY(16)   NULL,
  action  VARCHAR(48)  NOT NULL,
  subject VARCHAR(64)  NULL,
  detail  JSON         NULL,
  KEY ix_action_at (action, at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Tunable operativi modificabili a caldo (spec §3, E18); il tipo e i limiti sono dichiarati nel codice
CREATE TABLE tunable (
  tkey       VARCHAR(64)  NOT NULL PRIMARY KEY,
  value      VARCHAR(255) NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  updated_by BINARY(16)   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Job pianificati con lease (spec §9, E19): con più processi un job gira una volta sola
CREATE TABLE scheduled_job (
  job_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
  next_run_at  DATETIME(3)  NOT NULL,
  locked_by    VARCHAR(64)  NULL,
  locked_until DATETIME(3)  NULL,
  last_run_at  DATETIME(3)  NULL,
  last_status  VARCHAR(16)  NULL,
  last_error   VARCHAR(255) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
