CREATE UNIQUE INDEX idx_players_session_device ON players(session_id, device_id) WHERE device_id IS NOT NULL;
