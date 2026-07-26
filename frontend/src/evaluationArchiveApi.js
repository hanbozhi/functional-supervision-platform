import { API_BASE, request } from './api'

const base = '/basic-info/evaluation-archives'

export const fetchEvaluationArchives = (params) => request(base, params)
export const fetchEvaluationArchiveStats = () => request(`${base}/stats`)
export const fetchEvaluationArchive = (id) => request(`${base}/${id}`)
export const createEvaluationArchive = (body) =>
  request(base, {}, { method: 'POST', body })
export const updateEvaluationArchive = (id, body) =>
  request(`${base}/${id}`, {}, { method: 'PUT', body })
export const archiveEvaluationArchive = (id, rowVersion) =>
  request(`${base}/${id}/archive`, {}, { method: 'POST', body: { rowVersion } })
export const withdrawEvaluationArchive = (id, rowVersion, reason) =>
  request(`${base}/${id}/withdraw`, {}, { method: 'POST', body: { rowVersion, reason } })
export const fetchEvaluationAttachments = (id, history = false) =>
  request(`${base}/${id}/attachments`, { history })

export function uploadEvaluationAttachment(id, category, remarks, file) {
  const body = new FormData()
  body.append('category', category)
  if (remarks) body.append('remarks', remarks)
  body.append('file', file)
  return request(`${base}/${id}/attachments`, {}, { method: 'POST', body })
}

export function replaceEvaluationAttachment(id, attachmentId, remarks, file) {
  const body = new FormData()
  if (remarks) body.append('remarks', remarks)
  body.append('file', file)
  return request(`${base}/${id}/attachments/${attachmentId}/replace`, {}, {
    method: 'POST', body,
  })
}

export const deactivateEvaluationAttachment = (archiveId, attachmentId) =>
  request(`${base}/${archiveId}/attachments/${attachmentId}/status`, {}, {
    method: 'PUT', body: { status: 'INACTIVE' },
  })

export function attachmentUrl(id, action) {
  return `${API_BASE}/basic-info/evaluation-archive-attachments/${id}/${action}`
}

export async function downloadEvaluationAttachment(id, fileName) {
  const response = await fetch(attachmentUrl(id, 'download'))
  if (!response.ok) throw new Error('附件下载失败')
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || '附件'
  link.click()
  URL.revokeObjectURL(url)
}
