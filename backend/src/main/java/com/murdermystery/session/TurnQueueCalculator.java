package com.murdermystery.session;

import java.util.List;

final class TurnQueueCalculator {

    private TurnQueueCalculator() {}

    /**
     * 라운드 k의 i번째 차례 캐릭터 ID를 반환한다.
     * snake 회전: turn_order[((k-1) + i) mod n]
     * - k: 라운드 번호 (1-indexed)
     * - i: 라운드 내 차례 인덱스 (0-indexed)
     * - n: 캐릭터 수
     */
    static String characterIdAt(List<String> turnOrder, int roundNumber, int turnIndex) {
        if (turnOrder == null || turnOrder.isEmpty()) {
            throw new IllegalArgumentException("turnOrder must not be empty");
        }
        int n = turnOrder.size();
        int slot = ((roundNumber - 1) + turnIndex) % n;
        return turnOrder.get(slot);
    }
}
