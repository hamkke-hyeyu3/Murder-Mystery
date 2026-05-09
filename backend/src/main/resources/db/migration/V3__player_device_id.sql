ALTER TABLE players ADD COLUMN device_id UUID;
CREATE INDEX idx_players_device_id ON players(device_id);
