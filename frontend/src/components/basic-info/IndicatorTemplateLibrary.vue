<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  copyIndicatorTemplate,
  createIndicatorTemplate,
  fetchIndicatorTemplate,
  fetchIndicatorTemplates,
  fetchIndicatorVersions,
  initializeFromIndicatorTemplate,
  updateIndicatorTemplateStatus,
} from '../../indicatorApi'

const templates = ref([])
const versions = ref([])
const preview = ref(null)
const error = ref('')
const message = ref('')
const modal = ref('')
const filters = reactive({ keyword: '', orgType: '', status: '' })
const form = reactive({
  sourceVersionId: '', templateCode: '', templateName: '',
  applicableOrgType: 'GENERAL', description: '',
})
const initForm = reactive({
  templateId: null, systemCode: '', systemName: '',
  evaluationYear: new Date().getFullYear(), applicableOrgType: 'GENERAL', description: '',
})

const stats = computed(() => ({
  total: templates.value.length,
  active: templates.value.filter(item => item.status === 'ACTIVE').length,
  indicators: templates.value.reduce((sum, item) => sum + Number(item.indicator_count || 0), 0),
}))
const previewItems = computed(() => {
  const items = preview.value?.snapshot?.items || []
  const byId = new Map(items.map(item => [Number(item.id), item]))
  const depth = (item) => {
    let value = 0
    let parent = item.parent_id == null ? null : byId.get(Number(item.parent_id))
    while (parent) { value += 1; parent = parent.parent_id == null ? null : byId.get(Number(parent.parent_id)) }
    return value
  }
  return [...items].sort((a, b) => Number(a.indicator_level) - Number(b.indicator_level) || Number(a.sort_order) - Number(b.sort_order)).map(item => ({ ...item, depth: depth(item) }))
})

async function load() {
  error.value = ''
  try {
    [templates.value, versions.value] = await Promise.all([
      fetchIndicatorTemplates(filters), fetchIndicatorVersions(),
    ])
  } catch (e) { error.value = e.message }
}

function resetFilters() {
  Object.assign(filters, { keyword: '', orgType: '', status: '' })
  load()
}

function openCreate() {
  Object.assign(form, {
    sourceVersionId: versions.value.find(v => v.status === 'PUBLISHED')?.id || '',
    templateCode: '', templateName: '', applicableOrgType: 'GENERAL', description: '',
  })
  modal.value = 'create'
}

async function saveTemplate() {
  try {
    await createIndicatorTemplate({ ...form, sourceVersionId: Number(form.sourceVersionId) })
    message.value = '完整指标体系已保存为独立模板快照'
    modal.value = ''
    await load()
  } catch (e) { error.value = e.message }
}

async function openPreview(item) {
  try {
    preview.value = await fetchIndicatorTemplate(item.id)
    modal.value = 'preview'
  } catch (e) { error.value = e.message }
}

async function copyTemplate(item) {
  const code = window.prompt('新模板编码', `${item.template_code}-COPY`)
  if (!code) return
  const name = window.prompt('新模板名称', `${item.template_name} 副本`)
  if (!name) return
  try {
    await copyIndicatorTemplate(item.id, { templateCode: code, templateName: name })
    message.value = '模板快照已复制'
    await load()
  } catch (e) { error.value = e.message }
}

async function toggleTemplate(item) {
  try {
    await updateIndicatorTemplateStatus(item.id, {
      status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      rowVersion: item.row_version,
    })
    message.value = '模板状态已更新'
    await load()
  } catch (e) { error.value = e.message }
}

function openInitialize(item) {
  Object.assign(initForm, {
    templateId: item.id, systemCode: '', systemName: '',
    evaluationYear: new Date().getFullYear(),
    applicableOrgType: item.applicable_org_type, description: '',
  })
  modal.value = 'initialize'
}

