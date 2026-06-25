import axios from 'axios'
import type { TeamMember, Customer, Assignment, TeamMemberUsage, LogoSearchResult } from '@/types'

const api = axios.create({ baseURL: '/api' })

export const teamMemberApi = {
  list: () => api.get<TeamMember[]>('/team-members').then(r => r.data),
  get: (id: number) => api.get<TeamMember>(`/team-members/${id}`).then(r => r.data),
  create: (data: Omit<TeamMember, 'id'>) => api.post<TeamMember>('/team-members', data).then(r => r.data),
  update: (id: number, data: Omit<TeamMember, 'id'>) => api.put<TeamMember>(`/team-members/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/team-members/${id}`),
  uploadPhoto: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/team-members/${id}/photo`, form)
  },
  deletePhoto: (id: number) => api.delete(`/team-members/${id}/photo`),
  photoUrl: (id: number) => `/api/team-members/${id}/photo`,
}

export const customerApi = {
  list: () => api.get<Customer[]>('/customers').then(r => r.data),
  get: (id: number) => api.get<Customer>(`/customers/${id}`).then(r => r.data),
  create: (data: Omit<Customer, 'id'>) => api.post<Customer>('/customers', data).then(r => r.data),
  update: (id: number, data: Omit<Customer, 'id'>) => api.put<Customer>(`/customers/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/customers/${id}`),
  uploadLogo: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/customers/${id}/logo`, form)
  },
  deleteLogo: (id: number) => api.delete(`/customers/${id}/logo`),
  logoUrl: (id: number) => `/api/customers/${id}/logo`,
  setLogoFromUrl: (id: number, url: string) => api.post(`/customers/${id}/logo-from-url`, { url }),
}

export const assignmentApi = {
  list: (params?: { teamMemberId?: number; customerId?: number }) =>
    api.get<Assignment[]>('/assignments', { params }).then(r => r.data),
  create: (data: Omit<Assignment, 'id'>) => api.post<Assignment>('/assignments', data).then(r => r.data),
  update: (id: number, data: Partial<Assignment>) => api.patch<Assignment>(`/assignments/${id}`, data).then(r => r.data),
  delete: (id: number) => api.delete(`/assignments/${id}`),
}

export const usageApi = {
  get: (from: string, to: string, country?: string, teamMemberId?: number) =>
    api.get<TeamMemberUsage[]>('/usage', { params: { from, to, country, teamMemberId } }).then(r => r.data),
  exportExcel: (from: string, to: string, country?: string) =>
    api.get('/usage/export', { params: { from, to, country }, responseType: 'blob' }).then(r => {
      const url = window.URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url
      a.download = r.headers['content-disposition']?.match(/filename="(.+)"/)?.[1]
          ?? `usage-${from}-to-${to}.xlsx`
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    }),
}

export const logoSearchApi = {
  search: (q: string) => api.get<LogoSearchResult[]>('/logo-search', { params: { q } }).then(r => r.data),
}
