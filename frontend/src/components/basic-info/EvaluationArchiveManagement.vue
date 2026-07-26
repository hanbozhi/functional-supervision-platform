<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchOrgTree } from '../../orgUnitApi'
import {
  archiveEvaluationArchive,
  attachmentUrl,
  createEvaluationArchive,
  deactivateEvaluationAttachment,
  downloadEvaluationAttachment,
  fetchEvaluationArchive,
  fetchEvaluationArchives,
  fetchEvaluationArchiveStats,
  fetchEvaluationAttachments,
  replaceEvaluationAttachment,
  updateEvaluationArchive,
  uploadEvaluationAttachment,
  withdrawEvaluationArchive,
} from '../../evaluationArchiveApi'

const rows = ref([])
const orgOptions = ref([])
const loading = ref(false)
const error = ref('')
const message = ref('')
const modal = ref('')
const selected = ref(null)
const attachments = ref([])
const showHistory = ref(false)
const filters = reactive({
  orgId: '', year: '', type: '', grade: '', status: '', keyword: '',
})
const pager = reactive({ page: 1, size: 8, total: 0, totalPages: 0 })
const stats = reactive({ total: 0, drafts: 0, archived: 0, complete: 0, attachments: 0 })
const form = reactive(emptyForm())
const uploadForm = reactive({ category: 'REPORT', remarks: '', file: null, replaceId: null })

const typeLabels = {
  ANNUAL_COMPREHENSIVE: '年度综合评估', SPECIAL: '专项评估', AD_HOC: '不定期评估',
}
const gradeLabels = {
  EXCELLENT: '优秀', GOOD: '良好', QUALIFIED: '合格',
  UNQUALIFIED: '不合格', UNRATED: '未评定',
}
const accessLabels = { PUBLIC: '公开', DEPARTMENT: '部门级', AUTHORIZED: '授权可见' }
const categoryLabels = {
  REPORT: '评估报告', SELF_ASSESSMENT: '自评材料',
  RECTIFICATION_LEDGER: '整改台账', REVIEW_RECORD: '复核记录', OTHER: '其他材料',
}

function emptyForm() {
  return {
    id: null, orgUnitId: '', evaluationYear: new Date().getFullYear(),
    evaluationType: 'ANNUAL_COMPREHENSIVE', evaluationGrade: 'UNRATED',
    description: '', accessLevel: 'DEPARTMENT', rowVersion: null,
  }
}

async function load(page = pager.page) {
  loading.value = true
  error.value = ''
  pager.page = page
  try {
    const [data, summary] = await Promise.all([
      fetchEvaluationArchives({ ...filters, page, size: pager.size }),
      fetchEvaluationArchiveStats(),
    ])
    rows.value = data.items
    Object.assign(pager, { total: data.total, totalPages: data.totalPages })
    Object.assign(stats, summary)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadOrganizations() {
  const tree = await fetchOrgTree({ includeInactive: false })
  const flatten = (nodes) => nodes.flatMap((node) => [node, ...flatten(node.children || [])])
  orgOptions.value = flatten(tree).filter((item) => !['ROOT', 'GROUP'].includes(item.unitType))
}

function resetFilters() {
  Object.assign(filters, { orgId: '', year: '', type: '', grade: '', status: '', keyword: '' })
  load(1)
}

function openCreate() {
  Object.assign(form, emptyForm())
  modal.value = 'form'
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    orgUnitId: row.org_unit_id,
    evaluationYear: row.evaluation_year,
    evaluationType: row.evaluation_type,
    evaluationGrade: row.evaluation_grade,
    description: row.description || '',
    accessLevel: row.access_level,
    rowVersion: row.row_version,
  })
  modal.value = 'form'
}

async function save() {
  error.value = ''
  try {
    const body = { ...form }
    if (form.id) await updateEvaluationArchive(form.id, body)
    else await createEvaluationArchive(body)
    message.value = form.id ? '档案已更新' : '档案已创建'
    modal.value = ''
    await load(1)
  } catch (e) {
    error.value = e.message
  }
}

async function openDetail(row) {
  try {
    selected.value = await fetchEvaluationArchive(row.id)
    modal.value = 'detail'
  } catch (e) { error.value = e.message }
}

async function openAttachments(row) {
  try {
    selected.value = await fetchEvaluationArchive(row.id)
    showHistory.value = false
    await loadAttachments()
    Object.assign(uploadForm, { category: 'REPORT', remarks: '', file: null, replaceId: null })
    modal.value = 'attachments'
  } catch (e) { error.value = e.message }
}

async function loadAttachments() {
  attachments.value = await fetchEvaluationAttachments(selected.value.id, showHistory.value)
}

