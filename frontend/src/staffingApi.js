import { API_BASE, request } from './api'

const base = '/basic-info/staffing-ledgers'

export const fetchStaffingLedgers = (params) => request(base, params)
export const fetchStaffingStats = (params) => request(`${base}/stats`, params)
export const fetchStaffingDetail = (id) => request(`${base}/${id}`)
export const createStaffingLedger = (body) => request(base, {}, { method: 'POST', body })
export const updateStaffingLedger = (id, body) =>
  request(`${base}/${id}`, {}, { method: 'PUT', body })
export const batchUpdateStaffing = (body) =>
  request(`${base}/batch`, {}, { method: 'PUT', body })
export const fetchStaffingChanges = (id, params) =>
  request(`${base}/${id}/changes`, params)
export const fetchStaffingImport = (id) => request(`${base}/imports/${id}`)

export async function importStaffing(file) {
  const body = new FormData()
  body.append('file', file)
  return request(`${base}/imports`, {}, { method: 'POST', body })
}

export async function downloadStaffingTemplate() {
  const response = await fetch(`${API_BASE}${base}/import-template`)
  if (!response.ok) throw new Error(await response.text() || '模板下载失败')
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '编制人员台账导入模板.xlsx'
  link.click()
  URL.revokeObjectURL(link.href)
}
