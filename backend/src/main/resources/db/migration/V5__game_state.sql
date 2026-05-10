ALTER TABLE sessions
    ADD COLUMN state      VARCHAR(32)  NULL,
    ADD COLUMN turn_order TEXT[]       NULL,
    ADD CONSTRAINT sessions_state_valid
        CHECK (state IS NULL OR state IN ('intro', 'character_assignment', 'tutorial', 'round', 'ending'));

ALTER TABLE players
    ADD COLUMN assigned_character_id VARCHAR(64)  NULL,
    ADD COLUMN tutorial_acked_at     TIMESTAMPTZ  NULL,
    ADD COLUMN mission_checked_at    TIMESTAMPTZ  NULL;
