<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  archiveIndicatorVersion,
  copyIndicatorVersion,
  createIndicatorItem,
  createIndicatorSystem,
  fetchIndicatorSystems,
  fetchIndicatorTree,
  fetchIndicatorVersions,
  publishIndicatorVersion,
} from '../../indicatorApi'

const systems = ref([])
const versions = ref([])
const tree = ref([])
const selectedVersionId = ref('')
const loading = ref(false)
const error = ref('')
const message = ref('')
const modal = ref('')
const filters = reactive({ keyword: '', year: '', status: '' })
const systemForm = reactive({
  systemCode: '', systemName: '', evaluationYear: new Date().getFullYear(),
  applicableOrgType: 'ADMINISTRATIVE', description: '',
})
const itemForm = reactive(emptyItem())

function emptyItem() {
  return {
    versionId: '', parentId: null, indicatorLevel: 1, indicatorCode: '',
    indicatorName: '', standardScore: 0, weight: 0, indicatorType: 'COMMON',
    evaluationMethod: '', sortOrder: 0,
  }
}

const currentVersion = computed(() =>
  versions.value.find((item) => item.id === Number(selectedVersionId.value)))
const flatTree = computed(() => {
  const result = []
  const walk = (nodes, depth = 0) => nodes.forEach((node) => {
    result.push({ ...node, depth })
    walk(node.children || [], depth + 1)
  })
  walk(tree.value)
  return result
})
const parentOptions = computed(() =>
  flatTree.value.filter((item) =>
    item.status === 'ACTIVE' && item.indicator_level === itemForm.indicatorLevel - 1))
const totalIndicators = computed(() =>
  systems.value.reduce((sum, item) => sum + Number(item.indicator_count || 0), 0))

async function load() {
  loading.value = true
  error.value = ''
  try {
    systems.value = await fetchIndicatorSystems(filters)
    versions.value = await fetchIndicatorVersions()
    if (!selectedVersionId.value && versions.value.length) {
      selectedVersionId.value = versions.value[0].id
    }
    await loadTree()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function loadTree() {
  if (!selectedVersionId.value) {
    tree.value = []
    return
  }
  try { tree.value = await fetchIndicatorTree(selectedVersionId.value) }
  catch (e) { error.value = e.message }
}

function resetFilters() {
  Object.assign(filters, { keyword: '', year: '', status: '' })
  load()
}

async function saveSystem() {
  try {
    const created = await createIndicatorSystem(systemForm)
    message.value = '指标体系和首个草稿版本已创建'
    modal.value = ''
    selectedVersionId.value = created.currentVersionId
    Object.assign(systemForm, {
      systemCode: '', systemName: '', evaluationYear: new Date().getFullYear(),
      applicableOrgType: 'ADMINISTRATIVE', description: '',
    })
    await load()
  } catch (e) { error.value = e.message }
}

function openItem(level = 1, parentId = null) {
  Object.assign(itemForm, emptyItem(), {
    versionId: Number(selectedVersionId.value), indicatorLevel: level, parentId,
  })
  modal.value = 'item'
}

async function saveItem() {
  try {
    await createIndicatorItem({
      ...itemForm,
      parentId: itemForm.indicatorLevel === 1 ? null : Number(itemForm.parentId),
    })
    message.value = '指标已新增'
    modal.value = ''
    await loadTree()
    await load()
  } catch (e) { error.value = e.message }
}

async function publishVersion() {
  if (!currentVersion.value || !window.confirm('发布后版本只读，确认发布？')) return
  try {
    await publishIndicatorVersion(currentVersion.value.id, currentVersion.value.row_version)
    message.value = '指标版本已发布'
    await load()
  } catch (e) { error.value = e.message }
}

async function archiveVersion() {
  if (!currentVersion.value || !window.confirm('确认归档当前已发布版本？')) return
  try {
    await archiveIndicatorVersion(currentVersion.value.id, currentVersion.value.row_version)
    message.value = '指标版本已归档'
    await load()
  } catch (e) { error.value = e.message }
}

async function copyVersion() {
  if (!currentVersion.value) return
  const year = Number(window.prompt('目标年度', String(currentVersion.value.evaluation_year + 1)))
  if (!year) return
  try {
    const copied = await copyIndicatorVersion(currentVersion.value.id, {
      targetYear: year, versionName: `${year}年度复制草稿`,
    })
    selectedVersionId.value = copied.id
    message.value = '已复制为新草稿版本'
    await load()
  } catch (e) { error.value = e.message }
}

function levelName(level) { return ['一级', '二级', '三级'][level - 1] }
function statusName(status) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] || status
}

