import { test, expect } from '@playwright/test'
import { createRoomAsHost } from './fixtures'

test('호스트가 방을 생성하면 Lobby에 도달하고 초대코드·자신이 표시된다', async ({ page }) => {
  const nickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(page, nickname)

  expect(inviteCode).toMatch(/^\d{6}$/)

  const raw = await page.evaluate(() => localStorage.getItem('mm:lastSession'))
  expect(raw).not.toBeNull()
  const last = JSON.parse(raw!)
  expect(last.isHost).toBe(true)
  expect(last.inviteCode).toBe(inviteCode)

  await expect(page.getByText(nickname)).toBeVisible()
  await expect(page.getByText('(호스트)')).toBeVisible()
})
