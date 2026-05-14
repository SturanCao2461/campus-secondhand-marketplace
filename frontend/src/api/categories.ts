import { api } from './apiClient'

export interface Category {
  code: string
  nameEn: string
  nameZh: string
}

export const categoriesApi = {
  list: () => api.get<{ items: Category[] }>('/api/categories'),
}
