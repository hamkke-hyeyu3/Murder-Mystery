-- location_occupancy: 라운드별 장소 점유 (1 라운드 1 장소 1 방문자 원칙)
CREATE TABLE location_occupancy (
    session_id       UUID         NOT NULL,
    round_number     INT          NOT NULL,
    location_id      VARCHAR(64)  NOT NULL,
    player_id        UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    character_id     VARCHAR(64)  NOT NULL,
    selected_at      TIMESTAMPTZ  NOT NULL,
    is_auto_selected BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (session_id, round_number, location_id),
    CONSTRAINT location_occupancy_round_player_unique UNIQUE (session_id, round_number, player_id),
    CONSTRAINT location_occupancy_round_fk FOREIGN KEY (session_id, round_number)
        REFERENCES rounds(session_id, round_number) ON DELETE CASCADE
);
CREATE INDEX location_occupancy_session_round_idx ON location_occupancy(session_id, round_number);

-- clues: 플레이어가 발견한 단서 (monotonic — 삭제 금지)
CREATE TABLE clues (
    id                       UUID         PRIMARY KEY,
    session_id               UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number_discovered  INT          NOT NULL,
    item_id                  VARCHAR(64)  NOT NULL,
    origin_location_id       VARCHAR(64)  NOT NULL,
    title                    TEXT         NOT NULL,
    discovered_by_player_id  UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    discovered_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT clues_session_item_round_unique UNIQUE (session_id, item_id, round_number_discovered)
);
CREATE INDEX clues_session_round_idx ON clues(session_id, round_number_discovered);
CREATE INDEX clues_discovered_by_idx ON clues(session_id, discovered_by_player_id);

-- clue_acl: 단서 접근권 (monotonic — 삭제 금지, T-11에서 exchange/share로 확장)
CREATE TABLE clue_acl (
    clue_id    UUID         NOT NULL REFERENCES clues(id) ON DELETE CASCADE,
    player_id  UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ  NOT NULL,
    source     VARCHAR(16)  NOT NULL,
    PRIMARY KEY (clue_id, player_id)
);
CREATE INDEX clue_acl_player_idx ON clue_acl(player_id);
