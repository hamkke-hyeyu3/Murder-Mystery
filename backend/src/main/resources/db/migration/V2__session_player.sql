CREATE TABLE sessions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    invite_code VARCHAR(6)  NOT NULL UNIQUE,
    scenario_id VARCHAR(64) NOT NULL,
    phase       VARCHAR(16) NOT NULL DEFAULT 'lobby',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sessions_invite_code_numeric CHECK (invite_code ~ '^[0-9]{6}$'),
    CONSTRAINT sessions_phase_valid         CHECK (phase IN ('lobby', 'in_progress', 'ended'))
);

CREATE INDEX idx_sessions_phase_updated_at ON sessions (phase, updated_at);

CREATE TABLE players (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    nickname   VARCHAR(20) NOT NULL,
    is_host    BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT players_nickname_unique UNIQUE (session_id, nickname)
);

CREATE INDEX idx_players_session ON players (session_id);
