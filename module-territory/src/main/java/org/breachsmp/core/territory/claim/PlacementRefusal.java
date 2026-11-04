package org.breachsmp.core.territory.claim;

/** Motivo per cui un Tool Cupboard non si può piazzare (design M2, T3–T4). */
public enum PlacementRefusal {
    /** Il mondo non è tra quelli del territorio ({@code territory.yml} {@code worlds}). */
    WORLD_DISABLED,
    /** Il nuovo claim tocca un claim (anche solo riservato) in cui il giocatore non è autorizzato. */
    OVERLAPS_FOREIGN_CLAIM,
    /** Il giocatore ha già creato {@code territory.max_cupboards_per_player} claim. */
    LIMIT_REACHED,
    /** C'è già un cupboard in quella posizione. */
    POSITION_TAKEN
}
