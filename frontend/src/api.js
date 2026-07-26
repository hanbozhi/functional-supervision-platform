const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

export async function request(path, params = {}, options = {}) {
  const url = new URL(`${API_BASE}${path}`)
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, value)
    }
  })

  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: options.body ? { 'Content-Type': 'application/json' } : undefined,
    body: options.body ? JSON.stringify(options.body) : undefined,
  })
  if (!response.ok) {
    const text = await response.text()
    try {
      const error = JSON.parse(text)
      throw new Error(error.message || `请求失败：${response.status}`)
    } catch (error) {
      if (error instanceof SyntaxError) throw new Error(text || `请求失败：${response.status}`)
      throw error
    }
  }
  return response.status === 204 ? null : response.json()
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