async function initialize() {
  try {
    const result = await initializeFromIndicatorTemplate(initForm.templateId, initForm)
    message.value = `已从模板初始化指标体系，草稿版本ID：${result.id}`
    modal.value = ''
    await load()
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <section class="page active template-page">
    <div class="alert alert-info">模板保存完整指标树和评分规则的独立快照；原指标体系后续变化不会影响已有模板。</div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ stats.total }}</div><div class="label">模板总数</div></div>
      <div class="stat-card"><div class="num green">{{ stats.active }}</div><div class="label">启用模板</div></div>
      <div class="stat-card"><div class="num">{{ stats.indicators }}</div><div class="label">快照指标合计</div></div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">📚</span>指标模板库管理</h3><button class="btn btn-primary" :disabled="!versions.length" @click="openCreate">＋ 从指标版本创建模板</button></div>
      <div class="search-bar">
        <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="模板编码或名称" @keyup.enter="load"></div>
        <div class="form-item"><label>适用机构类型</label><select v-model="filters.orgType"><option value="">全部</option><option value="ADMINISTRATIVE">行政机关</option><option value="PARTY">党委单位</option><option value="PUBLIC_INSTITUTION">事业单位</option><option value="GENERAL">通用</option></select></div>
        <div class="form-item"><label>状态</label><select v-model="filters.status"><option value="">全部</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></div>
        <button class="btn btn-primary" @click="load">查询</button><button class="btn btn-outline" @click="resetFilters">重置</button>
      </div>
    </div>

    <div class="template-grid">
      <article v-for="item in templates" :key="item.id" class="template-card">
        <div class="template-head"><div><span class="code-badge">{{ item.template_code }}</span><h4>{{ item.template_name }}</h4></div><span class="tag" :class="item.status === 'ACTIVE' ? 'tag-success' : 'tag-default'">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></div>
        <p>{{ item.description || '暂无模板说明' }}</p>
        <div class="template-meta"><span>{{ item.applicable_org_type }}</span><span>{{ item.indicator_count }}项指标</span><span>创建人：{{ item.created_by_name }}</span></div>
        <div class="btn-group"><button class="btn btn-sm btn-outline" @click="openPreview(item)">预览</button><button class="btn btn-sm btn-outline" @click="copyTemplate(item)">复制</button><button class="btn btn-sm btn-outline" @click="toggleTemplate(item)">{{ item.status === 'ACTIVE' ? '停用' : '启用' }}</button><button class="btn btn-sm btn-primary" :disabled="item.status !== 'ACTIVE'" @click="openInitialize(item)">从模板初始化</button></div>
      </article>
      <div v-if="!templates.length" class="card empty">暂无真实模板，请从结构完整的指标版本创建。</div>
    </div>

    <div v-if="modal" class="modal-mask" @click.self="modal = ''"><div class="modal-card" :class="{ wide: modal === 'preview' }">
      <div class="card-header"><h3>{{ modal === 'create' ? '创建指标模板' : modal === 'initialize' ? '从模板初始化体系' : '模板快照预览' }}</h3><button class="btn btn-sm btn-outline" @click="modal = ''">关闭</button></div>
      <form v-if="modal === 'create'" class="form-grid" @submit.prevent="saveTemplate">
        <label class="span-2">来源版本<select v-model="form.sourceVersionId" required><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.system_name }} · {{ version.version_name }} · {{ version.status }}</option></select></label>
        <label>模板编码<input v-model="form.templateCode" required></label><label>模板名称<input v-model="form.templateName" required></label>
        <label>适用机构类型<select v-model="form.applicableOrgType"><option value="ADMINISTRATIVE">行政机关</option><option value="PARTY">党委单位</option><option value="PUBLIC_INSTITUTION">事业单位</option><option value="GENERAL">通用</option></select></label>
        <label class="span-2">说明<textarea v-model="form.description" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">保存独立快照</button>
      </form>
      <form v-else-if="modal === 'initialize'" class="form-grid" @submit.prevent="initialize">
        <label>新体系编码<input v-model="initForm.systemCode" required></label><label>新体系名称<input v-model="initForm.systemName" required></label>
        <label>年度<input v-model.number="initForm.evaluationYear" type="number" required></label>
        <label>适用机构类型<select v-model="initForm.applicableOrgType"><option value="ADMINISTRATIVE">行政机关</option><option value="PARTY">党委单位</option><option value="PUBLIC_INSTITUTION">事业单位</option><option value="GENERAL">通用</option></select></label>
        <label class="span-2">说明<textarea v-model="initForm.description" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">初始化草稿体系</button>
      </form>
      <div v-else-if="preview">
        <div class="alert alert-info">{{ preview.template_code }} · {{ preview.applicable_org_type }} · {{ preview.indicator_count }}项指标 · {{ preview.snapshot.rules.length }}条规则</div>
        <table><thead><tr><th>指标</th><th>层级</th><th>权重</th><th>标准分</th><th>状态</th></tr></thead><tbody><tr v-for="item in previewItems" :key="item.id"><td><span :style="{ paddingLeft: `${item.depth * 24}px` }"><span class="code-badge">{{ item.indicator_code }}</span> {{ item.indicator_name }}</span></td><td>{{ item.indicator_level }}级</td><td>{{ item.weight }}%</td><td>{{ item.indicator_level === 3 ? item.standard_score : '—' }}</td><td>{{ item.status }}</td></tr></tbody></table>
      </div>
    </div></div>
  </section>
</template>

<style scoped>
.template-page>.stat-grid{grid-template-columns:repeat(3,1fr);margin-bottom:18px}.template-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.template-card{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:18px}.template-head{display:flex;justify-content:space-between;gap:12px}.template-head h4{margin:10px 0}.template-meta{display:flex;flex-wrap:wrap;gap:8px;margin:15px 0}.template-meta span{background:#f1f5f9;padding:5px 8px;border-radius:6px;font-size:12px}.empty{text-align:center;color:#94a3b8;padding:30px}.modal-mask{position:fixed;inset:0;background:#0f172a88;display:flex;align-items:center;justify-content:center;z-index:1000}.modal-card{background:#fff;border-radius:14px;padding:20px;width:min(720px,94vw);max-height:90vh;overflow:auto}.modal-card.wide{width:min(1050px,96vw)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form-grid label{display:flex;flex-direction:column;gap:6px;font-weight:600}.span-2{grid-column:1/-1}@media(max-width:900px){.template-grid,.form-grid{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
