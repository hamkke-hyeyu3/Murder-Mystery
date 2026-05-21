-- mission_checked_at column exists since V5__game_state.sql
-- index for efficient per-session "all missions checked" count
CREATE INDEX idx_players_mission_checked
    ON players(session_id)
    WHERE mission_checked_at IS NOT NULL;
