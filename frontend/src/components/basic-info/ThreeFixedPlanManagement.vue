<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchOrgTree } from '../../orgUnitApi'
import {
  batchUploadPlans, createFieldMapping, createManualPlan, downloadThreeFixedAttachment,
  fetchFieldMappings, fetchThreeFixedPlan, fetchThreeFixedPlans, fetchThreeFixedVersion,
  reparsePlanVersion, reviewPlanVersion, submitPlanVersion, updateFieldMappingStatus,
  updateFieldMapping, updatePlanVersion, uploadPlan,
} from '../../threeFixedApi'

const rows = ref([])
const orgs = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const pager = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const filters = reactive({ orgUnitId: '', keyword: '', status: '', year: '' })
const detail = ref(null)
const planDetail = ref(null)
const editor = reactive({ visible: false, mode: 'manual', orgUnitId: '', rowVersion: null, versionId: null, fields: emptyFields() })
const uploadModal = reactive({ visible: false, mode: 'single', orgUnitId: '', planName: '', file: null, entries: [] })
const reviewModal = reactive({ visible: false, versionId: null, rowVersion: null, result: 'CONFIRMED', opinion: '' })
const mappingModal = reactive({ visible: false, rows: [], form: { id: null, fileType: 'ALL', sourceLabel: '', targetField: 'ORGANIZATION_NAME', sortOrder: 0 } })

const targetFields = [
  ['PLAN_NAME','方案名称'],['DOCUMENT_NO','文号'],['EFFECTIVE_DATE','生效日期'],
  ['ORGANIZATION_NAME','机构名称'],['ORGANIZATION_NATURE','机构性质'],
  ['STAFFING_TYPE','编制类型'],['APPROVED_STAFFING','核定编制'],
  ['MAIN_RESPONSIBILITIES','主要职责'],['INTERNAL_DEPARTMENTS','内设机构'],['REMARKS','备注'],
]

function emptyFields() {
  return { planName: '', documentNo: '', effectiveDate: '', organizationName: '', organizationNature: '', staffingType: '', approvedStaffing: null, mainResponsibilities: '', internalDepartments: '', remarks: '' }
}

function flatten(nodes, result = []) {
  for (const node of nodes || []) {
    if (!['ROOT','GROUP'].includes(node.unitType) && node.status === 'ACTIVE') result.push(node)
    flatten(node.children, result)
  }
  return result
}

