<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { fetchOrgUnits } from '../../orgUnitApi'
import {
  applyAutoMappings, createCoreFunction, createDuty, createManualMatchResult,
  fetchCoreFunctions, fetchCoreFunctionStats, fetchDuties, fetchDutyPreview,
  fetchMatchResults, fetchMatchRuns, fetchOrgRightsItems, fetchRightsMappings,
  importDutyCandidates, reviewMatchResult, saveRightsMappings, startMatchRun,
  updateCoreFunction, updateCoreFunctionStatus, updateDuty, updateDutyStatus,
} from '../../coreFunctionApi'

const organizations = ref([])
const orgId = ref('')
const functions = ref([])
const duties = ref([])
const runs = ref([])
const results = ref([])
const rightsOptions = ref([])
const mappings = ref([])
const selectedMappings = ref([])
const candidates = ref([])
const previewMeta = reactive({ available: false, message: '', sourceVersionId: null, versionLabel: '' })
const stats = reactive({ activeFunctions: 0, activeDuties: 0, unmappedDepartments: 0, latestRun: {} })
const filters = reactive({ keyword: '', status: '' })
const resultFilters = reactive({ resultType: '', reviewStatus: '' })
const pager = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const loading = ref(false)
const modal = ref('')
const error = ref('')
const message = ref('')
const activeRun = ref(null)
const functionForm = reactive(emptyFunction())
const dutyForm = reactive(emptyDuty())
const reviewForm = reactive(emptyReview())

function emptyFunction() {
  return { id: null, functionCode: '', functionName: '', industryTag: '', description: '', sortOrder: 0, versionNo: null }
}
function emptyDuty() {
  return { id: null, coreFunctionId: '', dutyContent: '', keywords: '', sortOrder: 0, versionNo: null }
}
function emptyReview() {
  return { id: null, resultType: 'MATCHED', dutyItemId: '', rightsItemId: '', finalScore: 0, reviewStatus: 'CONFIRMED', opinion: '', versionNo: null }
}
const activeFunctions = computed(() => functions.value.filter((item) => item.status === 'ACTIVE'))
const activeDuties = computed(() => duties.value.filter((item) => item.status === 'ACTIVE' && item.function_status === 'ACTIVE'))
const latestRun = computed(() => runs.value[0] || null)

