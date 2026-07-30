import { API_BASE } from './api'

const BASE=`${API_BASE}/public-evaluations`
async function request(path,options={}){const r=await fetch(`${BASE}${path}`,options);if(!r.ok){const b=await r.json().catch(()=>({}));throw new Error(b.message||`请求失败（${r.status}）`)}return r.json()}
const json=(method,body)=>({method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
export const publicEvaluationApi={
 orgs:()=>request('/options/organizations'),items:()=>request('/service-items'),
 saveItem:(id,body)=>request(`/service-items${id?`/${id}`:''}`,json(id?'PUT':'POST',body)),
 submit:(body,image)=>{const d=new FormData();d.append('data',new Blob([JSON.stringify(body)],{type:'application/json'}));if(image)d.append('image',image);return request('',{method:'POST',body:d})},
 list:(params={})=>{const q=new URLSearchParams(Object.entries(params).filter(([,v])=>v!==''&&v!=null));return request(`?${q}`)},
 detail:id=>request(`/${id}`),process:(id,body)=>request(`/${id}/process`,json('PUT',body)),sentiment:(id,body)=>request(`/${id}/sentiment`,json('PUT',body)),
 requests:()=>request('/privacy-requests'),requestAccess:(id,body)=>request(`/${id}/privacy-requests`,json('POST',body)),
 reviewRequest:(id,body)=>request(`/privacy-requests/${id}/review`,json('POST',body)),reveal:id=>request(`/privacy-requests/${id}/reveal`,{method:'POST'}),audits:()=>request('/privacy-audits'),
 imports:()=>request('/imports'),batch:id=>request(`/imports/${id}`),stats:()=>request('/stats'),
 importFile:(source,file)=>{const d=new FormData();d.append('source',source);d.append('file',file);return request('/imports',{method:'POST',body:d})},
 attachment:id=>`${BASE}/attachments/${id}/download`,
}
