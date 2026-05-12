import { test, expect } from '@playwright/test'
import {
  createRoomAsHost,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRound1,
  waitForLocationGrid,
  waitAndSelectLocation,
  waitForLocationOccupied,
  waitForRoundTurnsComplete,
} from './fixtures'

test('3 단말 회전 턴 조사 — 각자 차례에 장소 선택 후 모든 차례 완료', async ({ page, browser }) => {
  test.setTimeout(120000)

  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createRoomAsHost(page, `Alice-${ts}`)
  const { page: guestPage1, context: guestCtx1 } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)
  const { page: guestPage2, context: guestCtx2 } = await joinAsGuest(browser, inviteCode, `Charlie-${ts}`)

  const allPages = [page, guestPage1, guestPage2]

  try {
    // 1. 게임 시작
    await startGameAsHost(page)
    await Promise.all([
      guestPage1.getByTestId('page-play').waitFor({ timeout: 15000 }),
      guestPage2.getByTestId('page-play').waitFor({ timeout: 15000 }),
    ])

    // 2. 캐릭터 카드 도착
    await Promise.all(allPages.map((p) => waitForCharacterCard(p)))

    // 3. 튜토리얼 확인 → 라운드 1 진입
    await Promise.all(allPages.map((p) => waitForTutorialAndAck(p)))
    await Promise.all(allPages.map((p) => waitForRound1(p)))

    // 4. TURN_STARTED 수신 대기 — 3 단말 모두 location-grid 표시
    await Promise.all(allPages.map((p) => waitForLocationGrid(p)))

    // 5. 3 플레이어 각자 자기 차례에 장소 선택 (병렬 — 순서는 서버 결정)
    const [loc1, loc2, loc3] = await Promise.all([
      waitAndSelectLocation(page),
      waitAndSelectLocation(guestPage1),
      waitAndSelectLocation(guestPage2),
    ])

    // 6. 선택된 장소가 모두 다른지 확인 (중복 점유 없음)
    expect(new Set([loc1, loc2, loc3]).size).toBe(3)

    // 7. 각 단말에서 선택된 장소들이 최종적으로 occupied로 표시되는지 확인
    //    (자신이 선택한 장소는 이미 선택 완료, 타인 장소만 확인)
    await waitForLocationOccupied(page, loc2)
    await waitForLocationOccupied(page, loc3)
    await waitForLocationOccupied(guestPage1, loc1)
    await waitForLocationOccupied(guestPage2, loc1)

    // 8. 모든 차례 완료 — 3 단말 모두 "이번 라운드 조사 완료" 표시
    await Promise.all(allPages.map((p) => waitForRoundTurnsComplete(p)))

    // 9. 라운드 패널은 여전히 표시
    await expect(page.getByTestId('round-panel')).toBeVisible()
  } finally {
    await guestCtx1.close()
    await guestCtx2.close()
  }
})
