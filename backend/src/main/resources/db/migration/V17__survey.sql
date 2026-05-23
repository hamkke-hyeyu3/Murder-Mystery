CREATE TABLE survey_responses (
    session_id   uuid         NOT NULL REFERENCES sessions(id),
    player_id    uuid         NOT NULL REFERENCES players(id),
    platform_score smallint   CHECK (platform_score IS NULL OR (platform_score >= 1 AND platform_score <= 5)),
    work_score   smallint     CHECK (work_score IS NULL OR (work_score >= 1 AND work_score <= 5)),
    free_text    varchar(80),
    responded_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, player_id)
);

ALTER TABLE sessions
    DROP CONSTRAINT sessions_state_valid;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_state_valid
        CHECK (state IS NULL OR state IN (
            'intro', 'character_assignment', 'tutorial', 'round',
            'vote', 'reveal', 'mission', 'ending', 'debrief', 'survey', 'ended'
        ));
