import { expect, type Page, type Browser, type BrowserContext, type Locator } from '@playwright/test'

export async function createRoomAsHost(page: Page, hostNickname: string): Promise<string> {
  await page.goto('/')
  await page.getByRole('button', { name: '세션 만들기' }).first().waitFor()
  await page.getByRole('button', { name: '세션 만들기' }).first().click()
  await page.getByPlaceholder('닉네임').fill(hostNickname)
  await page.getByRole('button', { name: '확인' }).click()
  await page.getByTestId('page-lobby').waitFor()
  const inviteCode = await page.locator('.text-5xl').textContent()
  if (!inviteCode) throw new Error('inviteCode를 찾을 수 없음')
  return inviteCode.trim()
}

export async function joinAsGuest(
  browser: Browser,
  inviteCode: string,
  guestNickname: string,
): Promise<{ page: Page; context: Awaited<ReturnType<Browser['newContext']>> }> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(`/join?invite=${inviteCode}`)
  await page.getByPlaceholder('닉네임').fill(guestNickname)
  await page.getByRole('button', { name: '합류' }).click()
  await page.getByTestId('page-lobby').waitFor()
  return { page, context }
}

export async function reopenWithSameDevice(
  browser: Browser,
  state: Awaited<ReturnType<BrowserContext['storageState']>>,
): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext({ storageState: state })
  const page = await ctx.newPage()
  return { ctx, page }
}

export async function startGameAsHost(page: Page): Promise<void> {
  const btn = page.getByRole('button', { name: '게임 시작' })
  await expect(btn).toBeEnabled({ timeout: 10000 })
  await btn.click()
  await page.getByTestId('page-play').waitFor({ timeout: 15000 })
}

export async function waitForCharacterCard(page: Page): Promise<void> {
  await page.getByTestId('character-card').waitFor({ timeout: 15000 })
}

export async function waitForTutorialAndAck(page: Page): Promise<void> {
  // tutorial 단계 자체가 없으면 조기 반환 (이미 round로 전이)
  try {
    await page.getByTestId('tutorial').waitFor({ timeout: 10000 })
  } catch {
    return
  }
  // isAcked=true이면 tutorial-confirm이 disabled DOM에 존재 — 클릭 건너뜀
  const btn = page.getByTestId('tutorial-confirm')
  if (!(await btn.isDisabled())) {
    await btn.click()
  }
}

export async function waitForRound1(page: Page): Promise<void> {
  await page.getByTestId('round-panel').waitFor({ timeout: 15000 })
}

export async function getMyCharacterId(page: Page): Promise<string> {
  // waitForCharacterCard 호출 후 실행 전제 — 별도 waitFor 불필요
  const id = await page.getByTestId('character-card').getAttribute('data-character-id')
  if (!id) throw new Error('data-character-id를 찾을 수 없음')
  return id
}

// ── LocationGrid helpers ─────────────────────────────────────────────

/** location-grid가 나타날 때까지 대기 (TURN_STARTED 도착 후) */
export async function waitForLocationGrid(page: Page): Promise<void> {
  // 첫 차례가 올 때까지, 또는 이미 완료됐을 경우 complete 표시 대기
  const locator = page.locator('[data-testid="location-grid"],[data-testid="location-grid-complete"]')
  await locator.first().waitFor({ timeout: 15000 })
}

/** 이 페이지가 현재 자기 차례(활성 후보 버튼 존재)인지 반환 */
export async function isMyTurn(page: Page): Promise<boolean> {
  const grid = page.getByTestId('location-grid')
  if (!(await grid.isVisible().catch(() => false))) return false
  const count = await grid.locator('button:not([disabled])[data-testid^="location-cell-"]').count()
  return count > 0
}

/**
 * 자기 차례가 올 때까지 대기하고 첫 번째 활성 후보 셀을 클릭.
 * 클릭한 locationId를 반환.
 */
export async function waitAndSelectLocation(page: Page): Promise<string> {
  // 활성 버튼이 나타날 때까지 대기 (타인 차례 동안 자기 차례로 전환)
  const btn = page.locator('button:not([disabled])[data-testid^="location-cell-"]').first()
  await btn.waitFor({ timeout: 45000 })
  const testId = (await btn.getAttribute('data-testid')) ?? ''
  const locationId = testId.replace('location-cell-', '')
  await btn.click()
  return locationId
}

