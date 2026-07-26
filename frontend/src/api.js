const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

async function request(path, params = {}) {
  const url = new URL(`${API_BASE}${path}`)
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, value)
    }
  })

  const response = await fetch(url)
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `请求失败：${response.status}`)
  }
  return response.json()
}

export function fetchRightsItems(params) {
  return request('/basic-info/rights-items', params)
}

export function fetchRightsDetail(id) {
  return request(`/basic-info/rights-items/${id}`)
}

export function fetchRightsOptions() {
  return request('/basic-info/rights-items/options')
}

export function fetchRightsStats() {
  return request('/basic-info/rights-items/stats')
}