async function doArchive(row) {
  if (!window.confirm(`确认归档 ${row.archive_no}？归档后档案将只读。`)) return
  try {
    await archiveEvaluationArchive(row.id, row.row_version)
    message.value = '档案已归档'
    await load()
  } catch (e) { error.value = e.message }
}

async function doWithdraw(row) {
  const reason = window.prompt('请输入撤回原因')
  if (!reason?.trim()) return
  try {
    await withdrawEvaluationArchive(row.id, row.row_version, reason.trim())
    message.value = '档案已撤回为草稿'
    await load()
  } catch (e) { error.value = e.message }
}

function chooseFile(event) {
  uploadForm.file = event.target.files?.[0] || null
}

function prepareReplace(item) {
  uploadForm.replaceId = item.id
  uploadForm.category = item.category
  uploadForm.remarks = ''
  uploadForm.file = null
}

async function submitFile() {
  if (!uploadForm.file) {
    error.value = '请选择文件'
    return
  }
  try {
    if (uploadForm.replaceId) {
      await replaceEvaluationAttachment(
        selected.value.id, uploadForm.replaceId, uploadForm.remarks, uploadForm.file,
      )
      message.value = '附件已替换，历史版本已保留'
    } else {
      await uploadEvaluationAttachment(
        selected.value.id, uploadForm.category, uploadForm.remarks, uploadForm.file,
      )
      message.value = '附件已上传'
    }
    Object.assign(uploadForm, {
      category: 'REPORT', remarks: '', file: null, replaceId: null,
    })
    selected.value = await fetchEvaluationArchive(selected.value.id)
    await loadAttachments()
    await load()
  } catch (e) { error.value = e.message }
}

async function deactivate(item) {
  if (!window.confirm(`确认停用附件“${item.original_name}”？文件和历史记录仍会保留。`)) return
  try {
    await deactivateEvaluationAttachment(selected.value.id, item.id)
    message.value = '附件已逻辑停用'
    selected.value = await fetchEvaluationArchive(selected.value.id)
    await loadAttachments()
    await load()
  } catch (e) { error.value = e.message }
}

async function download(item) {
  try {
    await downloadEvaluationAttachment(item.id, item.original_name)
  } catch (e) { error.value = e.message }
}

function preview(item) {
  if (!['pdf', 'jpg', 'jpeg', 'png'].includes((item.extension || '').toLowerCase())) {
    error.value = '该文件类型不支持在线预览，请下载后查看'
    return
  }
  window.open(attachmentUrl(item.id, 'preview'), '_blank', 'noopener')
}

function completenessLabel(row) {
  return row.completenessStatus === 'COMPLETE'
    ? '完整' : row.completenessStatus === 'EMPTY' ? '无标准附件' : '部分完整'
}

const pages = computed(() => {
  const start = Math.max(1, pager.page - 2)
  return Array.from({ length: Math.min(5, pager.totalPages - start + 1) }, (_, i) => start + i)
})

