export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type Json = Record<string, unknown> | unknown[] | string | number | boolean | null

async function request<T>(method: string, path: string, body?: Json): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'include',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 204) return undefined as T

  let data: { code?: string; message?: string } | null = null
  try {
    data = await res.json()
  } catch {
    // empty body — fall through
  }

  if (!res.ok) {
    const code = data?.code ?? 'UNKNOWN'
    const message = data?.message ?? `HTTP ${res.status}`
    throw new ApiError(res.status, code, message)
  }

  return data as T
}

async function requestForm<T>(method: string, path: string, form: FormData): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'include',
    body: form,
  })

  if (res.status === 204) return undefined as T

  let data: { code?: string; message?: string } | null = null
  try {
    data = await res.json()
  } catch {
    // empty body — fall through
  }

  if (!res.ok) {
    const code = data?.code ?? 'UNKNOWN'
    const message = data?.message ?? `HTTP ${res.status}`
    throw new ApiError(res.status, code, message)
  }

  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Json) => request<T>('POST', path, body),
  put: <T>(path: string, body?: Json) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: Json) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
  postForm: <T>(path: string, form: FormData) => requestForm<T>('POST', path, form),
  putForm: <T>(path: string, form: FormData) => requestForm<T>('PUT', path, form),
}
