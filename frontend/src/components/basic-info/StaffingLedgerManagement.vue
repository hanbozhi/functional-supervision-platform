<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchOrgUnits } from '../../orgUnitApi'
import {
  batchUpdateStaffing,
  createStaffingLedger,
  downloadStaffingTemplate,
  fetchStaffingChanges,
  fetchStaffingDetail,
  fetchStaffingLedgers,
  fetchStaffingStats,
  importStaffing,
  updateStaffingLedger,
} from '../../staffingApi'

const rows = ref([])
const orgOptions = ref([])
const selectedIds = ref([])
const loading = ref(false)
const message = ref('')
const error = ref('')
const modal = ref('')
const changes = ref([])
const importResult = ref(null)
const selectedDetail = ref(null)
const filters = reactive({ keyword: '', maintenanceStatus: '', anomalyStatus: '' })
const pager = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const stats = reactive({
  approvedStaffing: 0, actualStaffing: 0, externalStaff: 0, utilizationRate: 0,
  maintainedUnits: 0, totalUnits: 0, overstaffedUnits: 0, leadershipOverOccupiedUnits: 0,
})
const form = reactive(emptyForm())
const batch = reactive({ dataDate: today(), changeReason: '', items: [] })

function emptyForm() {
  return {
    id: null, orgUnitId: '', approvedStaffing: 0, actualStaffing: 0,
    leadershipPositionsApproved: 0, leadershipPositionsOccupied: 0,
    externalStaff: 0, dataDate: today(), changeReason: '', remarks: '', versionNo: null,
  }
}
function today() { return new Date().toISOString().slice(0, 10) }
function params() {
  return { ...filters, page: pager.page, size: pager.size }
}
async function load(page = pager.page) {
  loading.value = true
  error.value = ''
  pager.page = page
  try {
    const [data, summary] = await Promise.all([
      fetchStaffingLedgers(params()), fetchStaffingStats(filters),
    ])
    rows.value = data.items
    Object.assign(pager, { total: data.total, totalPages: data.totalPages })
    Object.assign(stats, summary)
    selectedIds.value = selectedIds.value.filter((id) => rows.value.some((row) => row.id === id))
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function loadOrganizations() {
  const data = await fetchOrgUnits({ status: 'ACTIVE', page: 1, size: 100 })
  orgOptions.value = data.items.filter((unit) => !['ROOT', 'GROUP'].includes(unit.unitType))
}
function resetFilters() {
  Object.assign(filters, { keyword: '', maintenanceStatus: '', anomalyStatus: '' })
  load(1)
}
function openCreate() {
  Object.assign(form, emptyForm())
  modal.value = 'form'
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, orgUnitId: row.orgUnitId, approvedStaffing: row.approvedStaffing ?? 0,
    actualStaffing: row.actualStaffing ?? 0,
    leadershipPositionsApproved: row.leadershipPositionsApproved ?? 0,
    leadershipPositionsOccupied: row.leadershipPositionsOccupied ?? 0,
    externalStaff: row.externalStaff ?? 0, dataDate: row.dataDate || today(),
    changeReason: '', remarks: row.remarks || '', versionNo: row.versionNo,
  })
  modal.value = 'form'
}
async function save() {
  error.value = ''
  try {
    if (!form.changeReason.trim()) throw new Error('请填写变更原因')
    const body = { ...form, orgUnitId: Number(form.orgUnitId) }
    if (form.id) await updateStaffingLedger(form.id, body)
    else await createStaffingLedger(body)
    modal.value = ''
    message.value = form.id ? '台账修改成功' : '台账新增成功'
    await load()
  } catch (e) { error.value = e.message }
}
async function openChanges(row) {
  try {
    const data = await fetchStaffingChanges(row.id, { page: 1, size: 50 })
    changes.value = data.items
    modal.value = 'changes'
  } catch (e) { error.value = e.message }
}
async function openDetail(row) {
  try {
    selectedDetail.value = await fetchStaffingDetail(row.id)
    modal.value = 'detail'
  } catch (e) { error.value = e.message }
}
function toggle(row) {
  if (!row.id) return
  selectedIds.value = selectedIds.value.includes(row.id)
    ? selectedIds.value.filter((id) => id !== row.id)
    : [...selectedIds.value, row.id]
}
function openBatch() {
  const selected = rows.value.filter((row) => selectedIds.value.includes(row.id))
  if (!selected.length) { error.value = '请先选择已维护的台账'; return }
  batch.dataDate = today()
  batch.changeReason = ''
  batch.items = selected.map((row) => ({
    id: row.id, unitName: row.unitName, approvedStaffing: row.approvedStaffing ?? 0,
    actualStaffing: row.actualStaffing ?? 0,
    leadershipPositionsApproved: row.leadershipPositionsApproved ?? 0,
    leadershipPositionsOccupied: row.leadershipPositionsOccupied ?? 0,
    externalStaff: row.externalStaff ?? 0, remarks: row.remarks || '', versionNo: row.versionNo,
  }))
  modal.value = 'batch'
}
async function saveBatch() {
  try {
    if (!batch.changeReason.trim()) throw new Error('请填写统一变更原因')
    await batchUpdateStaffing({
      dataDate: batch.dataDate, changeReason: batch.changeReason,
      items: batch.items.map(({ unitName, ...item }) => item),
    })
    modal.value = ''
    selectedIds.value = []
    message.value = `已批量修改${batch.items.length}个部门`
    await load()
  } catch (e) { error.value = e.message }
}
async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    importResult.value = await importStaffing(file)
    modal.value = 'import'
    await load()
  } catch (e) { error.value = e.message }
}
function sourceName(value) {
  return {
    MANUAL_CREATE: '单条新增', MANUAL_UPDATE: '单条修改',
    BATCH_UPDATE: '批量修改', EXCEL_IMPORT: 'Excel导入',
  }[value] || value
}
function fmt(value) { return value == null ? '-' : Number(value).toLocaleString() }
const maintainedSelected = computed(() => selectedIds.value.length)

