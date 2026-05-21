import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { e2eFixture } from './helpers/paths'

const uniqueEmail = (prefix: string) =>
  `${prefix}-${Date.now()}@students.waikato.ac.nz`

async function register(
  page: import('@playwright/test').Page,
  email: string,
  nickname: string
) {
  await page.goto('/register')
  await page.fill('input[name="email"]', email)
  await page.fill('input[name="password"]', 'Pass1234')
  await page.fill('input[name="confirmPassword"]', 'Pass1234')
  await page.fill('input[name="nickname"]', nickname)
  await page.click('button[type="submit"]')
  await page.waitForURL('/')
}

async function login(
  page: import('@playwright/test').Page,
  email: string
) {
  await page.goto('/login')
  await page.fill('input[name="email"]', email)
  await page.fill('input[name="password"]', 'Pass1234')
  await page.click('button[type="submit"]')
  await page.waitForURL('/')
}

async function createListing(page: import('@playwright/test').Page, title: string) {
  await page.goto('/listings/new')
  await page.fill('input[name="title"]', title)
  await page.fill('textarea[name="description"]', 'Messaging test listing')
  await page.selectOption('select[name="categoryCode"]', 'BOOKS')
  await page.selectOption('select[name="listingType"]', 'SELL')
  await page.fill('input[name="price"]', '20.00')
  const testImage = e2eFixture(import.meta.url, 'fixtures/test-image.jpg')
  await page.setInputFiles('input[name="image"]', testImage)
  await page.click('button[type="submit"]')
  await page.waitForURL('/listings/mine')
}

test.describe('Messaging Flow', () => {
  let sellerEmail: string
  let buyerEmail: string
  let sellerNickname: string
  let buyerNickname: string
  const listingTitle = `Msg-Test-Book-${Date.now()}`

  test.beforeEach(() => {
    resetRateLimits()
  })

  test.beforeAll(async ({ browser }) => {
    const ts = Date.now()
    sellerEmail = uniqueEmail(`seller-${ts}`)
    buyerEmail = uniqueEmail(`buyer-${ts}`)
    sellerNickname = `Seller${ts}`
    buyerNickname = `Buyer${ts}`

    // Register seller and create a listing
    const sellerPage = await browser.newPage()
    await register(sellerPage, sellerEmail, sellerNickname)
    await createListing(sellerPage, listingTitle)
    await sellerPage.close()

    // Register buyer
    const buyerPage = await browser.newPage()
    await register(buyerPage, buyerEmail, buyerNickname)
    await buyerPage.close()
  })

  test('buyer can contact seller from listing detail page', async ({ page }) => {
    await login(page, buyerEmail)

    // Find the listing via browse
    await page.goto('/browse')
    await page.fill('input[placeholder*="Search"]', listingTitle)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(1000)

    // Click on the listing
    await page.locator(`text=${listingTitle}`).first().click()
    await page.waitForURL(/\/listings\/\d+/)

    // Click Contact seller
    await page.click('button:has-text("Contact seller")')
    await page.waitForURL(/\/conversations\/\d+/)

    // Verify we landed in a chat
    await expect(page.locator('button:has-text("Send")')).toBeVisible()
  })

  test('buyer can send a message and see it in chat', async ({ page }) => {
    await login(page, buyerEmail)
    await page.goto('/conversations')
    await page.waitForLoadState('networkidle')

    // Open the first conversation (button inside <li>)
    await page.locator('main ul li button').first().click()
    await page.waitForURL(/\/conversations\/\d+/)

    const messageText = `Hello from buyer at ${Date.now()}`
    await page.fill('textarea', messageText)
    await page.click('button:has-text("Send")')

    // Message appears in chat
    await expect(page.locator(`text=${messageText}`)).toBeVisible()
  })

  test('seller sees unread badge after buyer sends message', async ({ browser }) => {
    const ts = Date.now()
    const localSeller = uniqueEmail(`s2-${ts}`)
    const localBuyer = uniqueEmail(`b2-${ts}`)
    const localNick = `S2${ts}`
    const buyNick = `B2${ts}`
    const title = `Badge-Test-${ts}`

    // Setup: seller creates listing
    const sellerPage = await browser.newPage()
    await register(sellerPage, localSeller, localNick)
    await createListing(sellerPage, title)

    // Setup: buyer sends message
    const buyerPage = await browser.newPage()
    await register(buyerPage, localBuyer, buyNick)
    await buyerPage.goto('/browse')
    await buyerPage.fill('input[placeholder*="Search"]', title)
    await buyerPage.keyboard.press('Enter')
    await buyerPage.waitForTimeout(1000)
    await buyerPage.locator(`text=${title}`).first().click()
    await buyerPage.waitForURL(/\/listings\/\d+/)
    await buyerPage.click('button:has-text("Contact seller")')
    await buyerPage.waitForURL(/\/conversations\/\d+/)
    await buyerPage.fill('textarea', 'Hey, is this still available?')
    await buyerPage.click('button:has-text("Send")')
    await buyerPage.close()

    // Seller checks navbar unread badge
    await sellerPage.goto('/')
    await sellerPage.waitForTimeout(2000) // allow polling cycle
    const badge = sellerPage.locator('nav a[href="/conversations"] span')
    await expect(badge).toBeVisible()
    await sellerPage.close()
  })

  test('cannot send message to own listing', async ({ page }) => {
    await login(page, sellerEmail)

    // Seller visits own listing via public detail (use direct URL since search may double-match)
    await page.goto('/listings/mine')
    await page.locator(`a:has-text("${listingTitle}")`).first().click()
    // Owner detail page (not public). Public detail page is what matters for Contact seller button.
    await page.waitForURL(/\/listings\/\d+$/)
    const url = page.url()
    const id = url.match(/\/listings\/(\d+)/)?.[1]
    if (id) {
      await page.goto(`/listings/${id}/detail`)
      // Contact seller button should NOT be visible for the owner
      await expect(page.locator('button:has-text("Contact seller")')).not.toBeVisible()
    }
  })
})
