ALTER TABLE survey_responses
    ALTER COLUMN platform_score TYPE integer,
    ALTER COLUMN work_score TYPE integer;

-- V14's CREATE INDEX CONCURRENTLY deadlocks with Flyway Community's schema history lock.
-- If V14 left a partial or absent index, recreate it cleanly here.
DROP INDEX IF EXISTS idx_players_mission_checked;
CREATE INDEX IF NOT EXISTS idx_players_mission_checked
    ON players(session_id)
    WHERE mission_checked_at IS NOT NULL;
