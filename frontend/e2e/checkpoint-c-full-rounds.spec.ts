import { test, expect } from '@playwright/test'
import {
  createRoomAsHost,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRoundN,
  waitForLocationGrid,
  waitAndSelectLocation,
  expectClueSectionVisible,
} from './fixtures'

test('3 단말 풀 라운드 — R1→R2→R3 + 단서 monotonic + vote 전이', async ({ page, browser }) => {
  test.setTimeout(180000)

  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createRoomAsHost(page, `Alice-${ts}`)
  const { page: bobPage, context: bobCtx } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)
  const { page: charliePage, context: charlieCtx } = await joinAsGuest(browser, inviteCode, `Charlie-${ts}`)
  const allPages = [page, bobPage, charliePage]

  try {
    await startGameAsHost(page)
    await Promise.all(
      [bobPage, charliePage].map((p) => p.getByTestId('page-play').waitFor({ timeout: 15000 })),
    )

    await Promise.all(allPages.map(waitForCharacterCard))
    await Promise.all(allPages.map(waitForTutorialAndAck))
    await Promise.all(allPages.map((p) => waitForRoundN(p, 1)))

    // ── Round 1 ──────────────────────────────────────────────────────────
    await Promise.all(allPages.map(waitForLocationGrid))
    await Promise.all(allPages.map(waitAndSelectLocation))

    // R1 완료 → R2 자동 전환 대기 (R2 진입 = R1 완료 내재적 확인)
    await Promise.all(allPages.map((p) => waitForRoundN(p, 2)))
    // R1 단서가 R2 진입 후에도 보존됨 (monotonic)
    await Promise.all(allPages.map((p) => expectClueSectionVisible(p, 1)))

    // ── Round 2 ──────────────────────────────────────────────────────────
    await Promise.all(allPages.map(waitForLocationGrid))
    await Promise.all(allPages.map(waitAndSelectLocation))

    // R2 완료 → R3 자동 전환 대기
    await Promise.all(allPages.map((p) => waitForRoundN(p, 3)))
    // R1·R2 단서가 R3 진입 후에도 보존됨 (monotonic)
    await Promise.all(allPages.map((p) => expectClueSectionVisible(p, 1)))
    await Promise.all(allPages.map((p) => expectClueSectionVisible(p, 2)))

    // ── Round 3 ──────────────────────────────────────────────────────────
    await Promise.all(allPages.map(waitForLocationGrid))
    await Promise.all(allPages.map(waitAndSelectLocation))

    // R3 완료 → vote 전환 (마지막 라운드이므로 round-panel 사라지고 vote-placeholder 등장)
    await Promise.all(
      allPages.map((p) => p.getByTestId('vote-placeholder').waitFor({ timeout: 20000 })),
    )
    await Promise.all(
      allPages.map((p) => expect(p.getByTestId('round-panel')).not.toBeVisible()),
    )
  } finally {
    await bobCtx.close()
    await charlieCtx.close()
  }
})
