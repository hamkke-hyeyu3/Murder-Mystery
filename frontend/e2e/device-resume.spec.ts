import { test, expect } from '@playwright/test'
import { createRoomAsHost, joinAsGuest, reopenWithSameDevice } from './fixtures'

test('호스트 storageState 보존 시 / 진입 → /lobby로 자동 redirect', async ({ browser }) => {
  const ctxA = await browser.newContext()
  const pageA = await ctxA.newPage()
  const hostNickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(pageA, hostNickname)
  const state = await ctxA.storageState()
  await ctxA.close()

  const { ctx: ctxB, page: pageB } = await reopenWithSameDevice(browser, state)
  await pageB.goto('/')
  await expect(pageB).toHaveURL(new RegExp(`/lobby/${inviteCode}`), { timeout: 5000 })
  await expect(pageB.getByTestId('page-lobby')).toBeVisible()
  await ctxB.close()
})

test('게스트 재오픈 시 같은 닉네임으로 /lobby에 복원된다', async ({ browser }) => {
  const ctxHost = await browser.newContext()
  const pageHost = await ctxHost.newPage()
  const inviteCode = await createRoomAsHost(pageHost, `host-${Date.now()}`)

  const guestNickname = `guest-${Date.now()}`
  const { context: ctxGuest } = await joinAsGuest(browser, inviteCode, guestNickname)
  const guestState = await ctxGuest.storageState()
  await ctxGuest.close()

  const { ctx: ctxGuest2, page: pageGuest2 } = await reopenWithSameDevice(browser, guestState)
  await pageGuest2.goto('/')
  await expect(pageGuest2).toHaveURL(new RegExp(`/lobby/${inviteCode}`), { timeout: 5000 })
  await expect(pageGuest2.getByText(guestNickname)).toBeVisible({ timeout: 5000 })
  await expect(pageGuest2.getByText('(나)')).toBeVisible({ timeout: 5000 })

  await ctxGuest2.close()
  await ctxHost.close()
})

test('활성 세션 있을 때 / 직접 접속해도 /lobby로 redirect된다', async ({ page }) => {
  const hostNickname = `host-${Date.now()}`
  const inviteCode = await createRoomAsHost(page, hostNickname)

  await page.goto('/')
  await expect(page).toHaveURL(new RegExp(`/lobby/${inviteCode}`), { timeout: 5000 })
  await expect(page.getByTestId('page-lobby')).toBeVisible()
})

test('같은 deviceId로 재합류하면 같은 playerId를 반환한다', async ({ browser }) => {
  const ctxHost = await browser.newContext()
  const pageHost = await ctxHost.newPage()
  const inviteCode = await createRoomAsHost(pageHost, `host-${Date.now()}`)

  const guestNickname = `guest-${Date.now()}`
  const { context: ctxGuest, page: pageGuest } = await joinAsGuest(browser, inviteCode, guestNickname)

  const { deviceId, firstPlayerId } = await pageGuest.evaluate(() => ({
    deviceId: localStorage.getItem('mm:deviceId'),
    firstPlayerId: (JSON.parse(localStorage.getItem('mm:lastSession') ?? '{}') as { playerId?: string }).playerId,
  }))

  const secondJoinRes = await pageGuest.evaluate(
    async ({ ic, nn, did }: { ic: string; nn: string; did: string }) => {
      const res = await fetch(`/api/sessions/${ic}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Device-Id': did },
        body: JSON.stringify({ nickname: nn }),
      })
      return (await res.json()) as { playerId: string }
    },
    { ic: inviteCode, nn: guestNickname, did: deviceId! },
  )

  expect(secondJoinRes.playerId).toBe(firstPlayerId)

  await ctxGuest.close()
  await ctxHost.close()
})
