import { test } from '@playwright/test'
import {
  createRoomAsHost,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRound1,
  waitForLocationGrid,
  waitAndSelectLocation,
  waitForRoundTurnsComplete,
  getFirstClueId,
  lookupPlayerIdViaExchangeSheet,
  shareFullClue,
  sharePartialClue,
  longPressClue,
  exchangeClue,
  waitForBannerByText,
} from './fixtures'

test('3 단말 아이템 3행위 — shareFull / sharePartial / exchange', async ({ page, browser }) => {
  test.setTimeout(120000)

  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createRoomAsHost(page, `Alice-${ts}`)
  const { page: bobPage, context: bobCtx } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)
  const { page: charliePage, context: charlieCtx } = await joinAsGuest(browser, inviteCode, `Charlie-${ts}`)
  const allPages = [page, bobPage, charliePage]

  try {
    // 1. 게임 시작 → 캐릭터 → 튜토리얼 → R1
    await startGameAsHost(page)
    await Promise.all(
      [bobPage, charliePage].map((p) => p.getByTestId('page-play').waitFor({ timeout: 15000 })),
    )
    await Promise.all(allPages.map(waitForCharacterCard))
    await Promise.all(allPages.map(waitForTutorialAndAck))
    await Promise.all(allPages.map(waitForRound1))
    await Promise.all(allPages.map(waitForLocationGrid))

    // 2. R1 회전 턴 3개 — 각자 1단서 발견 (CLUE_DELIVERED → ownedClues 갱신)
    await Promise.all(allPages.map(waitAndSelectLocation))
    await Promise.all(allPages.map(waitForRoundTurnsComplete))

    // 3. 즉시 reload → snapshot에서 allOwnedClues(R1 전체 단서) 수신
    //    R2 자동 진행 중이지만 turn 1 auto-select는 30s 후 → 이 window에서 3행위 실행
    await Promise.all(
      allPages.map(async (p) => {
        await p.reload()
        await p.getByTestId('page-play').waitFor({ timeout: 15000 })
        await waitForRound1(p)
      }),
    )

    // 4. 각 단말 단서 ID 추출 (reload 직후 myClues = R1 clue 1개 각각)
    const aliceClueId = await getFirstClueId(page)
    const bobClueId = await getFirstClueId(bobPage)
    const charlieClueId = await getFirstClueId(charliePage)

    // ── Action 1: shareFull ───────────────────────────────────────────
    // Alice가 전체 공유 → 3 단말 배너 수신
    await shareFullClue(page, aliceClueId)
    await Promise.all(
      allPages.map((p) => waitForBannerByText(p, `Alice-${ts}님이 단서를 전체 공개했습니다`)),
    )

    // ── Action 2: sharePartial ────────────────────────────────────────
    // Bob이 Charlie에게만 공유 (Alice 제외) → 3 단말 배너 수신 (recipients 1명)
    const charliePlayerId = await lookupPlayerIdViaExchangeSheet(bobPage, `Charlie-${ts}`, bobClueId)
    await sharePartialClue(bobPage, bobClueId, [charliePlayerId])
    await Promise.all(
      allPages.map((p) =>
        waitForBannerByText(p, `Bob-${ts}님이 단서를 일부에게 공유했습니다 (총 1명)`),
      ),
    )

    // ── Action 3: exchange ────────────────────────────────────────────
    // Charlie ↔ Alice 단서 교환
    // reload 덕분에 Charlie의 ownedClues에 Alice의 R1 단서가 포함돼 exchange-partner 활성화
    const alicePlayerId = await lookupPlayerIdViaExchangeSheet(charliePage, `Alice-${ts}`, charlieClueId)
    await longPressClue(charliePage, charlieClueId)
    await charliePage.getByTestId('action-exchange').click()
    await exchangeClue(charliePage, alicePlayerId, charlieClueId, aliceClueId)
    await Promise.all(allPages.map((p) => waitForBannerByText(p, '단서를 교환했습니다')))
  } finally {
    await bobCtx.close()
    await charlieCtx.close()
  }
})
