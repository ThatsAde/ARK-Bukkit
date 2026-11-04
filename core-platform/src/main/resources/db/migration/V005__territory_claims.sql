-- Claim dei Tool Cupboard (spec §6, design M2 T2–T5): cubo di lato 2*radius+1 centrato sul cupboard.
-- Il raggio è quello in vigore alla creazione; un claim rimosso si cancella, la storia è in audit_log (D-35).
CREATE TABLE claim (
  id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  season_id              SMALLINT UNSIGNED NOT NULL,
  world_uuid             BINARY(16)        NOT NULL,
  cup_x                  INT               NOT NULL,
  cup_y                  INT               NOT NULL,
  cup_z                  INT               NOT NULL,
  radius                 SMALLINT UNSIGNED NOT NULL,
  created_by             BINARY(16)        NOT NULL,
  created_at             DATETIME(3)       NOT NULL,
  pin_salt               VARBINARY(32)     NULL,
  pin_hash               VARBINARY(64)     NULL,
  shield_until           DATETIME(3)       NULL,  -- M3
  raid_window_start      TINYINT UNSIGNED  NULL,  -- M3
  raid_window_changed_at DATETIME(3)       NULL,  -- M3
  UNIQUE KEY uq_claim_cupboard (season_id, world_uuid, cup_x, cup_y, cup_z),
  KEY ix_claim_creator (season_id, created_by),
  CONSTRAINT fk_claim_season FOREIGN KEY (season_id) REFERENCES season (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE claim_member (
  claim_id BIGINT UNSIGNED NOT NULL,
  uuid     BINARY(16)      NOT NULL,
  added_at DATETIME(3)     NOT NULL,
  added_by BINARY(16)      NULL,
  PRIMARY KEY (claim_id, uuid),
  CONSTRAINT fk_member_claim FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
