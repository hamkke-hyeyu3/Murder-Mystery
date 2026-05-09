import { test, expect } from '@playwright/test'
import { createRoomAsHost, joinAsGuest } from './fixtures'

test('게스트 합류 시 호스트 화면에 새로고침 없이 닉네임이 나타난다', async ({ page, browser }) => {
  const hostNickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(page, hostNickname)

  const guestNickname = `guest-${Date.now()}`
  const { context: guestCtx } = await joinAsGuest(browser, inviteCode, guestNickname)

  await expect(page.getByText(guestNickname)).toBeVisible({ timeout: 5000 })

  await guestCtx.close()
})

test('게스트 나가기 시 호스트 화면에서 사라진다', async ({ page, browser }) => {
  const hostNickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(page, hostNickname)

  const guestNickname = `guest-${Date.now()}`
  const { page: guestPage, context: guestCtx } = await joinAsGuest(browser, inviteCode, guestNickname)

  await expect(page.getByText(guestNickname)).toBeVisible({ timeout: 5000 })

  await guestPage.getByRole('button', { name: '나가기' }).click()

  await expect(page.getByText(guestNickname)).not.toBeVisible({ timeout: 5000 })

  await guestCtx.close()
})
