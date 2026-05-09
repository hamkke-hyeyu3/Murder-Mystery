import { test, expect } from '@playwright/test'
import { createRoomAsHost } from './fixtures'

test('로비 새로고침 후 초대코드·합류자 목록·호스트 버튼이 복원된다', async ({ page }) => {
  const nickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(page, nickname)

  await page.reload()
  await page.getByTestId('page-lobby').waitFor()

  await expect(page.locator('.text-5xl')).toHaveText(inviteCode)
  await expect(page.getByText(nickname)).toBeVisible()
  await expect(page.getByRole('button', { name: '게임 시작' })).toBeVisible()
})