onMounted(async () => {
  try {
    await Promise.all([loadOrganizations(), load(1)])
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <section class="page active archive-page">
    <div class="alert alert-info">
      对历年评估报告、自评材料、整改台账和复核记录电子化归档；权限级别仅作为业务提示。
    </div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="stat-grid archive-stats">
      <div class="stat-card"><div class="num">{{ stats.total }}</div><div class="label">档案总数</div></div>
      <div class="stat-card"><div class="num orange">{{ stats.drafts }}</div><div class="label">草稿</div></div>
      <div class="stat-card"><div class="num green">{{ stats.archived }}</div><div class="label">已归档</div></div>
      <div class="stat-card"><div class="num">{{ stats.complete }}</div><div class="label">完整档案</div><div class="sub">附件 {{ stats.attachments }} 份</div></div>
    </div>

    <div class="card">
      <div class="card-header">
        <h3><span class="icon">🗂️</span>历史评估档案检索</h3>
        <button class="btn btn-primary" @click="openCreate">＋ 新建档案</button>
      </div>
      <div class="search-bar archive-search">
        <div class="form-item"><label>机构</label><select v-model="filters.orgId"><option value="">全部机构</option><option v-for="org in orgOptions" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></div>
        <div class="form-item"><label>年度</label><input v-model="filters.year" type="number" placeholder="全部年度"></div>
        <div class="form-item"><label>评估类型</label><select v-model="filters.type"><option value="">全部类型</option><option v-for="(label, code) in typeLabels" :key="code" :value="code">{{ label }}</option></select></div>
        <div class="form-item"><label>评估等级</label><select v-model="filters.grade"><option value="">全部等级</option><option v-for="(label, code) in gradeLabels" :key="code" :value="code">{{ label }}</option></select></div>
        <div class="form-item"><label>状态</label><select v-model="filters.status"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="ARCHIVED">已归档</option></select></div>
        <div class="form-item keyword"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="档案号、机构、说明、附件名" @keyup.enter="load(1)"></div>
        <button class="btn btn-primary" @click="load(1)">🔍 检索</button>
        <button class="btn btn-outline" @click="resetFilters">重置</button>
      </div>
    </div>

    <div class="archive-grid">
      <article v-for="row in rows" :key="row.id" class="card archive-card">
        <div class="archive-card-head">
          <div><span class="code-badge">{{ row.archive_no }}</span><h3>{{ row.unit_name }}{{ typeLabels[row.evaluation_type] }}档案</h3></div>
          <span class="tag" :class="row.status === 'ARCHIVED' ? 'tag-success' : 'tag-warning'">{{ row.status === 'ARCHIVED' ? '已归档' : '草稿' }}</span>
        </div>
        <div class="archive-meta">
          <span>{{ row.evaluation_year }}年度</span><span>{{ gradeLabels[row.evaluation_grade] }}</span>
          <span>{{ accessLabels[row.access_level] }}</span>
        </div>
        <p>{{ row.description || '暂无档案说明' }}</p>
        <div class="completeness">
          <div><b>{{ completenessLabel(row) }}</b><span>{{ row.attachment_count }}个当前附件</span></div>
          <div class="progress-bar"><div class="fill fill-blue" :style="{ width: `${row.completenessPercent}%` }"></div></div>
          <small>标准附件完整度 {{ row.completenessPercent }}%</small>
        </div>
        <div class="btn-group">
          <button class="btn btn-sm btn-outline" @click="openDetail(row)">查看</button>
          <button v-if="row.status === 'DRAFT'" class="btn btn-sm btn-outline" @click="openEdit(row)">编辑</button>
          <button class="btn btn-sm btn-outline" @click="openAttachments(row)">附件</button>
          <button v-if="row.status === 'DRAFT'" class="btn btn-sm btn-primary" @click="doArchive(row)">归档</button>
          <button v-else class="btn btn-sm btn-outline" @click="doWithdraw(row)">撤回</button>
        </div>
      </article>
      <div v-if="!loading && !rows.length" class="card empty">暂无真实档案数据，请点击“新建档案”。</div>
    </div>

    <div v-if="pager.totalPages > 1" class="btn-group pager-group">
      <button class="btn btn-outline" :disabled="pager.page <= 1" @click="load(pager.page - 1)">上一页</button>
      <button v-for="pageNo in pages" :key="pageNo" class="btn" :class="pageNo === pager.page ? 'btn-primary' : 'btn-outline'" @click="load(pageNo)">{{ pageNo }}</button>
      <button class="btn btn-outline" :disabled="pager.page >= pager.totalPages" @click="load(pager.page + 1)">下一页</button>
    </div>

    <div v-if="modal" class="modal-mask" @click.self="modal = ''">
      <div class="modal-card" :class="{ wide: modal === 'attachments' }">
        <div class="card-header"><h3>{{ modal === 'form' ? (form.id ? '编辑档案' : '新建档案') : modal === 'detail' ? '档案详情' : '附件管理' }}</h3><button class="btn btn-sm btn-outline" @click="modal = ''">关闭</button></div>

        <form v-if="modal === 'form'" class="form-grid" @submit.prevent="save">
          <label>机构<select v-model="form.orgUnitId" required :disabled="!!form.id"><option value="">请选择</option><option v-for="org in orgOptions" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></label>
          <label>年度<input v-model.number="form.evaluationYear" type="number" required :disabled="!!form.id"></label>
          <label>评估类型<select v-model="form.evaluationType" :disabled="!!form.id"><option v-for="(label, code) in typeLabels" :key="code" :value="code">{{ label }}</option></select></label>
          <label>评估等级<select v-model="form.evaluationGrade"><option v-for="(label, code) in gradeLabels" :key="code" :value="code">{{ label }}</option></select></label>
          <label>权限级别<select v-model="form.accessLevel"><option v-for="(label, code) in accessLabels" :key="code" :value="code">{{ label }}</option></select></label>
          <label class="span-2">档案说明<textarea v-model="form.description" rows="5" placeholder="评估结论、问题和整改情况说明"></textarea></label>
          <div class="btn-group span-2"><button class="btn btn-primary" type="submit">保存</button><button class="btn btn-outline" type="button" @click="modal = ''">取消</button></div>
        </form>

        <div v-else-if="modal === 'detail' && selected" class="detail-list">
          <div><b>档案编号</b><span>{{ selected.archive_no }}</span></div><div><b>机构</b><span>{{ selected.unit_name }}</span></div>
          <div><b>年度/类型</b><span>{{ selected.evaluation_year }} / {{ typeLabels[selected.evaluation_type] }}</span></div><div><b>等级</b><span>{{ gradeLabels[selected.evaluation_grade] }}</span></div>
          <div><b>权限级别</b><span>{{ accessLabels[selected.access_level] }}（仅提示）</span></div><div><b>状态</b><span>{{ selected.status === 'ARCHIVED' ? '已归档' : '草稿' }}</span></div>
          <div><b>创建人</b><span>{{ selected.created_by_name }} · {{ selected.created_at }}</span></div><div><b>修改人</b><span>{{ selected.updated_by_name }} · {{ selected.updated_at }}</span></div>
          <div v-if="selected.archived_at"><b>归档人</b><span>{{ selected.archived_by_name }} · {{ selected.archived_at }}</span></div>
          <div class="span-2"><b>说明</b><span>{{ selected.description || '无' }}</span></div>
        </div>

        <div v-else-if="modal === 'attachments' && selected">
          <div v-if="selected.status === 'DRAFT'" class="attachment-upload">
            <select v-model="uploadForm.category" :disabled="!!uploadForm.replaceId"><option v-for="(label, code) in categoryLabels" :key="code" :value="code">{{ label }}</option></select>
            <input type="file" accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip" @change="chooseFile">
            <input v-model="uploadForm.remarks" placeholder="附件备注">
            <button class="btn btn-primary" @click="submitFile">{{ uploadForm.replaceId ? '上传替换版本' : '上传附件' }}</button>
            <button v-if="uploadForm.replaceId" class="btn btn-outline" @click="uploadForm.replaceId = null">取消替换</button>
          </div>
          <div class="history-switch"><label><input v-model="showHistory" type="checkbox" @change="loadAttachments"> 显示历史版本</label><span>归档状态下附件只读</span></div>
          <table>
            <thead><tr><th>分类</th><th>文件名</th><th>版本</th><th>状态</th><th>上传人/时间</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in attachments" :key="item.id">
              <td>{{ categoryLabels[item.category] }}</td><td>{{ item.original_name }}</td><td>V{{ item.version_no }}</td>
              <td><span class="tag" :class="item.is_current && item.attachment_status === 'ACTIVE' ? 'tag-success' : 'tag-default'">{{ item.is_current && item.attachment_status === 'ACTIVE' ? '当前有效' : '历史版本' }}</span></td>
              <td>{{ item.uploaded_by_name }}<br><small>{{ item.created_at }}</small></td>
              <td><div class="btn-group"><button class="btn btn-sm btn-outline" @click="preview(item)">预览</button><button class="btn btn-sm btn-outline" @click="download(item)">下载</button><button v-if="selected.status === 'DRAFT' && item.is_current && item.attachment_status === 'ACTIVE'" class="btn btn-sm btn-outline" @click="prepareReplace(item)">替换</button><button v-if="selected.status === 'DRAFT' && item.is_current && item.attachment_status === 'ACTIVE'" class="btn btn-sm btn-danger" @click="deactivate(item)">停用</button></div></td>
            </tr></tbody>
          </table>
          <div v-if="!attachments.length" class="empty">暂无附件</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.archive-stats{grid-template-columns:repeat(4,minmax(0,1fr));margin-bottom:18px}
.archive-search{display:flex;flex-wrap:wrap;align-items:flex-end;gap:12px}.archive-search .keyword{flex:1;min-width:220px}
.archive-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.archive-card{margin:0}.archive-card-head{display:flex;justify-content:space-between;gap:16px}.archive-card-head h3{font-size:17px;margin:10px 0 0}
.archive-meta{display:flex;gap:8px;margin:14px 0}.archive-meta span{background:#f4f7fb;border-radius:6px;padding:5px 9px;font-size:13px}
.archive-card p{min-height:42px;color:#64748b}.completeness>div:first-child{display:flex;justify-content:space-between;margin-bottom:8px}.completeness small{color:#64748b}
.modal-mask{position:fixed;inset:0;background:rgba(15,23,42,.5);display:flex;align-items:center;justify-content:center;z-index:1000;padding:24px}
.modal-card{background:#fff;border-radius:14px;padding:20px;width:min(680px,96vw);max-height:90vh;overflow:auto}.modal-card.wide{width:min(1080px,96vw)}
.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.form-grid label{display:flex;flex-direction:column;gap:7px;font-weight:600}.span-2{grid-column:1/-1}
.detail-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.detail-list>div{background:#f8fafc;border-radius:8px;padding:12px;display:flex;flex-direction:column;gap:5px}
.attachment-upload{display:grid;grid-template-columns:160px 1fr 1fr auto auto;gap:10px;margin-bottom:16px}.history-switch{display:flex;justify-content:space-between;margin:12px 0;color:#64748b}
.empty{text-align:center;color:#94a3b8;padding:32px}.pager-group{justify-content:center;margin-top:18px}
@media(max-width:900px){.archive-grid,.form-grid,.detail-list{grid-template-columns:1fr}.archive-stats{grid-template-columns:repeat(2,1fr)}.attachment-upload{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
