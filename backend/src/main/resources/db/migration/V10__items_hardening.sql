-- Hibernate @Version column for optimistic lock on Clue (future-proofs against lock-scope narrowing)
ALTER TABLE clues ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- DB-level idempotency: share_all is unique per (session, round, clue)
CREATE UNIQUE INDEX item_actions_share_all_idx
    ON item_actions(session_id, round_number, actor_clue_id)
    WHERE action_type = 'share_all';

-- DB-level idempotency: exchange is unique per (session, round, sorted clue pair)
-- LEAST/GREATEST ensures A↔B and B↔A map to the same row
CREATE UNIQUE INDEX item_actions_exchange_idx
    ON item_actions(session_id, round_number,
                    LEAST(actor_clue_id, target_clue_id),
                    GREATEST(actor_clue_id, target_clue_id))
    WHERE action_type = 'exchange';

-- Covering index for idempotency-check queries and audit lookups
CREATE INDEX item_actions_actor_idx
    ON item_actions(session_id, round_number, action_type, actor_player_id, actor_clue_id);
