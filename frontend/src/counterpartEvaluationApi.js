const BASE = '/api/counterpart-evaluation'

async function request(path, options = {}) {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || `请求失败（${response.status}）`)
  }
  return response.status === 204 ? null : response.json()
}

export const counterpartApi = {
  organizations: () => request('/organizations'),
  relations: (status = '') => request(`/relations${status ? `?status=${status}` : ''}`),
  createRelation: (body) => request('/relations', { method: 'POST', body: JSON.stringify(body) }),
  updateRelation: (id, body) => request(`/relations/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  verifyRelation: (id, body) => request(`/relations/${id}/verify`, { method: 'PUT', body: JSON.stringify(body) }),
  relationStatus: (id, body) => request(`/relations/${id}/status`, { method: 'PUT', body: JSON.stringify(body) }),
  generateSuggestions: () => request('/relation-suggestions/generate', { method: 'POST' }),
  questionnaires: (status = '') => request(`/questionnaires${status ? `?status=${status}` : ''}`),
  questionnaire: (id) => request(`/questionnaires/${id}`),
  createQuestionnaire: (body) => request('/questionnaires', { method: 'POST', body: JSON.stringify(body) }),
  copyQuestionnaire: (id, body) => request(`/questionnaires/${id}/copy`, { method: 'POST', body: JSON.stringify(body) }),
  addRecipients: (id, relationIds) => request(`/questionnaires/${id}/recipients`, { method: 'POST', body: JSON.stringify({ relationIds }) }),
  recipients: (id) => request(`/questionnaires/${id}/recipients`),
  publish: (id) => request(`/questionnaires/${id}/publish`, { method: 'POST' }),
  deadline: (id) => request(`/questionnaires/${id}/deadline`, { method: 'POST' }),
  close: (id) => request(`/questionnaires/${id}/close`, { method: 'POST' }),
  push: (id) => request(`/questionnaires/${id}/simulate-push`, { method: 'POST' }),
  pushLogs: (id) => request(`/questionnaires/${id}/push-logs`),
  statistics: (id) => request(`/questionnaires/${id}/statistics`),
  fill: (token) => request(`/fill/${token}`),
  submit: (token, body) => request(`/fill/${token}/submit`, { method: 'POST', body: JSON.stringify(body) }),
  restore: (recipientId) => request(`/anonymous-mappings/${recipientId}/restore`, { method: 'POST' }),
  detect: (id) => request(`/questionnaires/${id}/anomaly-runs`, { method: 'POST' }),
  runs: (questionnaireId = '') => request(`/anomaly-runs${questionnaireId ? `?questionnaireId=${questionnaireId}` : ''}`),
  anomalies: (runId, status = '') => request(`/anomaly-runs/${runId}/cases${status ? `?status=${status}` : ''}`),
  anomaly: (id) => request(`/anomaly-cases/${id}`),
  assign: (id, body) => request(`/anomaly-cases/${id}/assign`, { method: 'PUT', body: JSON.stringify(body) }),
  review: (id, body) => request(`/anomaly-cases/${id}/review`, { method: 'PUT', body: JSON.stringify(body) }),
}
