import { request } from './api'

const base = '/basic-info/org-units'

export const fetchOrgTree = (params) => request(`${base}/tree`, params)
export const fetchOrgUnits = (params) => request(base, params)
export const fetchOrgStats = (params) => request(`${base}/stats`, params)
export const fetchOrgOptions = () => request(`${base}/options`)
export const fetchOrgDetail = (id) => request(`${base}/${id}`)
export const createOrgUnit = (body) => request(base, {}, { method: 'POST', body })
export const updateOrgUnit = (id, body) => request(`${base}/${id}`, {}, { method: 'PUT', body })
export const updateOrgStatus = (id, body) => request(`${base}/${id}/status`, {}, { method: 'PUT', body })
export const verifyOrgUnit = (id, body) => request(`${base}/${id}/verifications`, {}, { method: 'POST', body })
