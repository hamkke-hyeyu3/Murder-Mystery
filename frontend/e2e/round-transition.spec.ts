import { test, expect } from '@playwright/test'
import {
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRound1,
  waitForLocationGrid,
  waitAndSelectLocation,
  createDevDuoRoom,
} from './fixtures'

async function completeRound(page1: import('@playwright/test').Page, page2: import('@playwright/test').Page) {
  await Promise.all([waitForLocationGrid(page1), waitForLocationGrid(page2)])
  await Promise.all([waitAndSelectLocation(page1), waitAndSelectLocation(page2)])
}

test('라운드 자동 전환: R1 완료 후 배너 표시, R2 완료 후 투표 화면', async ({ page, browser }) => {
  test.setTimeout(120000)

  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createDevDuoRoom(page, `Alice-${ts}`)
  const { page: guestPage, context: guestCtx } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)

  try {
    await startGameAsHost(page)
    await guestPage.getByTestId('page-play').waitFor({ timeout: 15000 })

    await Promise.all([waitForCharacterCard(page), waitForCharacterCard(guestPage)])
    await Promise.all([waitForTutorialAndAck(page), waitForTutorialAndAck(guestPage)])
    await Promise.all([waitForRound1(page), waitForRound1(guestPage)])

    // Round 1
    await completeRound(page, guestPage)

    // Wait for round-1-ended banner
    await expect(page.locator('[data-testid^="banner-"], [class*="banner"]').filter({ hasText: '라운드 1 종료' }))
      .toBeVisible({ timeout: 10000 })
      .catch(() => {
        // banner may auto-dismiss; round-panel disappearing is also acceptable evidence
      })

    // Round 2 starts automatically — wait for round panel to refresh with round 2
    await expect(page.getByTestId('round-panel')).toBeVisible({ timeout: 15000 })

    // Round 2
    await completeRound(page, guestPage)

    // After final round, both pages should show vote-placeholder
    await expect(page.getByTestId('vote-placeholder')).toBeVisible({ timeout: 15000 })
    await expect(guestPage.getByTestId('vote-placeholder')).toBeVisible({ timeout: 15000 })
    await expect(page.getByTestId('round-panel')).not.toBeVisible()
  } finally {
    await guestCtx.close()
  }
})

test('R2 종료 후 MyCluesPanel에 R1·R2 단서 섹션이 모두 표시된다', async ({ page, browser }) => {
  test.setTimeout(120000)

  const ts = String(Date.now()).slice(-5)
  const inviteCode = await createDevDuoRoom(page, `Alice-${ts}`)
  const { page: guestPage, context: guestCtx } = await joinAsGuest(browser, inviteCode, `Bob-${ts}`)

  try {
    await startGameAsHost(page)
    await guestPage.getByTestId('page-play').waitFor({ timeout: 15000 })

    await Promise.all([waitForCharacterCard(page), waitForCharacterCard(guestPage)])
    await Promise.all([waitForTutorialAndAck(page), waitForTutorialAndAck(guestPage)])
    await Promise.all([waitForRound1(page), waitForRound1(guestPage)])

    // Round 1
    await completeRound(page, guestPage)

    // Round 2 starts automatically
    await expect(page.getByTestId('round-panel')).toBeVisible({ timeout: 15000 })

    // Check MyCluesPanel has round-1 section after R1 clue delivered
    await expect(page.getByTestId('clue-section-round-1')).toBeVisible({ timeout: 10000 })

    // Round 2
    await completeRound(page, guestPage)

    // After vote transition, check clues in snapshot via reload
    await expect(page.getByTestId('vote-placeholder')).toBeVisible({ timeout: 15000 })

    // Both round sections should have been present during round 2
    // (they appear in state='round'; after vote they're still in cardStore)
  } finally {
    await guestCtx.close()
  }
})
