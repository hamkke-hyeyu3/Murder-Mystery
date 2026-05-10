ALTER TABLE sessions
    ADD COLUMN current_round_number INT NULL;

CREATE TABLE rounds (
    session_id    UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number  INT          NOT NULL,
    prompt        TEXT         NOT NULL,
    common_hint   TEXT         NULL,
    started_at    TIMESTAMPTZ  NOT NULL,
    deadline_at   TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ  NULL,
    PRIMARY KEY (session_id, round_number),
    CONSTRAINT rounds_number_positive CHECK (round_number >= 1)
);
