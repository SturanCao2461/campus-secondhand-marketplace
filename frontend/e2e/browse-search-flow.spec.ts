import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { e2eFixture } from './helpers/paths'

const uniqueEmail = (prefix: string) => `${prefix}-${Date.now()}@students.waikato.ac.nz`

async function register(page: import('@playwright/test').Page, email: string, nickname: string) {
  await page.goto('/register')
  await page.fill('input[name="email"]', email)
  await page.fill('input[name="password"]', 'Pass1234')
  await page.fill('input[name="confirmPassword"]', 'Pass1234')
  await page.fill('input[name="nickname"]', nickname)
  await page.click('button[type="submit"]')
  await page.waitForURL('/')
}

async function createListing(
  page: import('@playwright/test').Page,
  title: string,
  category: string,
  price: string,
  listingType: 'SELL' | 'GIVEAWAY'
) {
  await page.goto('/listings/new')
  await page.fill('input[name="title"]', title)
  await page.fill('textarea[name="description"]', `Description for ${title}`)
  await page.selectOption('select[name="categoryCode"]', category)
  await page.selectOption('select[name="listingType"]', listingType)
  if (listingType === 'SELL') {
    await page.fill('input[name="price"]', price)
  }
  const testImage = e2eFixture(import.meta.url, 'fixtures/test-image.jpg')
  await page.setInputFiles('input[name="image"]', testImage)
  await page.click('button[type="submit"]')
  await page.waitForURL('/listings/mine')
}

test.describe('Browse and Search Flow', () => {
  const ts = Date.now()
  const email = uniqueEmail(`browse-${ts}`)
  const nickname = `BrowseUser${ts}`

  test.beforeEach(() => {
    resetRateLimits()
  })

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage()
    await register(page, email, nickname)

    // Create diverse listings for testing filters
    await createListing(page, `Textbook-${ts}`, 'BOOKS', '25.00', 'SELL')
    await createListing(page, `Laptop-${ts}`, 'ELECTRONICS', '500.00', 'SELL')
    await createListing(page, `FreeChair-${ts}`, 'FURNITURE', '0', 'GIVEAWAY')
    await createListing(page, `Bike-${ts}`, 'SPORTS', '150.00', 'SELL')

    await page.close()
  })

  test('keyword search filters listings', async ({ page }) => {
    await page.goto('/browse')
    await page.waitForLoadState('networkidle')

    // Search for "Textbook"
    await page.fill('input[placeholder*="Search"]', `Textbook-${ts}`)
    await page.keyboard.press('Enter')
    await page.waitForTimeout(1000)

    // Should see the textbook listing
    await expect(page.locator(`text=Textbook-${ts}`)).toBeVisible()

    // Should NOT see other listings
    await expect(page.locator(`text=Laptop-${ts}`)).not.toBeVisible()
  })

  test('category filter works', async ({ page }) => {
    await page.goto('/browse')
    await page.waitForLoadState('networkidle')

    // Filter by ELECTRONICS
    await page.selectOption('select', { label: 'Electronics' })
    await page.waitForTimeout(1000)

    // Should see laptop
    await expect(page.locator(`text=Laptop-${ts}`)).toBeVisible()

    // Should NOT see textbook
    await expect(page.locator(`text=Textbook-${ts}`)).not.toBeVisible()
  })

  test('listing type filter works', async ({ page }) => {
    await page.goto('/browse')
    await page.waitForLoadState('networkidle')

    // Filter by GIVEAWAY
    const typeSelect = page.locator('select').nth(1) // second select is type
    await typeSelect.selectOption('GIVEAWAY')
    await page.waitForTimeout(1000)

    // Should see free chair
    await expect(page.locator(`text=FreeChair-${ts}`)).toBeVisible()

    // Should NOT see paid items
    await expect(page.locator(`text=Laptop-${ts}`)).not.toBeVisible()
  })

  test('sort by price works', async ({ page }) => {
    await page.goto('/browse')
    await page.waitForLoadState('networkidle')

    // Sort by price low to high
    const sortSelect = page.locator('select').nth(2) // third select is sort
    await sortSelect.selectOption('PRICE_ASC')
    await page.waitForTimeout(1000)

    // Get all listing titles in order
    const titles = await page.locator('.grid a h3').allTextContents()

    // FreeChair (0) should come before Textbook (25) and Laptop (500)
    const freeIdx = titles.findIndex(t => t.includes('FreeChair'))
    const laptopIdx = titles.findIndex(t => t.includes('Laptop'))

    if (freeIdx >= 0 && laptopIdx >= 0) {
      expect(freeIdx).toBeLessThan(laptopIdx)
    }
  })

  test('no results shows empty state', async ({ page }) => {
    await page.goto('/browse')
    await page.waitForLoadState('networkidle')

    // Search for something that doesn't exist
    await page.fill('input[placeholder*="Search"]', 'NonExistentItem999999')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(1000)

    // Should see empty state message
    await expect(page.locator('text=No listings found')).toBeVisible()
    await expect(page.locator('text=Try adjusting your search or filters')).toBeVisible()
  })

  test('pagination works when many listings exist', async ({ browser }) => {
    // Create 15 listings to trigger pagination (assuming page size is 12)
    const page = await browser.newPage()
    await register(page, uniqueEmail(`paginate-${Date.now()}`), `PaginateUser${Date.now()}`)

    for (let i = 1; i <= 15; i++) {
      await createListing(page, `PaginateItem${i}`, 'BOOKS', '10.00', 'SELL')
    }

    await page.goto('/browse')
    await page.fill('input[placeholder*="Search"]', 'PaginateItem')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(1500)

    // Should see pagination controls
    const nextButton = page.locator('button:has-text("Next")')
    await expect(nextButton).toBeVisible()
    await expect(nextButton).toBeEnabled()

    // Click next
    await nextButton.click()
    await page.waitForTimeout(1000)

    // Should be on page 2
    await expect(page.locator('text=Page 2 of')).toBeVisible()

    // Previous button should now be enabled
    const prevButton = page.locator('button:has-text("Previous")')
    await expect(prevButton).toBeEnabled()

    await page.close()
  })
})
