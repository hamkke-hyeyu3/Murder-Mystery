import { test, expect } from '@playwright/test'
import {
  createRoomAsHost,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRound1,
  getMyCharacterId,
} from './fixtures'

test('3 단말이 라운드 1에 동기화되고 각자 다른 캐릭터를 받는다', async ({ page, browser }) => {
  test.setTimeout(60000)

  const ts = String(Date.now()).slice(-5) // 최대 20자 닉네임 제한 준수
  // 1. 호스트 방 생성
  const inviteCode = await createRoomAsHost(page, `Alice-${ts}`)

  // 2. 게스트 2명 합류 (toy-manor 3인 시나리오 인원 충족)
  const { page: guestPage1, context: guestCtx1 } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)
  const { page: guestPage2, context: guestCtx2 } = await joinAsGuest(browser, inviteCode, `Charlie-${ts}`)

  try {
    // 3. 호스트가 게임 시작 — 3인 합류 후 버튼 활성화 대기 후 클릭
    await startGameAsHost(page)

    // 4. 게스트 페이지도 page-play로 자동 전환
    await Promise.all([
      guestPage1.getByTestId('page-play').waitFor({ timeout: 15000 }),
      guestPage2.getByTestId('page-play').waitFor({ timeout: 15000 }),
    ])

    // 5. 3 단말 모두 캐릭터 카드 도착 대기 (5초 character_assignment 전이)
    await Promise.all([
      waitForCharacterCard(page),
      waitForCharacterCard(guestPage1),
      waitForCharacterCard(guestPage2),
    ])

    // 6. 본인 카드 ACL: 3 단말의 characterId가 모두 distinct
    const [hostCharId, guest1CharId, guest2CharId] = await Promise.all([
      getMyCharacterId(page),
      getMyCharacterId(guestPage1),
      getMyCharacterId(guestPage2),
    ])
    expect(new Set([hostCharId, guest1CharId, guest2CharId]).size).toBe(3)

    // 7. 튜토리얼: 3 단말 모두 확인 클릭 → 마지막 ack 시 서버가 라운드 전이
    await Promise.all([
      waitForTutorialAndAck(page),
      waitForTutorialAndAck(guestPage1),
      waitForTutorialAndAck(guestPage2),
    ])

    // 8. 3 단말 모두 라운드 1 패널 도달
    await Promise.all([
      waitForRound1(page),
      waitForRound1(guestPage1),
      waitForRound1(guestPage2),
    ])

    // 9. 라운드 번호 동기 확인
    await expect(page.getByTestId('round-number')).toHaveText('라운드 1')
    await expect(guestPage1.getByTestId('round-number')).toHaveText('라운드 1')
    await expect(guestPage2.getByTestId('round-number')).toHaveText('라운드 1')

    // toy-manor 라운드 1은 common_hint가 없으므로 round-number 동기로 충분
  } finally {
    await guestCtx1.close()
    await guestCtx2.close()
  }
})
