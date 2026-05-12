ALTER TABLE sessions
    DROP CONSTRAINT sessions_state_valid;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_state_valid
        CHECK (state IS NULL OR state IN ('intro', 'character_assignment', 'tutorial', 'round', 'vote', 'ending'));
