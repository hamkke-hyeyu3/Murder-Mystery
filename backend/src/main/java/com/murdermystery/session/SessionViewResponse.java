package com.murdermystery.session;

import java.util.List;

public record SessionViewResponse(
    String sessionId,
    String inviteCode,
    String scenarioId,
    String phase,
    int requiredCharacterCount,
    int joinedCount,
    List<PlayerSummary> players,
    String state,
    Integer currentRoundNumber,
    List<String> turnOrder,
    RoundView round,
    TurnView currentTurn,
    List<OccupancyView> locationOccupancy,
    MeView me
) {
    public record RoundView(
        int roundNumber,
        String prompt,
        String commonHint,
        long startedAt,
        long deadlineAt
    ) {}

    public record TurnView(
        int turnIndex,
        String playerId,
        String characterId,
        long deadlineAt,
        List<String> candidateLocationIds
    ) {}

    public record OccupancyView(
        String locationId,
        String playerId,
        String characterId,
        boolean autoSelected
    ) {}

    public record ClueView(
        String id,
        String itemId,
        String title,
        String originLocationId,
        int roundNumberDiscovered,
        long discoveredAt,
        String source
    ) {}

    public record MeView(
        String playerId,
        String nickname,
        boolean isHost,
        String assignedCharacterId,
        CharacterCardView character,
        ObjectiveView objective,
        Long tutorialAckedAt,
        List<ClueView> myClues
    ) {}

    public record CharacterCardView(
        String characterId,
        String name,
        int turnOrderIndex,
        String speechStyle,
        String background,
        String motive,
        String alibi,
        String secret,
        String relationships,
        LocationRef alibiLocation,
        List<ItemRef> items
    ) {}

    public record LocationRef(String id, String name, String icon) {}

    public record ItemRef(String id, String title, LocationRef originLocation) {}

    public record ObjectiveView(int roundNumber, int totalRounds, String objective) {}
}
