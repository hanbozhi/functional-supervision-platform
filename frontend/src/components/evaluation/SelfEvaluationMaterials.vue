<script setup>
import{onMounted,ref}from'vue';import{selfEvaluationApi as api}from'../../selfEvaluationApi'
const tasks=ref([]),task=ref(null),form=ref(null),entry=ref(null),materials=ref([]),file=ref(null),message=ref(''),msgType=ref('info'),loading=ref(false)
function msg(text,type='info'){message.value=text;msgType.value=type;if(type!=='error')setTimeout(()=>{if(message.value===text)message.value=''},4000)}
async function load(){try{tasks.value=await api.tasks()}catch(e){msg(e.message||'加载任务列表失败','error')}}
async function selectTask(id){if(!id)return;try{loading.value=true;task.value=await api.task(id);form.value=null;entry.value=null;msg('')}catch(e){msg(e.message,'error')}finally{loading.value=false}}
async function selectForm(id){if(!id)return;try{loading.value=true;form.value=await api.form(id);entry.value=null;msg('')}catch(e){msg(e.message,'error')}finally{loading.value=false}}
async function selectEntry(x){entry.value=x;try{materials.value=await api.materials(x.id)}catch(e){msg(e.message,'error')}}
async function uploadExisting(replace){if(!file.value||!entry.value)return;const name=file.value.name;if(name.length>200){msg('文件名过长','error');return}try{await api.upload(entry.value.id,{materialName:name,category:null,description:'自评佐证材料',versionGroup:replace?replace.version_group:null,confirmClassification:false},file.value);materials.value=await api.materials(entry.value.id);msg(replace?'材料新版本已上传，历史版本保留':'材料已上传，请确认分类','success');file.value=null}catch(e){msg(e.message||'上传失败','error')}}
async function classify(x){const category=prompt('材料分类（如 REPORT/EVIDENCE/CERTIFICATE/OTHER）',x.category||'OTHER');if(!category)return;try{await api.classify(x.id,category);materials.value=await api.materials(entry.value.id);msg('分类已确认','success')}catch(e){msg(e.message,'error')}}
async function disable(x){if(!confirm('确认停用此材料？（不删除历史文件）'))return;try{await api.disable(x.id);materials.value=await api.materials(entry.value.id);msg('材料已停用','success')}catch(e){msg(e.message,'error')}}
function canEdit(){return form.value&&['NOT_STARTED','DRAFT','RETURNED'].includes(form.value.status)}
onMounted(load)
</script>
<template>
<section class="page active">
  <div class="alert alert-info">按任务、机构和指标管理佐证材料；文件名和指标编码只提供简单分类建议，必须人工确认，不使用OCR。</div>
  <div v-if="message" class="alert" :class="msgType==='error'?'alert-danger':'alert-success'">{{message}}</div>

  <div class="card">
    <div class="card-header">
      <h3>📁 材料管理</h3>
      <button class="btn btn-outline" @click="load">刷新</button>
    </div>
    <div class="search-bar">
      <div class="form-item">
        <label>自评任务</label>
        <select @change="selectTask($event.target.value)">
          <option value="">— 选择自评任务 —</option>
          <option v-for="x in tasks" :key="x.id" :value="x.id">{{x.task_name}}（{{x.status}}）</option>
        </select>
      </div>
      <div v-if="task" class="form-item">
        <label>参评机构</label>
        <select @change="selectForm($event.target.value)">
          <option value="">— 选择机构 —</option>
          <option v-for="x in task.organizations" :key="x.id" :value="x.id">{{x.unit_name}} / {{x.status}}（{{x.completed_items}}/{{x.total_items}}）</option>
        </select>
      </div>
    </div>
  </div>

  <div v-if="!tasks.length&&!loading" class="card">
    <div class="empty-cell">暂无自评任务。请先在"自评任务管理"(m2-12)中创建并发布自评任务。</div>
  </div>

  <div v-if="task&&!form" class="card">
    <div class="card-header"><h3>{{task.task_name}}</h3></div>
    <table>
      <thead><tr><th>机构</th><th>完成指标</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="x in task.organizations" :key="x.id">
          <td>{{x.unit_name}}</td>
          <td>{{x.completed_items}}/{{x.total_items}}</td>
          <td><span class="tag" :class="x.status==='COMPLETED'?'tag-success':x.status==='SUBMITTED'?'tag-info':x.status==='RETURNED'?'tag-warning':'tag-default'">{{x.status}}</span></td>
          <td><button class="btn btn-sm btn-outline" @click="selectForm(x.id)">查看指标</button></td>
        </tr>
      </tbody>
    </table>
  </div>

  <div v-if="form" class="card">
    <div class="card-header">
      <h3>{{form.unit_name}} · 指标材料柜</h3>
      <span class="extra">{{form.status}} / {{form.completed_items}}/{{form.total_items}}项</span>
    </div>
    <div style="margin-bottom:10px;display:flex;flex-wrap:wrap;gap:4px">
      指标：
      <button v-for="x in form.entries" :key="x.id" class="folder-pill" :class="{active:entry&&entry.id===x.id}" @click="selectEntry(x)">
        {{x.indicator_code}}<small>×{{x.material_count}}</small>
      </button>
    </div>

    <div v-if="entry">
      <div v-if="canEdit()" style="margin-bottom:12px">
        <label class="btn btn-sm btn-outline" style="cursor:pointer">
          选择文件
          <input type="file" hidden @change="file=$event.target.files[0]">
        </label>
        <button class="btn btn-sm btn-primary" :disabled="!file" @click="uploadExisting()">上传材料</button>
        <span v-if="file" class="tag tag-info">{{file.name}}（{{file.size?Math.round(file.size/1024):0}}KB）</span>
      </div>
      <div v-else class="alert alert-warning" style="margin-bottom:8px">
        当前自评状态为"{{form.status}}"，只读，退回修改后才可上传材料。
      </div>
      <table>
        <thead><tr><th>材料名称</th><th>分类</th><th>版本</th><th>当前</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!materials.length">
            <td colspan="6" class="empty-cell">暂无材料 — 该指标尚未上传佐证材料</td>
          </tr>
          <tr v-for="x in materials" :key="x.id">
            <td>
              <strong>{{x.material_name||x.original_name}}</strong>
              <div class="sub" style="font-size:11px;color:#888">{{x.description||''}}</div>
            </td>
            <td>
              <span class="tag" :class="x.classification_status==='SUGGESTED'?'tag-warning':'tag-info'">{{x.category||'OTHER'}}</span>
            </td>
            <td>V{{x.version_no}}</td>
            <td><span class="tag" :class="x.is_current?'tag-success':'tag-default'">{{x.is_current?'当前':'历史'}}</span></td>
            <td>{{x.status}}</td>
            <td>
              <a v-if="x.is_current&&x.status==='ACTIVE'" class="btn btn-sm btn-outline" :href="api.download(x.attachment_id)">下载</a>
              <button v-if="x.classification_status==='SUGGESTED'" class="btn btn-sm btn-primary" @click="classify(x)">确认</button>
              <button v-if="x.is_current&&x.status==='ACTIVE'&&canEdit()" class="btn btn-sm btn-outline" @click="uploadExisting(x)">替换</button>
              <button v-if="x.is_current&&x.status==='ACTIVE'&&canEdit()" class="btn btn-sm btn-outline" @click="disable(x)">停用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!entry" class="empty-cell" style="padding:32px">请点击上方指标按钮查看和管理对应材料</div>
  </div>
</section>
</template>
<style scoped>
.folder-pill{display:inline-block;border:1px solid #e5e7eb;background:#f5f7fa;padding:6px 12px;cursor:pointer;border-radius:20px;font-size:13px;margin:2px;transition:all .15s}
.folder-pill:hover{background:#e6f0ff}
.folder-pill.active{background:#dbeafe;border-color:#93b4f5;color:#1d4ed8;font-weight:600}
.folder-pill small{color:#888;margin-left:2px}
.empty-cell{text-align:center;color:#999;padding:24px!important}
@media(max-width:700px){.folder-pill{font-size:12px;padding:4px 8px}}
</style>
