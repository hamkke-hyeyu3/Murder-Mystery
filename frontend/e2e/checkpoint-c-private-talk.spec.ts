import { test, expect, type Page } from '@playwright/test'
import {
  createRoomAsHost,
  joinAsGuest,
  startGameAsHost,
  waitForCharacterCard,
  waitForTutorialAndAck,
  waitForRound1,
} from './fixtures'

test.setTimeout(150000)

/** 닉네임으로 RequestPrivateTalkPanel의 밀담 버튼을 클릭 */
async function requestPrivateTalk(page: Page, targetNickname: string): Promise<void> {
  const panel = page.getByTestId('private-talk-request-panel')
  await panel.waitFor({ timeout: 10000 })
  // 버튼 부모(row div)의 span 텍스트로 대상을 정확히 식별
  const buttons = panel.getByRole('button', { name: '밀담' })
  const count = await buttons.count()
  for (let i = 0; i < count; i++) {
    const btn = buttons.nth(i)
    const nick = await btn.locator('..').locator('span').textContent()
    if (nick?.trim() === targetNickname) {
      await btn.click()
      return
    }
  }
  throw new Error(`밀담 대상 '${targetNickname}'을 패널에서 찾을 수 없음`)
}

/** 3-단말(Alice·Bob·Charlie) 셋업 후 라운드 1 진입까지 */
async function setupRound1(page: Page, browser: Parameters<typeof joinAsGuest>[0]) {
  const ts = String(Date.now()).slice(-5)
  const aliceNick = `Alice${ts}`
  const bobNick = `Bob${ts}`
  const charlieNick = `Char${ts}`

  const inviteCode = await createRoomAsHost(page, aliceNick)
  const { page: bobPage, context: bobCtx } = await joinAsGuest(browser, inviteCode, bobNick)
  const { page: charliePage, context: charlieCtx } = await joinAsGuest(browser, inviteCode, charlieNick)

  await startGameAsHost(page)
  await Promise.all([page, bobPage, charliePage].map(waitForCharacterCard))
  await Promise.all([page, bobPage, charliePage].map(waitForTutorialAndAck))
  await Promise.all([page, bobPage, charliePage].map(waitForRound1))

  return { bobPage, charliePage, bobCtx, charlieCtx, aliceNick, bobNick, charlieNick }
}

test('밀담 신청 → 수락 → 3단말 배너 표시', async ({ page, browser }) => {
  const { bobPage, charliePage, bobCtx, charlieCtx, aliceNick, bobNick } =
    await setupRound1(page, browser)

  try {
    // Alice가 Bob에게 밀담 신청
    await requestPrivateTalk(page, bobNick)

    // Alice: 신청 중 카드 표시
    await expect(page.getByTestId('private-talk-pending')).toBeVisible({ timeout: 10000 })
    // Bob: 수신 카드 표시
    await expect(bobPage.getByTestId('private-talk-incoming')).toBeVisible({ timeout: 10000 })

    // Bob 수락
    await bobPage.getByTestId('private-talk-accept').click()

    // 3단말 모두 배너에 두 닉네임 표시
    await Promise.all(
      [page, bobPage, charliePage].map((p) =>
        expect(p.getByTestId('private-talk-banner')).toBeVisible({ timeout: 10000 })
      )
    )
    // 배너 텍스트에 두 닉네임 포함 (순서 무관하게 각각 체크)
    const bannerText = await page.getByTestId('private-talk-banner').textContent()
    expect(bannerText).toContain(aliceNick)
    expect(bannerText).toContain(bobNick)
    expect(bannerText).toContain('밀담 중')
  } finally {
    await bobCtx.close()
    await charlieCtx.close()
  }
})

test('밀담 신청 → 거절 → 수신 카드 즉시 소멸', async ({ page, browser }) => {
  const { bobPage, bobCtx, charlieCtx, bobNick } = await setupRound1(page, browser)

  try {
    await requestPrivateTalk(page, bobNick)

    // Bob: 수신 카드 표시
    await expect(bobPage.getByTestId('private-talk-incoming')).toBeVisible({ timeout: 10000 })

    // Bob 거절
    await bobPage.getByTestId('private-talk-reject').click()

    // Bob: 수신 카드 즉시 사라짐 (setPendingPrivateTalk(null) 동기 호출)
    await expect(bobPage.getByTestId('private-talk-incoming')).not.toBeVisible({ timeout: 5000 })
    // 배너 미표시 (PRIVATE_TALK_STARTED 미발생)
    expect(await bobPage.getByTestId('private-talk-banner').count()).toBe(0)
  } finally {
    await bobCtx.close()
    await charlieCtx.close()
  }
})

test('밀담 진행 중 — 다른 플레이어 신청 버튼 비활성', async ({ page, browser }) => {
  const { bobPage, charliePage, bobCtx, charlieCtx, bobNick } =
    await setupRound1(page, browser)

  try {
    // Alice→Bob 밀담 시작
    await requestPrivateTalk(page, bobNick)
    await bobPage.getByTestId('private-talk-accept').click()

    // 3단말 모두 배너 확인 (STARTED broadcast 수신)
    await Promise.all(
      [page, bobPage, charliePage].map((p) =>
        expect(p.getByTestId('private-talk-banner')).toBeVisible({ timeout: 10000 })
      )
    )

    // Charlie: 패널 버튼들이 disabled (currentPrivateTalk !== null)
    const charliePanel = charliePage.getByTestId('private-talk-request-panel')
    await charliePanel.waitFor({ timeout: 5000 })
    const buttons = charliePanel.getByRole('button', { name: '밀담' })
    const count = await buttons.count()
    expect(count).toBeGreaterThan(0)
    for (let i = 0; i < count; i++) {
      await expect(buttons.nth(i)).toBeDisabled()
    }
  } finally {
    await bobCtx.close()
    await charlieCtx.close()
  }
})
