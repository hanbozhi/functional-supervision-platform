import { API_BASE, request } from './api'

const plans = '/basic-info/three-fixed-plans'
const versions = '/basic-info/three-fixed-plan-versions'
const mappings = '/basic-info/three-fixed-field-mappings'

export const fetchThreeFixedPlans = (params) => request(plans, params)
export const fetchThreeFixedPlan = (id) => request(`${plans}/${id}`)
export const fetchThreeFixedVersion = (id) => request(`${versions}/${id}`)
export const createManualPlan = (body) => request(`${plans}/manual`, {}, { method: 'POST', body })
export const updatePlanVersion = (id, body) => request(`${versions}/${id}`, {}, { method: 'PUT', body })
export const reparsePlanVersion = (id, rowVersion) => request(`${versions}/${id}/reparse`, {}, { method: 'POST', body: { rowVersion } })
export const submitPlanVersion = (id, rowVersion) => request(`${versions}/${id}/submit`, {}, { method: 'POST', body: { rowVersion } })
export const reviewPlanVersion = (id, body) => request(`${versions}/${id}/review`, {}, { method: 'POST', body })
export const uploadPlan = (form) => request(`${plans}/upload`, {}, { method: 'POST', body: form })
export const batchUploadPlans = (form) => request(`${plans}/batch-upload`, {}, { method: 'POST', body: form })
export const fetchFieldMappings = () => request(mappings)
export const createFieldMapping = (body) => request(mappings, {}, { method: 'POST', body })
export const updateFieldMapping = (id, body) => request(`${mappings}/${id}`, {}, { method: 'PUT', body })
export const updateFieldMappingStatus = (id, status) => request(`${mappings}/${id}/status`, {}, { method: 'PUT', body: { status } })

export async function downloadThreeFixedAttachment(id, fileName) {
  const response = await fetch(`${API_BASE}/basic-info/three-fixed-attachments/${id}/download`)
  if (!response.ok) throw new Error('附件下载失败')
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName || '三定方案附件'
  link.click()
  URL.revokeObjectURL(link.href)
}
