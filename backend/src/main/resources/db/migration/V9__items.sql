-- current_owner_player_id: exchange 시 소유권 이전 추적 (ACL은 monotonic이지만 소유권은 mutable)
ALTER TABLE clues
    ADD COLUMN current_owner_player_id UUID;
UPDATE clues SET current_owner_player_id = discovered_by_player_id;
ALTER TABLE clues
    ALTER COLUMN current_owner_player_id SET NOT NULL;
ALTER TABLE clues
    ADD CONSTRAINT clues_owner_fk
        FOREIGN KEY (current_owner_player_id) REFERENCES players(id) ON DELETE CASCADE;
CREATE INDEX clues_owner_idx ON clues(session_id, current_owner_player_id);

-- item_actions: 아이템 행위 audit log (exchange / share_all / share_partial)
CREATE TABLE item_actions (
    id                    UUID         PRIMARY KEY,
    session_id            UUID         NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    round_number          INT          NOT NULL,
    action_type           VARCHAR(16)  NOT NULL,
    actor_player_id       UUID         NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    target_player_id      UUID         NULL REFERENCES players(id) ON DELETE CASCADE,
    actor_clue_id         UUID         NULL REFERENCES clues(id) ON DELETE CASCADE,
    target_clue_id        UUID         NULL REFERENCES clues(id) ON DELETE CASCADE,
    recipient_player_ids  UUID[]       NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT item_actions_type_valid CHECK (action_type IN ('exchange','share_all','share_partial'))
);
CREATE INDEX item_actions_session_round_idx ON item_actions(session_id, round_number);