onMounted(async () => {
  try { await Promise.all([load(1), loadOrganizations()]) } catch (e) { error.value = e.message }
})
</script>

<template>
  <section class="page active staffing-page">
    <div class="alert alert-info">通过模板导入和单条维护管理核定编制、实有在编、领导职数、编外人员，并永久保留变更日志。</div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">👥</span>编制人员台账</h3><span class="extra">SQLite实时数据</span></div>
      <div class="field-grid">
        <div class="field-box"><div class="name">核定编制</div><div class="val">{{ fmt(stats.approvedStaffing) }}</div><small>使用率 {{ stats.utilizationRate }}%</small></div>
        <div class="field-box"><div class="name">实有在编</div><div class="val">{{ fmt(stats.actualStaffing) }}</div><small>超编部门 {{ stats.overstaffedUnits }}</small></div>
        <div class="field-box"><div class="name">编外人员</div><div class="val">{{ fmt(stats.externalStaff) }}</div><small>已维护 {{ stats.maintainedUnits }}/{{ stats.totalUnits }}</small></div>
      </div>
      <div class="search-bar">
        <div class="form-item"><label>机构关键词</label><input v-model.trim="filters.keyword" placeholder="机构名称或编码" @keyup.enter="load(1)"></div>
        <div class="form-item"><label>维护状态</label><select v-model="filters.maintenanceStatus"><option value="">全部</option><option value="MAINTAINED">已维护</option><option value="UNMAINTAINED">未维护</option></select></div>
        <div class="form-item"><label>异常状态</label><select v-model="filters.anomalyStatus"><option value="">全部</option><option value="OVERSTAFFED">超编</option><option value="LEADERSHIP_OVER">领导超职数</option></select></div>
        <button class="btn btn-primary" @click="load(1)">查询</button>
        <button class="btn btn-outline" @click="resetFilters">重置</button>
        <button class="btn btn-outline" @click="load()">刷新</button>
      </div>
      <div class="btn-group">
        <button class="btn btn-outline" @click="downloadStaffingTemplate">⬇ 下载模板</button>
        <label class="btn btn-primary upload-button">📥 Excel导入<input type="file" accept=".xlsx" @change="upload"></label>
        <button class="btn btn-outline" @click="openCreate">单条新增</button>
        <button class="btn btn-outline" :disabled="!maintainedSelected" @click="openBatch">批量修改（{{ maintainedSelected }}）</button>
      </div>
      <div class="table-scroll">
        <table>
          <thead><tr><th>选择</th><th>部门</th><th>核定编制</th><th>实有在编</th><th>使用率</th><th>领导职数</th><th>编外人员</th><th>最近异动</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="10" class="empty-cell">数据加载中...</td></tr>
            <tr v-else-if="!rows.length"><td colspan="10" class="empty-cell">暂无匹配机构</td></tr>
            <tr v-for="row in rows" v-else :key="row.orgUnitId">
              <td><input type="checkbox" :checked="selectedIds.includes(row.id)" :disabled="!row.maintained || row.unitStatus !== 'ACTIVE'" @change="toggle(row)"></td>
              <td><b>{{ row.unitName }}</b><small class="block">{{ row.unitCode }}</small></td>
              <td>{{ fmt(row.approvedStaffing) }}</td><td>{{ fmt(row.actualStaffing) }}</td>
              <td><span :class="row.overstaffed ? 'tag tag-danger' : 'tag tag-info'">{{ row.utilizationRate }}%</span></td>
              <td><span :class="row.leadershipOverOccupied ? 'tag tag-danger' : ''">{{ fmt(row.leadershipPositionsOccupied) }}/{{ fmt(row.leadershipPositionsApproved) }}</span></td>
              <td>{{ fmt(row.externalStaff) }}</td><td>{{ row.lastChangeSummary || '-' }}</td>
              <td><span :class="row.maintained ? 'tag tag-success' : 'tag tag-default'">{{ row.maintained ? '已维护' : '未维护' }}</span></td>
              <td><button v-if="row.maintained" class="btn btn-sm btn-outline" @click="openDetail(row)">查看</button> <button v-if="row.maintained && row.unitStatus === 'ACTIVE'" class="btn btn-sm btn-outline" @click="openEdit(row)">编辑</button> <button v-if="row.maintained" class="btn btn-sm btn-outline" @click="openChanges(row)">变更记录</button><button v-else-if="row.unitStatus === 'ACTIVE'" class="btn btn-sm btn-primary" @click="openCreate(); form.orgUnitId=row.orgUnitId">新增</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="btn-group pager-group"><button class="btn btn-outline" :disabled="pager.page<=1" @click="load(pager.page-1)">上一页</button><span>共 {{ pager.total }} 条，第 {{ pager.page }}/{{ pager.totalPages || 1 }} 页</span><button class="btn btn-outline" :disabled="pager.page>=pager.totalPages" @click="load(pager.page+1)">下一页</button></div>
    </div>

    <div v-if="modal" class="evidence-modal show" @click.self="modal=''">
      <div class="evidence-panel staffing-modal">
        <div class="panel-head"><h3>{{ modal==='form' ? (form.id ? '编辑台账' : '新增台账') : modal==='batch' ? '批量修改' : modal==='changes' ? '变更记录' : modal==='detail' ? '台账详情' : '导入结果' }}</h3><button class="btn btn-sm btn-outline" @click="modal=''">关闭</button></div>
        <div v-if="modal==='form'" class="panel-body">
          <div class="staffing-form">
            <label>机构<select v-model="form.orgUnitId" :disabled="!!form.id"><option value="">请选择</option><option v-for="org in orgOptions" :key="org.id" :value="org.id">{{ org.unitName }}（{{ org.unitCode }}）</option></select></label>
            <label>数据日期<input v-model="form.dataDate" type="date"></label>
            <label>核定编制<input v-model.number="form.approvedStaffing" type="number" min="0"></label>
            <label>实有在编<input v-model.number="form.actualStaffing" type="number" min="0"></label>
            <label>领导职数核定<input v-model.number="form.leadershipPositionsApproved" type="number" min="0"></label>
            <label>领导职数占用<input v-model.number="form.leadershipPositionsOccupied" type="number" min="0"></label>
            <label>编外人员<input v-model.number="form.externalStaff" type="number" min="0"></label>
            <label class="wide">变更原因<input v-model.trim="form.changeReason" maxlength="200"></label>
            <label class="wide">备注<textarea v-model.trim="form.remarks" rows="3"></textarea></label>
          </div><div class="btn-group"><button class="btn btn-primary" @click="save">保存</button></div>
        </div>
        <div v-else-if="modal==='batch'" class="panel-body">
          <div class="staffing-form"><label>统一数据日期<input v-model="batch.dataDate" type="date"></label><label class="wide">统一变更原因<input v-model.trim="batch.changeReason"></label></div>
          <div class="table-scroll"><table><thead><tr><th>部门</th><th>核定</th><th>实有</th><th>领导核定</th><th>领导占用</th><th>编外</th></tr></thead><tbody><tr v-for="item in batch.items" :key="item.id"><td>{{ item.unitName }}</td><td><input v-model.number="item.approvedStaffing" type="number" min="0"></td><td><input v-model.number="item.actualStaffing" type="number" min="0"></td><td><input v-model.number="item.leadershipPositionsApproved" type="number" min="0"></td><td><input v-model.number="item.leadershipPositionsOccupied" type="number" min="0"></td><td><input v-model.number="item.externalStaff" type="number" min="0"></td></tr></tbody></table></div>
          <div class="btn-group"><button class="btn btn-primary" @click="saveBatch">提交批量修改</button></div>
        </div>
        <div v-else-if="modal==='changes'" class="panel-body"><div v-if="!changes.length" class="empty-cell">暂无变更记录</div><div v-for="item in changes" :key="item.id" class="log-item"><span><b>{{ item.operatedAt }}</b>　{{ sourceName(item.changeSource) }}：{{ item.changeReason }}<small class="block">变化字段：{{ item.changedFields }}；操作人：{{ item.operatorName || '-' }}</small></span><span class="tag tag-info">系统留痕</span></div></div>
        <div v-else-if="modal==='detail'" class="panel-body">
          <div class="evidence-grid">
            <div class="evidence-box"><div class="label">机构</div><div class="value">{{ selectedDetail?.unitName }}</div></div><div class="evidence-box"><div class="label">机构编码</div><div class="value">{{ selectedDetail?.unitCode }}</div></div>
            <div class="evidence-box"><div class="label">核定编制</div><div class="value">{{ fmt(selectedDetail?.approvedStaffing) }}</div></div><div class="evidence-box"><div class="label">实有在编</div><div class="value">{{ fmt(selectedDetail?.actualStaffing) }}</div></div>
            <div class="evidence-box"><div class="label">领导职数</div><div class="value">{{ fmt(selectedDetail?.leadershipPositionsOccupied) }}/{{ fmt(selectedDetail?.leadershipPositionsApproved) }}</div></div><div class="evidence-box"><div class="label">编外人员</div><div class="value">{{ fmt(selectedDetail?.externalStaff) }}</div></div>
            <div class="evidence-box"><div class="label">数据日期</div><div class="value">{{ selectedDetail?.dataDate }}</div></div><div class="evidence-box"><div class="label">最后修改</div><div class="value">{{ selectedDetail?.updatedByName || '-' }} / {{ selectedDetail?.updatedAt || '-' }}</div></div>
            <div class="evidence-box wide"><div class="label">备注</div><div class="value">{{ selectedDetail?.remarks || '-' }}</div></div>
          </div>
        </div>
        <div v-else class="panel-body"><div class="alert" :class="importResult?.failedRows ? 'alert-warning' : 'alert-success'">共 {{ importResult?.totalRows }} 行，成功 {{ importResult?.successRows }} 行，失败 {{ importResult?.failedRows }} 行，警告 {{ importResult?.warningRows }} 行。</div><div v-for="warning in importResult?.warnings" :key="warning" class="log-item">{{ warning }}</div><div v-for="item in importResult?.errors" :key="item.rowNumber" class="log-item"><span>第{{ item.rowNumber }}行 {{ item.orgUnitCode || '-' }} / {{ item.orgUnitName || '-' }}</span><span class="tag tag-danger">{{ item.errorMessage }}</span></div></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.staffing-page .field-grid { margin-bottom: 18px; }
.staffing-page small.block { display:block; margin-top:5px; color:var(--text-light); }
.upload-button { display:inline-flex; align-items:center; cursor:pointer; }
.upload-button input { display:none; }
.staffing-modal { width:min(1050px, 94vw); max-height:90vh; overflow:auto; }
.staffing-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; }
.staffing-form label { display:flex; flex-direction:column; gap:7px; font-weight:600; }
.staffing-form .wide { grid-column:1/-1; }
.staffing-form input,.staffing-form select,.staffing-form textarea { padding:10px 12px; border:1px solid var(--border); border-radius:6px; font:inherit; }
.staffing-page table input[type=number] { width:80px; padding:6px; }
@media (max-width:760px) { .staffing-form { grid-template-columns:1fr; } .staffing-form .wide { grid-column:auto; } }
</style>