async function loadOrganizations() {
  const page = await fetchOrgUnits({ status: 'ACTIVE', page: 1, size: 100 })
  organizations.value = page.items.filter((item) => !['ROOT', 'GROUP'].includes(item.unitType))
  if (!orgId.value && organizations.value.length) orgId.value = organizations.value[0].id
}
async function loadAll(page = 1) {
  if (!orgId.value) return
  loading.value = true
  error.value = ''
  try {
    pager.page = page
    const [functionPage, dutyRows, summary, runRows] = await Promise.all([
      fetchCoreFunctions({ orgId: orgId.value, ...filters, page, size: pager.size }),
      fetchDuties({ orgId: orgId.value }),
      fetchCoreFunctionStats(orgId.value),
      fetchMatchRuns(orgId.value),
    ])
    functions.value = functionPage.items
    duties.value = dutyRows
    runs.value = runRows
    Object.assign(pager, { total: functionPage.total, totalPages: functionPage.totalPages })
    Object.assign(stats, summary)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
watch(orgId, () => loadAll(1))
function resetFilters() {
  Object.assign(filters, { keyword: '', status: '' })
  loadAll(1)
}
function showSuccess(value) {
  message.value = value
  window.setTimeout(() => { if (message.value === value) message.value = '' }, 2500)
}

function openFunction(item = null) {
  Object.assign(functionForm, emptyFunction(), item ? {
    id: item.id, functionCode: item.function_code, functionName: item.function_name,
    industryTag: item.industry_tag || '', description: item.description || '',
    sortOrder: item.sort_order, versionNo: item.version_no,
  } : {})
  modal.value = 'function'
}
async function saveFunction() {
  try {
    const body = { ...functionForm, orgUnitId: Number(orgId.value) }
    if (functionForm.id) await updateCoreFunction(functionForm.id, body)
    else await createCoreFunction(body)
    modal.value = ''; showSuccess('核心职能已保存'); await loadAll()
  } catch (e) { error.value = e.message }
}
async function toggleFunction(item) {
  try {
    await updateCoreFunctionStatus(item.id, {
      status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', versionNo: item.version_no,
    })
    await loadAll(); showSuccess('核心职能状态已更新')
  } catch (e) { error.value = e.message }
}

function openDuty(item = null, functionId = '') {
  Object.assign(dutyForm, emptyDuty(), item ? {
    id: item.id, coreFunctionId: item.core_function_id,
    dutyContent: item.duty_content, keywords: item.keywords || '',
    sortOrder: item.sort_order, versionNo: item.version_no,
  } : { coreFunctionId: functionId || activeFunctions.value[0]?.id || '' })
  modal.value = 'duty'
}
async function saveDuty() {
  try {
    const body = { ...dutyForm, coreFunctionId: Number(dutyForm.coreFunctionId) }
    if (dutyForm.id) await updateDuty(dutyForm.id, body)
    else await createDuty(body)
    modal.value = ''; showSuccess('职责条目已保存'); await loadAll()
  } catch (e) { error.value = e.message }
}
async function toggleDuty(item) {
  try {
    await updateDutyStatus(item.id, {
      status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', versionNo: item.version_no,
    })
    await loadAll(); showSuccess('职责状态已更新')
  } catch (e) { error.value = e.message }
}

async function openPreview() {
  try {
    const data = await fetchDutyPreview(orgId.value)
    Object.assign(previewMeta, {
      available: data.available, message: data.message || '',
      sourceVersionId: data.sourceVersionId || null, versionLabel: data.versionLabel || '',
    })
    candidates.value = (data.items || []).map((item) => ({
      coreFunctionId: activeFunctions.value[0]?.id || '', ...item,
    }))
    modal.value = 'preview'
  } catch (e) { error.value = e.message }
}
function addCandidate() {
  candidates.value.push({
    coreFunctionId: activeFunctions.value[0]?.id || '', dutyContent: '',
    keywords: '', sourceSnippet: '', sortOrder: (candidates.value.length + 1) * 10,
  })
}
async function confirmCandidates() {
  try {
    await importDutyCandidates(orgId.value, {
      sourceVersionId: previewMeta.sourceVersionId,
      items: candidates.value.map((item) => ({
        ...item, coreFunctionId: Number(item.coreFunctionId),
      })),
    })
    modal.value = ''; showSuccess('三定职责候选已确认入库'); await loadAll()
  } catch (e) { error.value = e.message }
}

async function openMappings() {
  try {
    const data = await fetchRightsMappings(orgId.value)
    mappings.value = data.departments
    selectedMappings.value = data.departments.filter((item) => item.selected)
      .map((item) => item.department_name)
    modal.value = 'mappings'
  } catch (e) { error.value = e.message }
}
async function autoMap() {
  try {
    const data = await applyAutoMappings(orgId.value)
    mappings.value = data.departments
    selectedMappings.value = data.departments.filter((item) => item.selected)
      .map((item) => item.department_name)
    showSuccess('已应用可唯一识别的自动映射')
  } catch (e) { error.value = e.message }
}
async function saveMappings() {
  try {
    await saveRightsMappings(orgId.value, { departmentNames: selectedMappings.value })
    modal.value = ''; showSuccess('部门映射已保存'); await loadAll()
  } catch (e) { error.value = e.message }
}
function mappingDisabled(item) {
  return item.org_unit_id && Number(item.org_unit_id) !== Number(orgId.value)
}

async function runMatch() {
  try {
    const run = await startMatchRun(orgId.value, { threshold: 50 })
    showSuccess('关键词匹配已完成')
    await loadAll()
    await openResults(run)
  } catch (e) { error.value = e.message }
}
async function openResults(run = latestRun.value) {
  if (!run) { error.value = '尚无匹配运行记录'; return }
  try {
    activeRun.value = run
    const [resultRows, rights] = await Promise.all([
      fetchMatchResults(run.id, resultFilters),
      fetchOrgRightsItems(orgId.value),
    ])
    results.value = resultRows
    rightsOptions.value = rights
    modal.value = 'results'
  } catch (e) { error.value = e.message }
}
async function reloadResults() {
  if (!activeRun.value) return
  results.value = await fetchMatchResults(activeRun.value.id, resultFilters)
}
function openReview(item = null) {
  Object.assign(reviewForm, emptyReview(), item ? {
    id: item.id, resultType: item.result_type,
    dutyItemId: item.duty_item_id || '', rightsItemId: item.rights_item_id || '',
    finalScore: item.final_score, reviewStatus: item.review_status === 'PENDING' ? 'CONFIRMED' : item.review_status,
    opinion: item.processing_opinion || '', versionNo: item.version_no,
  } : { reviewStatus: 'ADJUSTED' })
  modal.value = 'review'
}
function normalizeSides() {
  if (reviewForm.resultType === 'DUTY_MISSING') reviewForm.rightsItemId = ''
  if (reviewForm.resultType === 'UNAPPROVED_NEW_DUTY') reviewForm.dutyItemId = ''
}
async function saveReview() {
  try {
    normalizeSides()
    const body = {
      ...reviewForm,
      dutyItemId: reviewForm.dutyItemId ? Number(reviewForm.dutyItemId) : null,
      rightsItemId: reviewForm.rightsItemId ? Number(reviewForm.rightsItemId) : null,
      finalScore: Number(reviewForm.finalScore),
    }
    if (reviewForm.id) await reviewMatchResult(reviewForm.id, body)
    else await createManualMatchResult(activeRun.value.id, body)
    modal.value = ''; showSuccess('人工处理结果已保存')
    await loadAll()
    await openResults(runs.value.find((item) => item.id === activeRun.value.id) || activeRun.value)
  } catch (e) { error.value = e.message }
}
function resultLabel(value) {
  return { MATCHED: '正常匹配', DUTY_MISSING: '职责缺失', UNAPPROVED_NEW_DUTY: '新增职责未核定' }[value] || value
}
function resultClass(value) {
  return value === 'MATCHED' ? 'tag tag-success' : value === 'DUTY_MISSING' ? 'tag tag-danger' : 'tag tag-warning'
}
function percent(value) { return `${Number(value || 0).toFixed(1)}%` }

onMounted(async () => {
  try { await loadOrganizations(); await loadAll(1) } catch (e) { error.value = e.message }
})
</script>

<template>
  <section class="page active core-function-page">
    <div class="alert alert-info">以已确认三定职责和权责清单为本地数据源，通过可解释关键词规则建立部门核心职能清单；自动结果均可人工复核。</div>
    <div v-if="stats.unmappedDepartments" class="alert alert-warning">当前仍有 {{ stats.unmappedDepartments }} 个权责部门未映射到机构，这些事项不会参与匹配。</div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🧭</span>部门核心职能清单</h3><span class="extra">SQLite真实数据</span></div>
      <div class="search-bar">
        <div class="form-item"><label>机构</label><select v-model="orgId"><option v-for="org in organizations" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></div>
        <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="职能编码、名称或行业" @keyup.enter="loadAll(1)"></div>
        <div class="form-item"><label>状态</label><select v-model="filters.status"><option value="">全部</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></div>
        <button class="btn btn-primary" @click="loadAll(1)">查询</button><button class="btn btn-outline" @click="resetFilters">重置</button><button class="btn btn-outline" @click="loadAll()">刷新</button>
      </div>
      <div class="stat-grid">
        <div class="stat-card"><div class="num">{{ stats.activeFunctions }}</div><div class="label">启用核心职能</div><div class="sub">当前机构</div></div>
        <div class="stat-card"><div class="num green">{{ stats.activeDuties }}</div><div class="label">启用职责条目</div><div class="sub">三定与手工来源</div></div>
        <div class="stat-card"><div class="num orange">{{ percent(latestRun?.coverage_rate) }}</div><div class="label">职责覆盖率</div><div class="sub">{{ latestRun ? `运行 #${latestRun.id}` : '尚未匹配' }}</div></div>
        <div class="stat-card"><div class="num red">{{ (latestRun?.duty_missing_count || 0) + (latestRun?.unapproved_new_count || 0) }}</div><div class="label">异常数量</div><div class="sub">匹配度 {{ percent(latestRun?.match_rate) }}</div></div>
      </div>
      <div class="btn-group">
        <button class="btn btn-primary" @click="openFunction()">新增核心职能</button>
        <button class="btn btn-outline" :disabled="!activeFunctions.length" @click="openDuty()">手工新增职责</button>
        <button class="btn btn-outline" :disabled="!activeFunctions.length" @click="openPreview">从三定生成候选</button>
        <button class="btn btn-outline" @click="openMappings">部门映射设置</button>
        <button class="btn btn-outline" @click="runMatch">{{ latestRun ? '重新匹配' : '自动匹配' }}</button>
        <button class="btn btn-outline" :disabled="!latestRun" @click="openResults()">查看匹配结果</button>
      </div>
      <div class="table-scroll">
        <table><thead><tr><th>职能编码</th><th>核心职能</th><th>行业标签</th><th>职责数</th><th>说明</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-if="loading"><td colspan="7" class="empty-cell">数据加载中...</td></tr>
          <tr v-else-if="!functions.length"><td colspan="7" class="empty-cell">暂无核心职能，可先手工新增</td></tr>
          <tr v-for="item in functions" v-else :key="item.id"><td><span class="code-badge">{{ item.function_code }}</span></td><td><b>{{ item.function_name }}</b></td><td>{{ item.industry_tag || '-' }}</td><td>{{ item.duty_count }}</td><td>{{ item.description || '-' }}</td><td><span :class="item.status==='ACTIVE'?'tag tag-success':'tag tag-default'">{{ item.status==='ACTIVE'?'启用':'停用' }}</span></td><td><button class="btn btn-sm btn-outline" @click="openFunction(item)">编辑</button> <button class="btn btn-sm btn-outline" @click="openDuty(null,item.id)">新增职责</button> <button class="btn btn-sm btn-outline" @click="toggleFunction(item)">{{ item.status==='ACTIVE'?'停用':'启用' }}</button></td></tr>
        </tbody></table>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">📋</span>职责条目</h3><span class="extra">共 {{ duties.length }} 条</span></div>
      <div class="table-scroll"><table><thead><tr><th>核心职能</th><th>职责内容</th><th>关键词</th><th>来源</th><th>状态</th><th>操作</th></tr></thead><tbody>
        <tr v-if="!duties.length"><td colspan="6" class="empty-cell">暂无职责条目</td></tr>
        <tr v-for="item in duties" v-else :key="item.id"><td>{{ item.function_name }}</td><td>{{ item.duty_content }}</td><td>{{ item.keywords || '待人工补充' }}</td><td>{{ item.source_type==='THREE_FIXED' ? `三定 ${item.version_label || ''}` : '手工录入' }}</td><td><span :class="item.status==='ACTIVE'?'tag tag-success':'tag tag-default'">{{ item.status }}</span></td><td><button class="btn btn-sm btn-outline" @click="openDuty(item)">编辑</button> <button v-if="item.status!=='SUPERSEDED'" class="btn btn-sm btn-outline" @click="toggleDuty(item)">{{ item.status==='ACTIVE'?'停用':'启用' }}</button></td></tr>
      </tbody></table></div>
    </div>

    <div v-if="runs.length" class="card">
      <div class="card-header"><h3><span class="icon">🕘</span>匹配运行历史</h3><span class="extra">重新匹配不覆盖历史</span></div>
      <div v-for="run in runs" :key="run.id" class="log-item"><span><b>运行 #{{ run.id }}</b>　覆盖率 {{ percent(run.coverage_rate) }}，匹配度 {{ percent(run.match_rate) }}，异常 {{ run.duty_missing_count + run.unapproved_new_count }}<small class="block">{{ run.created_at }} / {{ run.created_by_name || '-' }}</small></span><span><span v-if="run.data_stale" class="tag tag-warning">权责数据已变化</span> <button class="btn btn-sm btn-outline" @click="openResults(run)">结果</button></span></div>
    </div>

    <div v-if="modal" class="evidence-modal show" @click.self="modal=''">
      <div class="evidence-panel core-modal">
        <div class="panel-head"><h3>{{ {function:'核心职能',duty:'职责条目',preview:'三定职责候选',mappings:'权责部门映射',results:'匹配结果',review:'人工复核与调整'}[modal] }}</h3><button class="btn btn-sm btn-outline" @click="modal=''">关闭</button></div>
        <div v-if="modal==='function'" class="panel-body"><div class="edit-grid"><label>职能编码<input v-model.trim="functionForm.functionCode" placeholder="例如 ECONOMIC"></label><label>职能名称<input v-model.trim="functionForm.functionName"></label><label>行业标签<input v-model.trim="functionForm.industryTag"></label><label>排序<input v-model.number="functionForm.sortOrder" type="number" min="0"></label><label class="wide">职能说明<textarea v-model.trim="functionForm.description" rows="3"></textarea></label></div><div class="btn-group"><button class="btn btn-primary" @click="saveFunction">保存</button></div></div>
        <div v-else-if="modal==='duty'" class="panel-body"><div class="edit-grid"><label>核心职能<select v-model="dutyForm.coreFunctionId"><option v-for="item in activeFunctions" :key="item.id" :value="item.id">{{ item.function_name }}</option></select></label><label>排序<input v-model.number="dutyForm.sortOrder" type="number" min="0"></label><label class="wide">职责内容<textarea v-model.trim="dutyForm.dutyContent" rows="4"></textarea></label><label class="wide">关键词（逗号分隔，可留空自动生成）<input v-model.trim="dutyForm.keywords"></label></div><div class="btn-group"><button class="btn btn-primary" @click="saveDuty">保存</button></div></div>
        <div v-else-if="modal==='preview'" class="panel-body"><div v-if="!previewMeta.available" class="alert alert-warning">{{ previewMeta.message }}</div><template v-else><div class="alert alert-info">来源：{{ previewMeta.versionLabel }}。可直接修改、删除或补充简单候选列表。</div><div v-for="(item,index) in candidates" :key="index" class="candidate-row"><select v-model="item.coreFunctionId"><option v-for="fn in activeFunctions" :key="fn.id" :value="fn.id">{{ fn.function_name }}</option></select><textarea v-model.trim="item.dutyContent" rows="2"></textarea><input v-model.trim="item.keywords" placeholder="关键词"><button class="btn btn-sm btn-outline" @click="candidates.splice(index,1)">删除</button></div><div class="btn-group"><button class="btn btn-outline" @click="addCandidate">补充一条</button><button class="btn btn-primary" :disabled="!candidates.length" @click="confirmCandidates">确认替换三定来源职责</button></div></template></div>
        <div v-else-if="modal==='mappings'" class="panel-body"><div class="alert alert-info">已映射到其他机构的部门不可选择；未映射部门不会参与匹配。</div><div class="btn-group"><button class="btn btn-outline" @click="autoMap">应用自动建议</button></div><div class="mapping-list"><label v-for="item in mappings" :key="item.department_name" :class="{disabled:mappingDisabled(item)}"><input v-model="selectedMappings" type="checkbox" :value="item.department_name" :disabled="mappingDisabled(item)"> {{ item.department_name }} <span v-if="item.suggested" class="tag tag-info">建议</span><small v-if="mappingDisabled(item)">已映射：{{ item.mapped_org_name }}</small></label></div><div class="btn-group"><button class="btn btn-primary" @click="saveMappings">保存映射</button></div></div>
        <div v-else-if="modal==='results'" class="panel-body"><div class="search-bar"><div class="form-item"><select v-model="resultFilters.resultType"><option value="">全部结果</option><option value="MATCHED">正常匹配</option><option value="DUTY_MISSING">职责缺失</option><option value="UNAPPROVED_NEW_DUTY">新增职责未核定</option></select></div><div class="form-item"><select v-model="resultFilters.reviewStatus"><option value="">全部处理状态</option><option value="PENDING">待处理</option><option value="CONFIRMED">已确认</option><option value="REJECTED">已驳回</option><option value="ADJUSTED">已调整</option></select></div><button class="btn btn-primary" @click="reloadResults">筛选</button><button class="btn btn-outline" @click="openReview()">人工建立匹配</button></div><div class="compare-grid result-list"><div v-for="item in results" :key="item.id" class="compare-box"><h4><span :class="resultClass(item.result_type)">{{ resultLabel(item.result_type) }}</span>　{{ item.final_score }}%</h4><p><b>三定职责：</b>{{ item.duty_content_snapshot || '无关联职责' }}</p><p><b>权责事项：</b>{{ item.rights_item_name_snapshot || '无关联权责事项' }}</p><p><b>命中关键词：</b>{{ item.matched_keywords || '无' }}</p><div class="btn-group"><span class="tag tag-default">{{ item.review_status }}</span><button class="btn btn-sm btn-outline" @click="openReview(item)">确认/调整</button></div></div></div></div>
        <div v-else-if="modal==='review'" class="panel-body"><div class="edit-grid"><label>结果类型<select v-model="reviewForm.resultType" @change="normalizeSides"><option value="MATCHED">正常匹配</option><option value="DUTY_MISSING">职责缺失</option><option value="UNAPPROVED_NEW_DUTY">新增职责未核定</option></select></label><label>处理状态<select v-model="reviewForm.reviewStatus"><option value="CONFIRMED">确认</option><option value="REJECTED">驳回</option><option value="ADJUSTED">调整</option></select></label><label v-if="reviewForm.resultType!=='UNAPPROVED_NEW_DUTY'">职责条目<select v-model="reviewForm.dutyItemId"><option value="">请选择</option><option v-for="item in activeDuties" :key="item.id" :value="item.id">{{ item.duty_content }}</option></select></label><label v-if="reviewForm.resultType!=='DUTY_MISSING'">权责事项<select v-model="reviewForm.rightsItemId"><option value="">请选择</option><option v-for="item in rightsOptions" :key="item.id" :value="item.id">{{ item.itemName }}</option></select></label><label>最终分值<input v-model.number="reviewForm.finalScore" type="number" min="0" max="100"></label><label class="wide">处理意见<textarea v-model.trim="reviewForm.opinion" rows="3"></textarea></label></div><div class="btn-group"><button class="btn btn-primary" @click="saveReview">保存处理结果</button></div></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.core-function-page small.block{display:block;margin-top:5px;color:var(--text-light)}
.core-modal{width:min(1120px,95vw);max-height:92vh;overflow:auto}
.edit-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}
.edit-grid label{display:flex;flex-direction:column;gap:7px;font-weight:600}
.edit-grid .wide{grid-column:1/-1}
.edit-grid input,.edit-grid select,.edit-grid textarea,.candidate-row input,.candidate-row select,.candidate-row textarea{padding:10px;border:1px solid var(--border);border-radius:6px;font:inherit}
.candidate-row{display:grid;grid-template-columns:180px 1fr 240px auto;gap:10px;align-items:center;margin-bottom:10px}
.mapping-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin:14px 0}
.mapping-list label{padding:10px;border:1px solid var(--border);border-radius:6px}
.mapping-list label.disabled{opacity:.55}.mapping-list small{display:block;margin-left:22px}
.result-list{grid-template-columns:repeat(2,minmax(0,1fr));margin-top:14px}
@media(max-width:800px){.edit-grid,.mapping-list,.result-list{grid-template-columns:1fr}.edit-grid .wide{grid-column:auto}.candidate-row{grid-template-columns:1fr}}
</style>
