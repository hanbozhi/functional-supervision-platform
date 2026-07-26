<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { performanceApi } from '../../performanceApi'

const loading = ref(false)
const message = ref('')
const batches = ref([])
const records = ref([])
const mappings = ref([])
const selectedBatch = ref(null)
const importInput = ref(null)
const year = ref('')
const showMapping = ref(false)
const mappingForm = reactive({ id: null, sourceField: '', targetField: 'ORG_CODE', required: false, sortOrder: 0, rowVersion: null })
const targets = [
  ['ORG_CODE', '机构编码'], ['ORG_NAME', '机构名称'], ['YEAR', '年度'],
  ['PERFORMANCE_GRADE', '绩效等次'], ['KEY_WORK_SCORE', '重点工作得分'],
  ['LEADERSHIP_RATING', '班子评价'], ['REMARKS', '备注'],
]
const stats = computed(() => ({
  batches: batches.value.length,
  success: batches.value.reduce((n, item) => n + Number(item.success_rows || 0), 0),
  failed: batches.value.reduce((n, item) => n + Number(item.failed_rows || 0), 0),
  mappings: mappings.value.filter((item) => item.status === 'ACTIVE').length,
}))

async function load() {
  loading.value = true
  try {
    const [batchData, mappingData, recordData] = await Promise.all([
      performanceApi.batches(), performanceApi.mappings(), performanceApi.records(year.value),
    ])
    batches.value = batchData
    mappings.value = mappingData
    records.value = recordData
  } catch (error) { message.value = error.message } finally { loading.value = false }
}
async function filterRecords() {
  try { records.value = await performanceApi.records(year.value) } catch (error) { message.value = error.message }
}
async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  loading.value = true
  try {
    selectedBatch.value = await performanceApi.uploadImport(file)
    message.value = `导入完成：成功 ${selectedBatch.value.success_rows} 条，失败 ${selectedBatch.value.failed_rows} 条`
    await load()
    selectedBatch.value = await performanceApi.batch(selectedBatch.value.id)
  } catch (error) { message.value = error.message } finally { loading.value = false }
}
async function openBatch(id) {
  try { selectedBatch.value = await performanceApi.batch(id) } catch (error) { message.value = error.message }
}
function editMapping(item) {
  Object.assign(mappingForm, {
    id: item.id, sourceField: item.source_field, targetField: item.target_field,
    required: Boolean(item.required), sortOrder: item.sort_order, rowVersion: item.row_version,
  })
  showMapping.value = true
}
function newMapping() {
  Object.assign(mappingForm, { id: null, sourceField: '', targetField: 'ORG_CODE', required: false, sortOrder: mappings.value.length + 1, rowVersion: null })
  showMapping.value = true
}
async function saveMapping() {
  try {
    const body = { ...mappingForm }
    if (mappingForm.id) await performanceApi.updateMapping(mappingForm.id, body)
    else await performanceApi.createMapping(body)
    showMapping.value = false
    mappings.value = await performanceApi.mappings()
  } catch (error) { message.value = error.message }
}
async function toggleMapping(item) {
  try {
    await performanceApi.mappingStatus(item.id, { status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', rowVersion: item.row_version })
    mappings.value = await performanceApi.mappings()
  } catch (error) { message.value = error.message }
}
function statusText(status) {
  return ({ COMPLETED: '成功', PARTIAL_FAILED: '部分失败', FAILED: '失败', ACTIVE: '启用', INACTIVE: '停用' })[status] || status
}
function downloadTemplate() { window.location.href = performanceApi.templateUrl }
onMounted(load)
</script>

<template>
  <section class="page active">
    <div class="alert alert-info">通过本地 XLSX 模拟组织部绩效数据同步；原始导入记录永久保留，机构编码是唯一匹配依据。</div>
    <div v-if="message" class="alert" :class="message.includes('失败') ? 'alert-warning' : 'alert-success'">{{ message }}</div>

    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ stats.batches }}</div><div class="label">导入批次</div></div>
      <div class="stat-card"><div class="num green">{{ stats.success }}</div><div class="label">成功记录</div></div>
      <div class="stat-card"><div class="num red">{{ stats.failed }}</div><div class="label">失败记录</div></div>
      <div class="stat-card"><div class="num orange">{{ stats.mappings }}</div><div class="label">启用映射</div></div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🔄</span>绩效数据同步/导入</h3><span class="extra">仅支持 .xlsx，最大 10MB</span></div>
      <div class="btn-group">
        <button class="btn btn-outline" @click="downloadTemplate">下载模板</button>
        <button class="btn btn-primary" :disabled="loading" @click="importInput.click()">重新同步（上传 XLSX）</button>
        <input ref="importInput" type="file" accept=".xlsx" hidden @change="upload">
        <button class="btn btn-outline" @click="load">刷新</button>
      </div>
      <table>
        <thead><tr><th>批次</th><th>文件</th><th>总行</th><th>成功</th><th>失败</th><th>警告</th><th>状态</th><th>导入时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!batches.length"><td colspan="9">暂无真实导入批次</td></tr>
          <tr v-for="item in batches" :key="item.id">
            <td>{{ item.batch_code }}</td><td>{{ item.original_file_name }}</td><td>{{ item.total_rows }}</td>
            <td>{{ item.success_rows }}</td><td>{{ item.failed_rows }}</td><td>{{ item.warning_rows }}</td>
            <td><span class="tag" :class="item.status === 'COMPLETED' ? 'tag-success' : 'tag-warning'">{{ statusText(item.status) }}</span></td>
            <td>{{ item.imported_at }}</td><td><button class="btn btn-sm btn-outline" @click="openBatch(item.id)">校验报告</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">📋</span>当前原始绩效记录</h3><span class="extra">同机构同年度取最新成功批次</span></div>
      <div class="search-bar"><div class="form-item"><label>年度</label><input v-model="year" type="number" placeholder="全部年度" @keyup.enter="filterRecords"></div><button class="btn btn-primary" @click="filterRecords">查询</button><button class="btn btn-outline" @click="year = ''; filterRecords()">重置</button></div>
      <table>
        <thead><tr><th>机构编码</th><th>机构</th><th>年度</th><th>绩效等次</th><th>重点工作得分</th><th>班子评价</th><th>导入警告</th></tr></thead>
        <tbody>
          <tr v-if="!records.length"><td colspan="7">暂无已导入数据</td></tr>
          <tr v-for="item in records" :key="item.id">
            <td>{{ item.org_code }}</td><td>{{ item.unit_name }}</td><td>{{ item.evaluation_year }}</td><td>{{ item.performance_grade }}</td>
            <td>{{ item.key_work_score ?? '-' }}</td><td>{{ item.leadership_rating || '-' }}</td><td>{{ item.warning_message || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🔗</span>来源字段映射</h3><button class="btn btn-primary" @click="newMapping">新增映射</button></div>
      <table>
        <thead><tr><th>来源字段</th><th>目标字段</th><th>必填</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in mappings" :key="item.id"><td>{{ item.source_field }}</td><td>{{ targets.find(x => x[0] === item.target_field)?.[1] || item.target_field }}</td><td>{{ item.required ? '是' : '否' }}</td><td>{{ item.sort_order }}</td><td>{{ statusText(item.status) }}</td><td><button class="btn btn-sm btn-outline" @click="editMapping(item)">编辑</button> <button class="btn btn-sm btn-outline" @click="toggleMapping(item)">{{ item.status === 'ACTIVE' ? '停用' : '启用' }}</button></td></tr></tbody>
      </table>
    </div>

    <div v-if="selectedBatch" class="modal-overlay" @click.self="selectedBatch = null">
      <div class="modal large-modal"><div class="modal-header"><h3>批次校验报告</h3><button @click="selectedBatch = null">×</button></div>
        <div class="alert alert-info">成功 {{ selectedBatch.success_rows }}，失败 {{ selectedBatch.failed_rows }}，警告 {{ selectedBatch.warning_rows }}</div>
        <h4>成功记录</h4><table><thead><tr><th>行号</th><th>机构</th><th>年度</th><th>等次</th><th>警告</th></tr></thead><tbody><tr v-for="row in selectedBatch.records" :key="row.id"><td>{{ row.source_row_number }}</td><td>{{ row.unit_name }}</td><td>{{ row.evaluation_year }}</td><td>{{ row.performance_grade }}</td><td>{{ row.warning_message || '-' }}</td></tr></tbody></table>
        <h4>失败明细</h4><table><thead><tr><th>行号</th><th>机构编码</th><th>原因</th></tr></thead><tbody><tr v-if="!selectedBatch.errors?.length"><td colspan="3">无失败记录</td></tr><tr v-for="row in selectedBatch.errors" :key="row.id"><td>{{ row.source_row_number }}</td><td>{{ row.org_code || '-' }}</td><td>{{ row.error_message }}</td></tr></tbody></table>
      </div>
    </div>

    <div v-if="showMapping" class="modal-overlay" @click.self="showMapping = false">
      <div class="modal"><div class="modal-header"><h3>{{ mappingForm.id ? '编辑' : '新增' }}字段映射</h3><button @click="showMapping = false">×</button></div>
        <div class="form-grid"><div class="form-item"><label>来源字段 *</label><input v-model.trim="mappingForm.sourceField"></div><div class="form-item"><label>目标字段 *</label><select v-model="mappingForm.targetField"><option v-for="item in targets" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select></div><div class="form-item"><label>排序</label><input v-model.number="mappingForm.sortOrder" type="number"></div><label><input v-model="mappingForm.required" type="checkbox"> 必填字段</label></div>
        <div class="btn-group"><button class="btn btn-primary" @click="saveMapping">保存</button><button class="btn btn-outline" @click="showMapping = false">取消</button></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.modal-overlay{position:fixed;inset:0;background:#0006;display:flex;align-items:center;justify-content:center;z-index:1000}.modal{background:white;border-radius:12px;padding:22px;width:min(620px,92vw);max-height:88vh;overflow:auto}.large-modal{width:min(1000px,94vw)}.modal-header{display:flex;justify-content:space-between;align-items:center}.modal-header button{border:0;background:none;font-size:26px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px;margin:18px 0}h4{margin:18px 0 8px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}}
</style>
