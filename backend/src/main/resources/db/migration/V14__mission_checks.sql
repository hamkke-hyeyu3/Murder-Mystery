-- flyway:executeInTransaction=false
-- mission_checked_at column exists since V5__game_state.sql
-- CONCURRENTLY avoids table write-lock during index build
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_players_mission_checked
    ON players(session_id)
    WHERE mission_checked_at IS NOT NULL;
