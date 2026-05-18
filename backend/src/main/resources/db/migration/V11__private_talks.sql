CREATE TABLE private_talks (
    id                   UUID         PRIMARY KEY,
    session_id           UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number         INT          NOT NULL,
    requester_player_id  UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    target_player_id     UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    requested_at         TIMESTAMPTZ  NOT NULL,
    started_at           TIMESTAMPTZ  NULL,
    ended_at             TIMESTAMPTZ  NULL,
    end_reason           TEXT         NULL CHECK (end_reason IN ('REJECTED','TIMEOUT','USER_ENDED','ROUND_BOUNDARY')),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_distinct_players CHECK (requester_player_id <> target_player_id)
);

-- 세션당 동시에 하나의 비종료 row만 허용
CREATE UNIQUE INDEX uq_private_talks_active_one_per_session
    ON private_talks(session_id) WHERE end_reason IS NULL;

CREATE INDEX ix_private_talks_session_round
    ON private_talks(session_id, round_number);
