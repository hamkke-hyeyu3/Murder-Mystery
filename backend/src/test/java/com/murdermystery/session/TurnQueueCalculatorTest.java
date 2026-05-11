package com.murdermystery.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnQueueCalculatorTest {

    private static final List<String> ORDER_3 = List.of("alice", "bob", "charlie");
    private static final List<String> ORDER_2 = List.of("alice", "bob");

    // ── n=3 snake 회전 ──

    @Test
    void round1_turn0_firstCharacter() {
        // k=1, i=0: ((1-1)+0) mod 3 = 0 → alice
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 1, 0)).isEqualTo("alice");
    }

    @Test
    void round1_turn1_secondCharacter() {
        // k=1, i=1: ((1-1)+1) mod 3 = 1 → bob
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 1, 1)).isEqualTo("bob");
    }

    @Test
    void round1_turn2_thirdCharacter() {
        // k=1, i=2: ((1-1)+2) mod 3 = 2 → charlie
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 1, 2)).isEqualTo("charlie");
    }

    @Test
    void round2_startsFromSecondCharacter() {
        // k=2, i=0: ((2-1)+0) mod 3 = 1 → bob
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 2, 0)).isEqualTo("bob");
    }

    @Test
    void round2_wrapsAround() {
        // k=2, i=2: ((2-1)+2) mod 3 = 0 → alice (wrap)
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 2, 2)).isEqualTo("alice");
    }

    @Test
    void round3_startsFromThirdCharacter() {
        // k=3, i=0: ((3-1)+0) mod 3 = 2 → charlie
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 3, 0)).isEqualTo("charlie");
    }

    @Test
    void round4_wrapsBackToFirst() {
        // k=4, i=0: ((4-1)+0) mod 3 = 0 → alice
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_3, 4, 0)).isEqualTo("alice");
    }

    // ── n=2 ──

    @Test
    void twoPlayers_round1_startsFirst() {
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_2, 1, 0)).isEqualTo("alice");
    }

    @Test
    void twoPlayers_round2_startsSecond() {
        // k=2, i=0: ((2-1)+0) mod 2 = 1 → bob
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_2, 2, 0)).isEqualTo("bob");
    }

    @Test
    void twoPlayers_round1_turn1_wraps() {
        // k=1, i=1: ((1-1)+1) mod 2 = 1 → bob
        assertThat(TurnQueueCalculator.characterIdAt(ORDER_2, 1, 1)).isEqualTo("bob");
    }

    // ── 경계 ──

    @Test
    void emptyTurnOrder_throws() {
        assertThatThrownBy(() -> TurnQueueCalculator.characterIdAt(List.of(), 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTurnOrder_throws() {
        assertThatThrownBy(() -> TurnQueueCalculator.characterIdAt(null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
