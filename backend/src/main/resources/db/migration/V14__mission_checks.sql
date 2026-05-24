-- mission_checked_at column exists since V5__game_state.sql
CREATE INDEX IF NOT EXISTS idx_players_mission_checked
    ON players(session_id)
    WHERE mission_checked_at IS NOT NULL;
