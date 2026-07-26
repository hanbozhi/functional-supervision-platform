const BASE = '/api/org-performance'

async function request(path, options = {}) {
  const response = await fetch(`${BASE}${path}`, options)
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || `请求失败（${response.status}）`)
  }
  return response.json()
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const performanceApi = {
  mappings: () => request('/mappings'),
  createMapping: (body) => request('/mappings', json('POST', body)),
  updateMapping: (id, body) => request(`/mappings/${id}`, json('PUT', body)),
  mappingStatus: (id, body) => request(`/mappings/${id}/status`, json('PUT', body)),
  batches: () => request('/imports'),
  batch: (id) => request(`/imports/${id}`),
  records: (year = '') => request(`/records${year ? `?year=${year}` : ''}`),
  uploadImport: (file) => {
    const data = new FormData()
    data.append('file', file)
    return request('/imports', { method: 'POST', body: data })
  },
  templateUrl: `${BASE}/import-template`,
  corrections: () => request('/corrections'),
  correction: (id) => request(`/corrections/${id}`),
  createCorrection: (body) => request('/corrections', json('POST', body)),
  updateCorrection: (id, body) => request(`/corrections/${id}`, json('PUT', body)),
  submitCorrection: (id, body) => request(`/corrections/${id}/submit`, json('POST', body)),
  reviewCorrection: (id, body) => request(`/corrections/${id}/review`, json('POST', body)),
  uploadMaterial: (id, file, remarks = '') => {
    const data = new FormData()
    data.append('file', file)
    data.append('remarks', remarks)
    return request(`/corrections/${id}/materials`, { method: 'POST', body: data })
  },
  materialUrl: (id) => `${BASE}/materials/${id}/download`,
}
