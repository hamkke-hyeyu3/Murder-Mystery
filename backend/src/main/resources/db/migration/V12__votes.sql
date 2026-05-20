CREATE TABLE votes (
    session_id          UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_no            INT          NOT NULL CHECK (round_no IN (0, 1)),
    voter_player_id     UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    target_character_id TEXT         NOT NULL,
    voted_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (session_id, round_no, voter_player_id)
);

CREATE INDEX ix_votes_session_round
    ON votes(session_id, round_no);

-- 투표 결과를 세션에 기록 (재합류 시 복원용)
ALTER TABLE sessions
    ADD COLUMN vote_round_no            INT          NULL CHECK (vote_round_no IN (0, 1)),
    ADD COLUMN vote_outcome             TEXT         NULL CHECK (vote_outcome IN ('single_winner', 'tie', 'failed')),
    ADD COLUMN vote_winner_character_id TEXT         NULL,
    ADD COLUMN vote_deadline_at         TIMESTAMPTZ  NULL;