onMounted(load)
</script>

<template>
  <section class="page active indicator-page">
    <div class="alert alert-info">适用对象类型不计入层级；指标严格分为一级、二级、三级，只有三级指标作为最终评分项。</div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ systems.length }}</div><div class="label">指标体系</div></div>
      <div class="stat-card"><div class="num">{{ versions.length }}</div><div class="label">年度版本</div></div>
      <div class="stat-card"><div class="num">{{ totalIndicators }}</div><div class="label">指标总数</div></div>
      <div class="stat-card"><div class="num green">{{ versions.filter(v => v.status === 'PUBLISHED').length }}</div><div class="label">已发布版本</div></div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🧩</span>指标体系与年度版本</h3><button class="btn btn-primary" @click="modal = 'system'">＋ 创建指标体系</button></div>
      <div class="search-bar">
        <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="体系编码或名称" @keyup.enter="load"></div>
        <div class="form-item"><label>年度</label><input v-model="filters.year" type="number" placeholder="全部年度"></div>
        <div class="form-item"><label>状态</label><select v-model="filters.status"><option value="">全部</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></div>
        <button class="btn btn-primary" @click="load">查询</button><button class="btn btn-outline" @click="resetFilters">重置</button>
      </div>
      <div class="system-grid">
        <div v-for="system in systems" :key="system.id" class="feature-card">
          <div class="k">{{ system.system_name }}</div><div class="v code">{{ system.system_code }}</div>
          <div class="sub">{{ system.applicable_org_type }} · {{ system.version_count }}个版本 · {{ system.indicator_count }}项指标</div>
        </div>
        <div v-if="!systems.length && !loading" class="empty">暂无真实指标体系</div>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🌳</span>严格三级指标树</h3><div class="btn-group"><button class="btn btn-outline" :disabled="currentVersion?.status !== 'DRAFT'" @click="openItem()">新增一级指标</button><button class="btn btn-outline" @click="copyVersion">复制为新草稿</button><button v-if="currentVersion?.status === 'DRAFT'" class="btn btn-primary" @click="publishVersion">发布版本</button><button v-if="currentVersion?.status === 'PUBLISHED'" class="btn btn-outline" @click="archiveVersion">归档版本</button></div></div>
      <div class="version-select"><label>当前版本</label><select v-model="selectedVersionId" @change="loadTree"><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.system_name }} · {{ version.version_name }} · {{ statusName(version.status) }}</option></select><span v-if="currentVersion" class="tag" :class="currentVersion.status === 'PUBLISHED' ? 'tag-success' : currentVersion.status === 'DRAFT' ? 'tag-warning' : 'tag-default'">{{ statusName(currentVersion.status) }}</span></div>
      <table>
        <thead><tr><th>指标</th><th>层级</th><th>权重</th><th>标准分</th><th>类型</th><th>评估方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in flatTree" :key="item.id">
          <td><span :style="{ paddingLeft: `${item.depth * 26}px` }"><span class="code-badge">{{ item.indicator_code }}</span> {{ item.indicator_name }}</span></td>
          <td>{{ levelName(item.indicator_level) }}</td><td>{{ item.weight }}%</td><td>{{ item.indicator_level === 3 ? item.standard_score : '—' }}</td>
          <td>{{ item.indicator_type === 'COMMON' ? '共性' : '个性' }}</td><td>{{ item.evaluation_method || '—' }}</td>
          <td><span class="tag" :class="item.status === 'ACTIVE' ? 'tag-success' : 'tag-default'">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td>
          <td><button v-if="currentVersion?.status === 'DRAFT' && item.indicator_level < 3" class="btn btn-sm btn-outline" @click="openItem(item.indicator_level + 1, item.id)">新增下级</button></td>
        </tr></tbody>
      </table>
      <div v-if="!flatTree.length" class="empty">当前版本尚未创建指标，请从一级指标开始。</div>
    </div>

    <div v-if="modal" class="modal-mask" @click.self="modal = ''"><div class="modal-card">
      <div class="card-header"><h3>{{ modal === 'system' ? '创建指标体系' : '新增指标' }}</h3><button class="btn btn-sm btn-outline" @click="modal = ''">关闭</button></div>
      <form v-if="modal === 'system'" class="form-grid" @submit.prevent="saveSystem">
        <label>体系编码<input v-model="systemForm.systemCode" required placeholder="例如 SYS-2026"></label>
        <label>体系名称<input v-model="systemForm.systemName" required></label>
        <label>年度<input v-model.number="systemForm.evaluationYear" type="number" required></label>
        <label>适用机构类型<select v-model="systemForm.applicableOrgType"><option value="ADMINISTRATIVE">行政机关</option><option value="PARTY">党委单位</option><option value="PUBLIC_INSTITUTION">事业单位</option><option value="GENERAL">通用</option></select></label>
        <label class="span-2">说明<textarea v-model="systemForm.description" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">创建</button>
      </form>
      <form v-else class="form-grid" @submit.prevent="saveItem">
        <label>层级<input :value="levelName(itemForm.indicatorLevel)" disabled></label>
        <label v-if="itemForm.indicatorLevel > 1">父指标<select v-model="itemForm.parentId" required><option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">{{ parent.indicator_code }} {{ parent.indicator_name }}</option></select></label>
        <label>指标编码<input v-model="itemForm.indicatorCode" required></label><label>指标名称<input v-model="itemForm.indicatorName" required></label>
        <label>权重（%）<input v-model.number="itemForm.weight" type="number" min="0" max="100" step="0.01" required></label>
        <label v-if="itemForm.indicatorLevel === 3">标准分<input v-model.number="itemForm.standardScore" type="number" min="0" step="0.01" required></label>
        <label>指标类型<select v-model="itemForm.indicatorType"><option value="COMMON">共性</option><option value="CUSTOM">个性</option></select></label>
        <label>排序<input v-model.number="itemForm.sortOrder" type="number" min="0"></label>
        <label class="span-2">评估方式<textarea v-model="itemForm.evaluationMethod" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">保存指标</button>
      </form>
    </div></div>
  </section>
</template>

<style scoped>
.indicator-page>.stat-grid{grid-template-columns:repeat(4,1fr);margin-bottom:18px}.system-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.feature-card .code{font-size:16px}.version-select{display:flex;align-items:center;gap:12px;margin:14px 0}.version-select select{min-width:420px}.empty{text-align:center;color:#94a3b8;padding:28px}.modal-mask{position:fixed;inset:0;background:#0f172a88;display:flex;align-items:center;justify-content:center;z-index:1000}.modal-card{background:white;border-radius:14px;padding:20px;width:min(720px,94vw);max-height:90vh;overflow:auto}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form-grid label{display:flex;flex-direction:column;gap:6px;font-weight:600}.span-2{grid-column:1/-1}@media(max-width:900px){.system-grid,.form-grid{grid-template-columns:1fr}.indicator-page>.stat-grid{grid-template-columns:repeat(2,1fr)}.span-2{grid-column:auto}}
</style>
