import { test, expect } from '@playwright/test'
import {
  createDevDuoRoom,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForLocationGrid,
  waitAndSelectLocation,
} from './fixtures'

test.setTimeout(180000)

/**
 * 2-player dev-duo: 모든 라운드 완주 후 투표 단계까지 진입.
 * vote-panel이 두 페이지 모두에 표시될 때까지 대기.
 */
async function driveToVote(page: Parameters<typeof createDevDuoRoom>[0], browser: Parameters<typeof joinAsGuest>[0]) {
  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createDevDuoRoom(page, `Host${ts}`)
  const { page: guestPage, context: guestCtx } = await joinAsGuest(browser, inviteCode, `Guest${ts}`)

  await startGameAsHost(page)
  await guestPage.getByTestId('page-play').waitFor({ timeout: 15000 })

  await Promise.all([page, guestPage].map(waitForCharacterCard))
  await Promise.all([page, guestPage].map(waitForTutorialAndAck))

  // dev-duo: 2 rounds × 2 turns each
  for (let r = 0; r < 2; r++) {
    await Promise.all([page, guestPage].map(waitForLocationGrid))
    await Promise.all([page, guestPage].map(waitAndSelectLocation))
  }

  // Wait for both pages to reach the vote panel (VOTE_STARTED received)
  await Promise.all(
    [page, guestPage].map((p) => p.getByTestId('vote-panel').waitFor({ timeout: 20000 })),
  )

  return { guestPage, guestCtx }
}

test('단독 승자 투표 → CULPRIT_REVEAL_STARTED → MISSION_REVEALED 누설 없음', async ({ page, browser }) => {
  const { guestPage, guestCtx } = await driveToVote(page, browser)

  try {
    // 두 플레이어 모두 "host" 캐릭터에 투표
    await page.getByTestId('vote-candidate-host').click()
    await page.getByTestId('vote-submit-btn').click()

    await guestPage.getByTestId('vote-candidate-host').click()
    await guestPage.getByTestId('vote-submit-btn').click()

    // CULPRIT_REVEAL_STARTED → reveal-result-winner 표시
    await expect(page.getByTestId('reveal-result-winner')).toBeVisible({ timeout: 10000 })
    await expect(guestPage.getByTestId('reveal-result-winner')).toBeVisible({ timeout: 10000 })

    // reveal-result-winner에 culprit 이름("호스트")이 포함됨
    await expect(page.getByTestId('reveal-result-winner')).toContainText('호스트')

    // 8초 후 MISSION_PHASE_STARTED → mission-panel 표시
    await expect(page.getByTestId('mission-panel')).toBeVisible({ timeout: 15000 })
    await expect(guestPage.getByTestId('mission-panel')).toBeVisible({ timeout: 15000 })

    // 호스트: "진범 색출" 미션이 표시되고, "의심 회피"(게스트 미션)는 없어야 함
    await expect(page.getByTestId('mission-panel')).toContainText('진범 색출')
    await expect(page.getByTestId('mission-panel')).not.toContainText('의심 회피')

    // 게스트: "의심 회피" 미션이 표시되고, "진범 색출"(호스트 미션)는 없어야 함
    await expect(guestPage.getByTestId('mission-panel')).toContainText('의심 회피')
    await expect(guestPage.getByTestId('mission-panel')).not.toContainText('진범 색출')

  } finally {
    await guestCtx.close()
  }
})

test('동점→재투표→재동점(failed) → CULPRIT_REVEAL_STARTED.culpritCharacterId = trueCulprit', async ({ page, browser }) => {
  const { guestPage, guestCtx } = await driveToVote(page, browser)

  try {
    // 라운드 0: 동점 (host→host, guest→guest)
    await page.getByTestId('vote-candidate-host').click()
    await page.getByTestId('vote-submit-btn').click()
    await guestPage.getByTestId('vote-candidate-guest').click()
    await guestPage.getByTestId('vote-submit-btn').click()

    // RUNOFF_STARTED → vote-panel에 재투표 배너
    await expect(page.getByTestId('vote-runoff-banner')).toBeVisible({ timeout: 10000 })

    // 라운드 1: 재동점 (host→host, guest→guest)
    await page.getByTestId('vote-candidate-host').click()
    await page.getByTestId('vote-submit-btn').click()
    await guestPage.getByTestId('vote-candidate-guest').click()
    await guestPage.getByTestId('vote-submit-btn').click()

    // failed → reveal-result-failed 표시 (trueCulprit = "게스트")
    await expect(page.getByTestId('reveal-result-failed')).toBeVisible({ timeout: 10000 })
    await expect(page.getByTestId('reveal-result-failed')).toContainText('색출 실패')
    await expect(page.getByTestId('reveal-result-failed')).toContainText('게스트')

  } finally {
    await guestCtx.close()
  }
})
