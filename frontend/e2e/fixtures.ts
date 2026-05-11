import { expect, type Page, type Browser, type BrowserContext } from '@playwright/test'

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
