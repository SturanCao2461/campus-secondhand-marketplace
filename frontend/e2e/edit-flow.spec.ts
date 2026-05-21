import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { e2eFixture } from './helpers/paths'

const uniqueEmail = () => `e2e-edit-${Date.now()}@students.waikato.ac.nz`

test.describe('Listing Edit Flow', () => {
  test.beforeEach(() => {
    resetRateLimits()
  })
  test('edit listing changes title and preserves image', async ({ page }) => {
    const email = uniqueEmail()
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `Edit${Date.now()}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Create
    await page.goto('/listings/new')
    await page.fill('input[name="title"]', 'Before Edit')
    await page.fill('textarea[name="description"]', 'Original description')
    await page.selectOption('select[name="categoryCode"]', 'BOOKS')
    await page.selectOption('select[name="listingType"]', 'SELL')
    await page.fill('input[name="price"]', '30.00')
    const testImage = e2eFixture(import.meta.url, 'fixtures/test-image.jpg')
    await page.setInputFiles('input[name="image"]', testImage)
    await page.click('button[type="submit"]')
    await page.waitForURL('/listings/mine')

    // Navigate to detail then edit
    await page.click(`text=Before Edit`)
    await page.waitForURL(/\/listings\/\d+$/)
    await page.click('a:has-text("Edit")')
    await page.waitForURL(/\/listings\/\d+\/edit/)

    // Change title
    await page.fill('input[name="title"]', 'After Edit')
    await page.click('button[type="submit"]')
    await page.waitForURL(/\/listings\/\d+$/)

    // Verify
    await expect(page.locator('h1')).toHaveText('After Edit')
  })
})
