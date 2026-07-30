import { API_BASE } from './api'

const BASE=`${API_BASE}/internal-evaluations`
async function request(path,options={}){
  const response=await fetch(`${BASE}${path}`,options)
  if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.message||`请求失败（${response.status}）`)}
  return response.json()
}
const json=(method,body)=>({method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const internalApi={
  tasks:()=>request('/tasks'),task:(id)=>request(`/tasks/${id}`),
  versions:()=>request('/options/indicator-versions'),organizations:()=>request('/options/organizations'),users:()=>request('/options/users'),
  createTask:(body)=>request('/tasks',json('POST',body)),copyTask:(id,body)=>request(`/tasks/${id}/copy`,json('POST',body)),
  publish:(id)=>request(`/tasks/${id}/publish`,{method:'POST'}),taskStatus:(id,body)=>request(`/tasks/${id}/status`,json('PUT',body)),
  sheet:(id)=>request(`/score-sheets/${id}`),saveScores:(id,body)=>request(`/score-sheets/${id}/scores`,json('PUT',body)),
  submit:(id,body)=>request(`/score-sheets/${id}/submit`,json('POST',body)),review:(id,body)=>request(`/score-sheets/${id}/review`,json('POST',body)),
  materials:(id)=>request(`/score-entries/${id}/materials`),
  upload:(id,file,remarks='')=>{const data=new FormData();data.append('file',file);data.append('remarks',remarks);return request(`/score-entries/${id}/materials`,{method:'POST',body:data})},
  download:(id,preview=false)=>`${BASE}/materials/${id}/${preview?'preview':'download'}`,
}