async function initialize() {
  loading.value = true
  try {
    const tree = await fetchOrgTree({ includeInactive: false })
    orgs.value = flatten(tree)
    await loadRows(1)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function loadRows(page = pager.page) {
  loading.value = true; error.value = ''
  try {
    const data = await fetchThreeFixedPlans({ ...filters, page, size: pager.size })
    rows.value = data.items
    Object.assign(pager, { page: data.page, size: data.size, total: data.total, totalPages: data.totalPages })
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function resetFilters() {
  Object.assign(filters, { orgUnitId: '', keyword: '', status: '', year: '' })
  loadRows(1)
}

function openManual() {
  Object.assign(editor, { visible: true, mode: 'manual', orgUnitId: filters.orgUnitId || '', rowVersion: null, versionId: null, fields: emptyFields() })
}

async function openPlan(row) {
  try {
    planDetail.value = await fetchThreeFixedPlan(row.id)
  } catch (e) { error.value = e.message }
}

async function openVersion(id) {
  try {
    detail.value = await fetchThreeFixedVersion(id)
    planDetail.value = null
  } catch (e) { error.value = e.message }
}

function editVersion(version) {
  Object.assign(editor, {
    visible: true, mode: 'edit', orgUnitId: version.org_unit_id,
    rowVersion: version.row_version, versionId: version.id,
    fields: {
      planName: version.plan_name || '', documentNo: version.document_no || '',
      effectiveDate: version.effective_date || '', organizationName: version.organization_name || '',
      organizationNature: version.organization_nature || '', staffingType: version.staffing_type || '',
      approvedStaffing: version.approved_staffing, mainResponsibilities: version.main_responsibilities || '',
      internalDepartments: version.internal_departments || '', remarks: version.remarks || '',
    },
  })
}

async function saveEditor() {
  if (!editor.orgUnitId || !editor.fields.planName.trim()) { error.value = '请选择机构并填写方案名称'; return }
  saving.value = true
  try {
    const body = { fields: editor.fields, rowVersion: editor.rowVersion }
    if (editor.mode === 'manual') await createManualPlan({ orgUnitId: Number(editor.orgUnitId), fields: editor.fields })
    else await updatePlanVersion(editor.versionId, body)
    editor.visible = false; detail.value = null
    await loadRows(1)
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

function openUpload(mode) {
  Object.assign(uploadModal, { visible: true, mode, orgUnitId: filters.orgUnitId || '', planName: '', file: null, entries: [] })
}

function chooseSingle(event) { uploadModal.file = event.target.files[0] || null }
function chooseBatch(event) {
  uploadModal.entries = Array.from(event.target.files || []).map((file) => ({ file, orgUnitId: filters.orgUnitId || '', planName: file.name.replace(/\.[^.]+$/, '') }))
}

async function submitUpload() {
  saving.value = true; error.value = ''
  try {
    if (uploadModal.mode === 'single') {
      if (!uploadModal.file || !uploadModal.orgUnitId) throw new Error('请选择机构和文件')
      const form = new FormData()
      form.append('orgUnitId', uploadModal.orgUnitId); form.append('planName', uploadModal.planName); form.append('file', uploadModal.file)
      await uploadPlan(form)
    } else {
      if (!uploadModal.entries.length || uploadModal.entries.some(e => !e.orgUnitId)) throw new Error('请为每个文件选择机构')
      const form = new FormData()
      uploadModal.entries.forEach(entry => form.append('files', entry.file))
      form.append('items', JSON.stringify(uploadModal.entries.map(e => ({ orgUnitId: Number(e.orgUnitId), planName: e.planName }))))
      const results = await batchUploadPlans(form)
      const failed = results.filter(item => !item.success)
      if (failed.length) error.value = `${results.length - failed.length}个成功，${failed.length}个失败：${failed.map(x => x.message).join('；')}`
    }
    uploadModal.visible = false
    await loadRows(1)
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

function openReview(version, result = 'CONFIRMED') {
  Object.assign(reviewModal, { visible: true, versionId: version.id, rowVersion: version.row_version, result, opinion: '' })
}

async function submitReview() {
  if (reviewModal.result === 'RETURNED' && !reviewModal.opinion.trim()) { error.value = '退回时必须填写意见'; return }
  saving.value = true
  try {
    detail.value = await reviewPlanVersion(reviewModal.versionId, { result: reviewModal.result, opinion: reviewModal.opinion, rowVersion: reviewModal.rowVersion })
    reviewModal.visible = false
    await loadRows(1)
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function submitReturned(version) {
  try { detail.value = await submitPlanVersion(version.id, version.row_version); await loadRows(1) } catch (e) { error.value = e.message }
}

async function reparse(version) {
  try { detail.value = await reparsePlanVersion(version.id, version.row_version) } catch (e) { error.value = e.message }
}

async function openMappings() {
  try { mappingModal.rows = await fetchFieldMappings(); mappingModal.visible = true } catch (e) { error.value = e.message }
}

async function addMapping() {
  if (!mappingModal.form.sourceLabel.trim()) { error.value = '请输入来源标签'; return }
  try {
    if (mappingModal.form.id) await updateFieldMapping(mappingModal.form.id, mappingModal.form)
    else await createFieldMapping(mappingModal.form)
    mappingModal.rows = await fetchFieldMappings()
    Object.assign(mappingModal.form, { id: null, fileType: 'ALL', sourceLabel: '', targetField: 'ORGANIZATION_NAME', sortOrder: 0 })
  } catch (e) { error.value = e.message }
}

function editMapping(row) {
  Object.assign(mappingModal.form, { id: row.id, fileType: row.file_type, sourceLabel: row.source_label, targetField: row.target_field, sortOrder: row.sort_order })
}

async function toggleMapping(row) {
  try {
    await updateFieldMappingStatus(row.id, row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
    mappingModal.rows = await fetchFieldMappings()
  } catch (e) { error.value = e.message }
}

function statusLabel(status) {
  return { PENDING_REVIEW: '待复核', RETURNED: '已退回', CONFIRMED: '已入库' }[status] || status
}
function statusClass(status) {
  return status === 'CONFIRMED' ? 'tag-success' : status === 'RETURNED' ? 'tag-danger' : 'tag-warning'
}
function parseLabel(status) {
  return { SUCCESS: '解析成功', PARTIAL: '部分解析', FAILED: '解析失败', NOT_APPLICABLE: '手动录入' }[status] || status
}
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }

onMounted(initialize)
</script>

<template>
  <section class="page active three-fixed-page">
    <div class="alert" :class="error ? 'alert-danger' : 'alert-info'">{{ error || '三定方案按机构独立归集，解析结果需人工复核确认后发布。' }}</div>
    <div class="card knowledge-import">
      <div class="card-header"><h3><span class="icon">📚</span>三定文本导入与知识库更新</h3><span class="extra">SQLite版本管理</span></div>
      <div class="step-strip">
        <div class="step-card"><b>1. 选择机构</b><span>关联单位架构中的启用机构。</span></div>
        <div class="step-card"><b>2. 导入或录入</b><span>支持XLSX、DOCX、PDF及手动录入。</span></div>
        <div class="step-card"><b>3. 字段提取</b><span>简单标签/表格规则，结果允许人工修正。</span></div>
        <div class="step-card"><b>4. 复核发布</b><span>确认后成为机构当前生效版本。</span></div>
      </div>
      <div class="btn-group">
        <button class="btn btn-primary" @click="openUpload('single')">📄 单份上传</button>
        <button class="btn btn-primary" @click="openUpload('batch')">📥 批量导入</button>
        <button class="btn btn-outline" @click="openManual">手动录入</button>
        <button class="btn btn-outline" @click="openMappings">字段映射设置</button>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🕘</span>三定方案与版本历史</h3><span class="extra">共 {{ pager.total }} 个机构方案</span></div>
      <div class="search-bar">
        <div class="form-item"><label>机构</label><select v-model="filters.orgUnitId"><option value="">全部机构</option><option v-for="org in orgs" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></div>
        <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="方案名称、文号" @keyup.enter="loadRows(1)"></div>
        <div class="form-item"><label>状态</label><select v-model="filters.status"><option value="">全部</option><option value="PENDING_REVIEW">待复核</option><option value="RETURNED">已退回</option><option value="CONFIRMED">已入库</option></select></div>
        <div class="form-item"><label>年度</label><input v-model.trim="filters.year" maxlength="4" placeholder="如2026"></div>
        <div class="toolbar-actions"><button class="btn btn-primary" @click="loadRows(1)">查询</button><button class="btn btn-outline" @click="resetFilters">重置</button></div>
      </div>
      <div class="table-scroll">
        <table><thead><tr><th>机构</th><th>方案名称</th><th>版本</th><th>来源</th><th>解析</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="8" class="empty-cell">加载中...</td></tr>
            <tr v-else-if="!rows.length"><td colspan="8" class="empty-cell">暂无三定方案</td></tr>
            <tr v-for="row in rows" v-else :key="row.id">
              <td>{{ row.unit_name }}</td><td>{{ row.plan_name }}</td><td><span class="code-badge">{{ row.version_label }}</span></td>
              <td>{{ row.source_type }}</td><td>{{ parseLabel(row.parse_status) }}</td>
              <td><span class="tag" :class="statusClass(row.workflow_status)">{{ statusLabel(row.workflow_status) }}</span></td>
              <td>{{ formatTime(row.updated_at) }}</td>
              <td><button class="btn btn-sm btn-outline" @click="openPlan(row)">详情/版本</button><button class="btn btn-sm btn-primary" @click="openVersion(row.latest_version_id)">处理</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="btn-group pager-group"><button class="btn btn-outline" :disabled="pager.page <= 1" @click="loadRows(pager.page - 1)">上一页</button><span class="table-summary">第 {{ pager.page }} / {{ pager.totalPages || 1 }} 页</span><button class="btn btn-outline" :disabled="pager.page >= pager.totalPages" @click="loadRows(pager.page + 1)">下一页</button></div>
    </div>

    <div v-if="editor.visible" class="org-modal-backdrop" @click.self="editor.visible=false"><div class="org-modal">
      <div class="card-header"><h3>{{ editor.mode === 'manual' ? '手动录入三定方案' : '编辑结构化字段' }}</h3><button class="btn btn-sm btn-outline" @click="editor.visible=false">关闭</button></div>
      <div class="org-form-grid">
        <div v-if="editor.mode==='manual'" class="form-item"><label class="required">机构</label><select v-model="editor.orgUnitId"><option value="">请选择</option><option v-for="org in orgs" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></div>
        <div class="form-item"><label class="required">方案名称</label><input v-model.trim="editor.fields.planName"></div>
        <div class="form-item"><label>文号</label><input v-model.trim="editor.fields.documentNo"></div>
        <div class="form-item"><label>生效日期</label><input v-model="editor.fields.effectiveDate" type="date"></div>
        <div class="form-item"><label>机构名称</label><input v-model.trim="editor.fields.organizationName"></div>
        <div class="form-item"><label>机构性质</label><input v-model.trim="editor.fields.organizationNature"></div>
        <div class="form-item"><label>编制类型</label><input v-model.trim="editor.fields.staffingType"></div>
        <div class="form-item"><label>核定编制</label><input v-model.number="editor.fields.approvedStaffing" type="number" min="0"></div>
        <div class="form-item wide"><label>主要职责</label><textarea v-model.trim="editor.fields.mainResponsibilities"></textarea></div>
        <div class="form-item wide"><label>内设机构</label><textarea v-model.trim="editor.fields.internalDepartments"></textarea></div>
        <div class="form-item wide"><label>备注</label><textarea v-model.trim="editor.fields.remarks"></textarea></div>
      </div>
      <div class="btn-group org-modal-actions"><button class="btn btn-primary" :disabled="saving" @click="saveEditor">保存</button><button class="btn btn-outline" @click="editor.visible=false">取消</button></div>
    </div></div>

    <div v-if="uploadModal.visible" class="org-modal-backdrop" @click.self="uploadModal.visible=false"><div class="org-modal">
      <div class="card-header"><h3>{{ uploadModal.mode==='single' ? '单份文件上传' : '批量文件导入' }}</h3><button class="btn btn-sm btn-outline" @click="uploadModal.visible=false">关闭</button></div>
      <p class="org-modal-hint">支持.xlsx、.docx、.pdf；单文件10MB，批量最多20个且总计50MB。</p>
      <template v-if="uploadModal.mode==='single'">
        <div class="org-form-grid"><div class="form-item"><label class="required">机构</label><select v-model="uploadModal.orgUnitId"><option value="">请选择</option><option v-for="org in orgs" :key="org.id" :value="org.id">{{ org.unitName }}</option></select></div><div class="form-item"><label>方案名称</label><input v-model.trim="uploadModal.planName" placeholder="不填则使用解析结果或文件名"></div><div class="form-item wide"><label class="required">文件</label><input type="file" accept=".xlsx,.docx,.pdf" @change="chooseSingle"></div></div>
      </template>
      <template v-else>
        <div class="form-item"><label class="required">选择多个文件</label><input type="file" multiple accept=".xlsx,.docx,.pdf" @change="chooseBatch"></div>
        <div v-for="(entry,index) in uploadModal.entries" :key="index" class="batch-file-row"><span>{{ entry.file.name }}</span><select v-model="entry.orgUnitId"><option value="">选择机构</option><option v-for="org in orgs" :key="org.id" :value="org.id">{{ org.unitName }}</option></select><input v-model.trim="entry.planName" placeholder="方案名称"></div>
      </template>
      <div class="btn-group org-modal-actions"><button class="btn btn-primary" :disabled="saving" @click="submitUpload">开始导入</button><button class="btn btn-outline" @click="uploadModal.visible=false">取消</button></div>
    </div></div>

    <div v-if="planDetail" class="org-modal-backdrop" @click.self="planDetail=null"><div class="org-modal">
      <div class="card-header"><h3>{{ planDetail.unit_name }} · 版本历史</h3><button class="btn btn-sm btn-outline" @click="planDetail=null">关闭</button></div>
      <div class="version-list"><button v-for="v in planDetail.versions" :key="v.id" class="version-item version-button" @click="openVersion(v.id)"><span><b>{{ v.version_label }} {{ v.plan_name }}</b>　{{ formatTime(v.updated_at) }}</span><span class="tag" :class="statusClass(v.workflow_status)">{{ statusLabel(v.workflow_status) }}</span></button></div>
    </div></div>

    <div v-if="detail" class="org-modal-backdrop" @click.self="detail=null"><div class="org-modal wide-modal">
      <div class="card-header"><h3>{{ detail.version_label }} · {{ detail.plan_name }}</h3><button class="btn btn-sm btn-outline" @click="detail=null">关闭</button></div>
      <div class="alert alert-info">解析状态：{{ parseLabel(detail.parse_status) }}　|　流程状态：{{ statusLabel(detail.workflow_status) }}</div>
      <div class="org-detail-grid"><div><span>机构</span><b>{{ detail.unit_name }}</b></div><div><span>文号</span><b>{{ detail.document_no || '-' }}</b></div><div><span>机构性质</span><b>{{ detail.organization_nature || '-' }}</b></div><div><span>核定编制</span><b>{{ detail.approved_staffing ?? '-' }}</b></div><div><span>创建人</span><b>{{ detail.created_by_name }}</b></div><div><span>复核意见</span><b>{{ detail.review_opinion || '-' }}</b></div></div>
      <h4 class="org-history-title">解析结果</h4>
      <div class="table-scroll"><table><thead><tr><th>字段</th><th>来源标签</th><th>提取值</th><th>人工修正值</th><th>方式/置信度</th></tr></thead><tbody><tr v-for="item in detail.parseResults" :key="item.id"><td>{{ item.field_code }}</td><td>{{ item.source_label }}</td><td>{{ item.extracted_value }}</td><td>{{ item.corrected_value || '-' }}</td><td>{{ item.parse_method }}/{{ item.confidence_code }}</td></tr><tr v-if="!detail.parseResults?.length"><td colspan="5" class="empty-cell">无自动解析字段，可人工编辑</td></tr></tbody></table></div>
      <div class="btn-group org-modal-actions">
        <button v-if="detail.workflow_status!=='CONFIRMED'" class="btn btn-outline" @click="editVersion(detail)">编辑字段</button>
        <button v-if="detail.workflow_status!=='CONFIRMED' && detail.attachments?.length" class="btn btn-outline" @click="reparse(detail)">重新解析</button>
        <button v-if="detail.workflow_status==='PENDING_REVIEW'" class="btn btn-primary" @click="openReview(detail,'CONFIRMED')">确认入库</button>
        <button v-if="detail.workflow_status==='PENDING_REVIEW'" class="btn btn-outline" @click="openReview(detail,'RETURNED')">退回</button>
        <button v-if="detail.workflow_status==='RETURNED'" class="btn btn-primary" @click="submitReturned(detail)">重新提交</button>
        <button v-for="file in detail.attachments" :key="file.id" class="btn btn-outline" @click="downloadThreeFixedAttachment(file.id,file.original_name)">下载附件</button>
      </div>
    </div></div>

    <div v-if="reviewModal.visible" class="org-modal-backdrop" @click.self="reviewModal.visible=false"><div class="org-modal compact"><div class="card-header"><h3>{{ reviewModal.result==='CONFIRMED'?'确认入库':'退回修改' }}</h3></div><div class="form-item"><label :class="{required:reviewModal.result==='RETURNED'}">复核意见</label><textarea v-model.trim="reviewModal.opinion"></textarea></div><div class="btn-group org-modal-actions"><button class="btn btn-primary" @click="submitReview">提交</button><button class="btn btn-outline" @click="reviewModal.visible=false">取消</button></div></div></div>

    <div v-if="mappingModal.visible" class="org-modal-backdrop" @click.self="mappingModal.visible=false"><div class="org-modal">
      <div class="card-header"><h3>字段映射设置</h3><button class="btn btn-sm btn-outline" @click="mappingModal.visible=false">关闭</button></div>
      <div class="mapping-add"><select v-model="mappingModal.form.fileType"><option value="ALL">全部类型</option><option>XLSX</option><option>DOCX</option><option>PDF</option></select><input v-model.trim="mappingModal.form.sourceLabel" placeholder="来源标签"><select v-model="mappingModal.form.targetField"><option v-for="item in targetFields" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select><button class="btn btn-primary" @click="addMapping">{{ mappingModal.form.id ? '保存修改' : '新增映射' }}</button></div>
      <div class="table-scroll"><table><thead><tr><th>文件类型</th><th>来源标签</th><th>目标字段</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in mappingModal.rows" :key="row.id"><td>{{ row.file_type }}</td><td>{{ row.source_label }}</td><td>{{ row.target_field }}</td><td>{{ row.status==='ACTIVE'?'启用':'停用' }}</td><td><button class="btn btn-sm btn-outline" @click="editMapping(row)">编辑</button><button class="btn btn-sm btn-outline" @click="toggleMapping(row)">{{ row.status==='ACTIVE'?'停用':'启用' }}</button></td></tr></tbody></table></div>
    </div></div>
  </section>
</template>
