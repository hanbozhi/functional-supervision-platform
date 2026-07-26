import { request } from './api'

const base = '/basic-info/core-functions'

export const fetchCoreFunctions = (params) => request(base, params)
export const fetchCoreFunctionStats = (orgId) => request(`${base}/stats`, { orgId })
export const fetchCoreFunction = (id) => request(`${base}/${id}`)
export const createCoreFunction = (body) => request(base, {}, { method: 'POST', body })
export const updateCoreFunction = (id, body) =>
  request(`${base}/${id}`, {}, { method: 'PUT', body })
export const updateCoreFunctionStatus = (id, body) =>
  request(`${base}/${id}/status`, {}, { method: 'PUT', body })

export const fetchDuties = (params) => request(`${base}/duties`, params)
export const createDuty = (body) => request(`${base}/duties`, {}, { method: 'POST', body })
export const updateDuty = (id, body) =>
  request(`${base}/duties/${id}`, {}, { method: 'PUT', body })
export const updateDutyStatus = (id, body) =>
  request(`${base}/duties/${id}/status`, {}, { method: 'PUT', body })

export const fetchDutyPreview = (orgId) =>
  request(`${base}/org-units/${orgId}/duty-import-preview`)
export const importDutyCandidates = (orgId, body) =>
  request(`${base}/org-units/${orgId}/duty-imports`, {}, { method: 'POST', body })
export const fetchRightsMappings = (orgId) =>
  request(`${base}/org-units/${orgId}/rights-mappings`)
export const saveRightsMappings = (orgId, body) =>
  request(`${base}/org-units/${orgId}/rights-mappings`, {}, { method: 'PUT', body })
export const applyAutoMappings = (orgId) =>
  request(`${base}/org-units/${orgId}/rights-mappings/auto`, {}, { method: 'POST' })
export const fetchOrgRightsItems = (orgId) =>
  request(`${base}/org-units/${orgId}/rights-items`)

export const fetchMatchRuns = (orgId) =>
  request(`${base}/org-units/${orgId}/match-runs`)
export const startMatchRun = (orgId, body) =>
  request(`${base}/org-units/${orgId}/match-runs`, {}, { method: 'POST', body })
export const fetchMatchResults = (runId, params) =>
  request(`${base}/match-runs/${runId}/results`, params)
export const reviewMatchResult = (id, body) =>
  request(`${base}/match-results/${id}/review`, {}, { method: 'PUT', body })
export const createManualMatchResult = (runId, body) =>
  request(`${base}/match-runs/${runId}/manual-results`, {}, { method: 'POST', body })
