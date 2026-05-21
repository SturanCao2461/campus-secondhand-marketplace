import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api, ApiError } from './apiClient'

const originalFetch = globalThis.fetch

function mockFetchResponse(opts: {
  status?: number
  ok?: boolean
  json?: () => Promise<unknown>
}) {
  return {
    status: opts.status ?? 200,
    ok: opts.ok ?? (opts.status ?? 200) < 400,
    json: opts.json ?? (() => Promise.resolve({})),
  } as unknown as Response
}

describe('apiClient', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn()
  })
  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  it('returns parsed body on 200', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockFetchResponse({
        status: 200,
        json: () => Promise.resolve({ hello: 'world' }),
      })
    )

    const result = await api.get<{ hello: string }>('/api/x')
    expect(result).toEqual({ hello: 'world' })
  })

  it('returns undefined on 204 without parsing body', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockFetchResponse({ status: 204, ok: true })
    )

    const result = await api.delete('/api/x')
    expect(result).toBeUndefined()
  })

  it('throws ApiError with code and message from JSON error body', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockFetchResponse({
        status: 400,
        ok: false,
        json: () => Promise.resolve({ code: 'VALIDATION_FAILED', message: 'Bad input' }),
      })
    )

    await expect(api.post('/api/x', { a: 1 })).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Bad input',
    })
  })

  it('throws ApiError instance', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockFetchResponse({
        status: 401,
        ok: false,
        json: () => Promise.resolve({ code: 'UNAUTHORIZED', message: 'Login required' }),
      })
    )

    try {
      await api.get('/api/x')
      expect.fail('expected ApiError to be thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      expect((err as ApiError).status).toBe(401)
    }
  })

  it('falls back to default code/message when error body is not JSON', async () => {
    ;(globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      mockFetchResponse({
        status: 500,
        ok: false,
        json: () => Promise.reject(new Error('not json')),
      })
    )

    await expect(api.get('/api/x')).rejects.toMatchObject({
      status: 500,
      code: 'UNKNOWN',
      message: 'HTTP 500',
    })
  })

  it('sends JSON body with Content-Type when provided', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValue(
      mockFetchResponse({ status: 200, json: () => Promise.resolve({}) })
    )

    await api.post('/api/x', { name: 'alice' })

    const call = fetchMock.mock.calls[0]
    const init = call[1] as RequestInit
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(init.body).toBe(JSON.stringify({ name: 'alice' }))
    expect(init.credentials).toBe('include')
  })

  it('omits Content-Type for GET without body', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValue(
      mockFetchResponse({ status: 200, json: () => Promise.resolve({}) })
    )

    await api.get('/api/x')

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toBeUndefined()
    expect(init.body).toBeUndefined()
  })

  it('postForm sends FormData without Content-Type', async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValue(
      mockFetchResponse({ status: 200, json: () => Promise.resolve({ id: 1 }) })
    )

    const form = new FormData()
    form.append('title', 'Book')
    const result = await api.postForm<{ id: number }>('/api/upload', form)

    expect(result).toEqual({ id: 1 })
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.method).toBe('POST')
    expect(init.body).toBe(form)
    expect(init.headers).toBeUndefined()
  })
})
