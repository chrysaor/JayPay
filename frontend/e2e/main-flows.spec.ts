import { expect, test, type Page } from '@playwright/test'

const web = process.env.E2E_WEB_URL ?? 'http://localhost:8090'
const admin = process.env.E2E_ADMIN_URL ?? 'http://localhost:8091'
const run = Date.now().toString(36)
const members = { merchant: '', payer: '' }
const payment = { id: '' }

async function signUp(page: Page, name: string, corporate: boolean) {
  await page.goto(`${web}/sign-up`)
  await page.getByLabel('Name').fill(name)
  await page.getByLabel('Email').fill(`${name.toLowerCase().replaceAll(' ', '-')}-${run}@example.com`)
  await page.getByLabel('Address').fill(`E2E-${run}`)
  if (corporate) await page.getByLabel('Corporate account').check()
  await page.getByRole('button', { name: 'Create account' }).click()
  const membershipId = await page.getByRole('status').locator('strong').innerText()
  expect(membershipId).toMatch(/^\d+$/)
  return membershipId
}

async function fillPassword(page: Page, password: string) {
  try {
    await page.locator('#password').fill(password)
  } catch {
    throw new Error('The Keycloak sign-in page did not take the password.')
  }
}

test.describe.configure({ mode: 'serial' })

test('a merchant signs up for a corporate account', async ({ page }) => {
  members.merchant = await signUp(page, 'E2E Merchant', true)
})

test('a member signs up, links a bank account, adds money and pays the merchant', async ({ page }) => {
  members.payer = await signUp(page, 'E2E Payer', false)
  await page.getByRole('link', { name: 'Go to my wallet' }).click()
  await expect(page.getByRole('heading', { name: 'Wallet' })).toBeVisible()

  await page.getByRole('link', { name: 'Bank account' }).click()
  await page.getByLabel('Bank name').fill('Jay Bank')
  await page.getByLabel('Account number').fill(`9002-${run}`)
  await page.getByRole('button', { name: 'Link account' }).click()
  await expect(page.getByText('Linked', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'JayPay', exact: true }).click()
  await page.getByLabel('Amount').fill('10000')
  await page.getByRole('button', { name: 'Add money' }).click()
  await expect(page.getByRole('status')).toContainText('10,000')
  await expect(page.locator('.amount')).toContainText('10,000')

  await page.getByRole('link', { name: 'Pay', exact: true }).click()
  await page.getByLabel('Merchant ID').fill(members.merchant)
  await page.getByLabel('Amount').fill('3000')
  await page.getByRole('button', { name: 'Pay', exact: true }).click()
  const receipt = page.getByRole('status')
  await expect(receipt).toContainText('approved')
  await expect(receipt).toContainText('3,000')
  payment.id = /Payment #(\d+) approved/.exec(await receipt.innerText())?.[1] ?? ''
  expect(payment.id).not.toBe('')

  await page.getByRole('link', { name: 'JayPay', exact: true }).click()
  await expect(page.locator('.amount')).toContainText('7,000')
})

test('a member signs in with the membership ID and sees the balance that is left', async ({ page }) => {
  await page.goto(`${web}/sign-in`)
  await page.getByLabel('Membership ID').fill(members.payer)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Wallet' })).toBeVisible()
  await expect(page.locator('.amount')).toContainText('7,000')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in to JayPay' })).toBeVisible()
})

test('staff sign in through Keycloak and follow the payment step by step in the event flow', async ({ page }) => {
  const username = process.env.E2E_STAFF_USERNAME
  const password = process.env.E2E_STAFF_PASSWORD
  if (!username || !password) throw new Error('Set E2E_STAFF_USERNAME and E2E_STAFF_PASSWORD, or run make e2e.')

  await page.goto(admin)
  await page.locator('#username').fill(username)
  await fillPassword(page, password)
  await page.locator('#kc-login').click()
  await expect(page.getByRole('heading', { name: 'Members' })).toBeVisible()

  await page.getByRole('link', { name: 'Event flow' }).click()
  await page.getByLabel('Type').selectOption({ label: 'Payment' })
  const flow = page.getByRole('row').filter({ has: page.getByRole('cell', { name: members.payer, exact: true }) })
  await expect(flow).toHaveCount(1)
  await expect(flow).toContainText('Succeeded')
  await expect(flow).toContainText('3,000')

  await flow.getByRole('link').click()
  await expect(page.getByRole('list', { name: 'Steps' }).getByRole('listitem')).toHaveCount(2)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByText('You signed out.')).toBeVisible()
})
