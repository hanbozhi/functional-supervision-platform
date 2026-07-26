import { request } from './api'

export const fetchIndicatorSystems = (params = {}) =>
  request('/basic-info/indicator-systems', params)
export const fetchIndicatorSystem = (id) =>
  request(`/basic-info/indicator-systems/${id}`)
export const createIndicatorSystem = (body) =>
  request('/basic-info/indicator-systems', {}, { method: 'POST', body })

export const fetchIndicatorVersions = (params = {}) =>
  request('/basic-info/indicator-versions', params)
export const fetchIndicatorVersion = (id) =>
  request(`/basic-info/indicator-versions/${id}`)
export const fetchIndicatorTree = (id) =>
  request(`/basic-info/indicator-versions/${id}/tree`)
export const publishIndicatorVersion = (id, rowVersion) =>
  request(`/basic-info/indicator-versions/${id}/publish`, {}, {
    method: 'POST', body: { rowVersion },
  })
export const archiveIndicatorVersion = (id, rowVersion) =>
  request(`/basic-info/indicator-versions/${id}/archive`, {}, {
    method: 'POST', body: { rowVersion },
  })
export const copyIndicatorVersion = (id, body) =>
  request(`/basic-info/indicator-versions/${id}/copy`, {}, { method: 'POST', body })

export const createIndicatorItem = (body) =>
  request('/basic-info/indicator-items', {}, { method: 'POST', body })
export const updateIndicatorItem = (id, body) =>
  request(`/basic-info/indicator-items/${id}`, {}, { method: 'PUT', body })
export const updateIndicatorItemStatus = (id, body) =>
  request(`/basic-info/indicator-items/${id}/status`, {}, { method: 'PUT', body })

export const fetchIndicatorRules = (params = {}) =>
  request('/basic-info/indicator-rules', params)
export const createIndicatorRule = (body) =>
  request('/basic-info/indicator-rules', {}, { method: 'POST', body })
export const updateIndicatorRule = (id, body) =>
  request(`/basic-info/indicator-rules/${id}`, {}, { method: 'PUT', body })
export const updateIndicatorRuleStatus = (id, body) =>
  request(`/basic-info/indicator-rules/${id}/status`, {}, { method: 'PUT', body })

export const fetchIndicatorTemplates = (params = {}) =>
  request('/basic-info/indicator-templates', params)
export const fetchIndicatorTemplate = (id) =>
  request(`/basic-info/indicator-templates/${id}`)
export const createIndicatorTemplate = (body) =>
  request('/basic-info/indicator-templates', {}, { method: 'POST', body })
export const copyIndicatorTemplate = (id, body) =>
  request(`/basic-info/indicator-templates/${id}/copy`, {}, { method: 'POST', body })
export const updateIndicatorTemplateStatus = (id, body) =>
  request(`/basic-info/indicator-templates/${id}/status`, {}, { method: 'PUT', body })
export const initializeFromIndicatorTemplate = (id, body) =>
  request(`/basic-info/indicator-templates/${id}/initialize`, {}, { method: 'POST', body })