/** 지정된 locationId 셀에 점유자가 표시될 때까지 대기하고 닉네임을 반환 */
export async function waitForLocationOccupied(page: Page, locationId: string): Promise<string> {
  const cell = page.getByTestId(`location-cell-${locationId}`)
  await expect(cell).toHaveAttribute('data-occupied-by', /.+/, { timeout: 10000 })
  return (await cell.getAttribute('data-occupied-by')) ?? ''
}

/** 모든 차례 완료 표시 대기 */
export async function waitForRoundTurnsComplete(page: Page): Promise<void> {
  await page.getByTestId('location-grid-complete').waitFor({ timeout: 15000 })
}

// ── Item action helpers ───────────────────────────────────────────────

/** clue-item-{clueId} 요소를 500ms+ pointerdown으로 long-press → ClueActionSheet 대기 */
export async function longPressClue(page: Page, clueId: string): Promise<void> {
  const el = page.getByTestId(`clue-item-${clueId}`)
  await el.waitFor({ timeout: 10000 })
  const box = await el.boundingBox()
  if (!box) throw new Error(`clue ${clueId} bounding box not found`)
  const cx = box.x + box.width / 2
  const cy = box.y + box.height / 2
  await el.dispatchEvent('pointerdown', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, pointerType: 'mouse', pointerId: 1, pressure: 0.5, button: 0, buttons: 1 })
  await page.waitForTimeout(600)
  await el.dispatchEvent('pointerup', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, pointerType: 'mouse', pointerId: 1, pressure: 0, button: 0, buttons: 0 })
  await page.getByTestId('clue-action-sheet').waitFor({ timeout: 3000 })
}

/** long-press → 전체 공유 클릭 */
export async function shareFullClue(page: Page, clueId: string): Promise<void> {
  await longPressClue(page, clueId)
  await page.getByTestId('action-share-full').click()
}

/** long-press → 부분 공유 → recipient 체크박스 ON → 확정 */
export async function sharePartialClue(
  page: Page,
  clueId: string,
  recipientPlayerIds: string[],
): Promise<void> {
  await longPressClue(page, clueId)
  await page.getByTestId('action-share-partial').click()
  for (const id of recipientPlayerIds) {
    await page.getByTestId(`recipient-${id}`).check()
  }
  await page.getByTestId('confirm-share-partial').click()
}

/** long-press → 교환 → partner 선택 → 내 단서·상대 단서 선택 → 교환 확정 */
export async function exchangeClue(
  page: Page,
  partnerPlayerId: string,
  requesterClueId: string,
  partnerClueId: string,
): Promise<void> {
  await page.getByTestId(`exchange-partner-${partnerPlayerId}`).click()
  await page.getByTestId(`exchange-my-clue-${requesterClueId}`).check()
  await page.getByTestId(`exchange-partner-clue-${partnerClueId}`).check()
  await page.getByTestId('confirm-exchange').click()
}

/** my-clues-panel 내 첫 번째 단서의 clueId 반환 (clue-item-{id} data-testid에서 추출) */
export async function getFirstClueId(page: Page): Promise<string> {
  const el = page.locator('[data-testid^="clue-item-"]').first()
  await el.waitFor({ timeout: 10000 })
  const testId = (await el.getAttribute('data-testid')) ?? ''
  return testId.replace('clue-item-', '')
}

/**
 * 닉네임으로 playerId 추출.
 * ClueActionSheet exchange-partner 모드를 잠시 진입해서 exchange-partner-{playerId} 버튼의 testid를 읽음.
 * ownerClueId: 진입에 필요한 내가 소유한 단서 ID (long-press 대상).
 */
export async function lookupPlayerIdViaExchangeSheet(
  page: Page,
  nickname: string,
  ownerClueId: string,
): Promise<string> {
  await longPressClue(page, ownerClueId)
  await page.getByTestId('action-exchange').click()
  const btn: Locator = page.locator(`[data-testid^="exchange-partner-"]`).filter({ hasText: nickname })
  await btn.waitFor({ timeout: 5000 })
  const testId = (await btn.getAttribute('data-testid')) ?? ''
  const playerId = testId.replace('exchange-partner-', '')
  await page.getByTestId('exchange-back').click()
  await page.getByTestId('action-cancel').click()
  return playerId
}

/** 배너 메시지에 partialText가 포함될 때까지 대기 (role=status로 개별 배너 item만 매칭) */
export async function waitForBannerByText(page: Page, partialText: string): Promise<void> {
  await expect(page.locator('[role="status"]').filter({ hasText: partialText })).toBeVisible({
    timeout: 10000,
  })
}
