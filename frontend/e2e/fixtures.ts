import type { Page, Browser, BrowserContext } from '@playwright/test'

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
