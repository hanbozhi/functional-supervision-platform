<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { performanceApi } from '../../performanceApi'

const records = ref([])
const corrections = ref([])
const selected = ref(null)
const message = ref('')
const messageType = ref('success')
const showForm = ref(false)
const materialInput = ref(null)
const form = reactive({ id: null, rawRecordId: null, correctionScope: 'ALL', correctedGrade: '', correctedKeyWorkScore: null, correctedLeadershipRating: '', correctionReason: '', rowVersion: null })
const statusLabels = { DRAFT: '草稿', SUBMITTED: '待确认', CONFIRMED: '已确认', REJECTED: '已驳回' }
const confirmedCount = computed(() => corrections.value.filter(x => x.status === 'CONFIRMED').length)
function notice(text, type = 'success') { message.value = text; messageType.value = type }

async function load() {
  try { [records.value, corrections.value] = await Promise.all([performanceApi.records(), performanceApi.corrections()]) }
  catch (error) { notice(error.message, 'error') }
}
function create(item) {
  Object.assign(form, {
    id: null, rawRecordId: item.id, correctionScope: 'ALL', correctedGrade: item.performance_grade || '',
    correctedKeyWorkScore: item.key_work_score, correctedLeadershipRating: item.leadership_rating || '',
    correctionReason: '', rowVersion: null,
  })
  showForm.value = true
}
async function edit(item) {
  const detail = await performanceApi.correction(item.id)
  Object.assign(form, {
    id: detail.id, rawRecordId: detail.raw_record_id, correctionScope: detail.correction_scope,
    correctedGrade: detail.corrected_grade || '', correctedKeyWorkScore: detail.corrected_key_work_score,
    correctedLeadershipRating: detail.corrected_leadership_rating || '', correctionReason: detail.correction_reason,
    rowVersion: detail.row_version,
  })
  showForm.value = true
}
async function save() {
  try {
    const body = { ...form }
    const result = form.id ? await performanceApi.updateCorrection(form.id, body) : await performanceApi.createCorrection(body)
    showForm.value = false
    notice('修正草稿已保存')
    await load()
    selected.value = await performanceApi.correction(result.id)
  } catch (error) { notice(error.message, 'error') }
}
async function detail(item) {
  try { selected.value = await performanceApi.correction(item.id) } catch (error) { notice(error.message, 'error') }
}
async function submit(item) {
  try { await performanceApi.submitCorrection(item.id, { opinion: '提交组织部二次修正确认', rowVersion: item.row_version }); notice('已提交确认'); await load(); if (selected.value?.id === item.id) selected.value = await performanceApi.correction(item.id) } catch (error) { notice(error.message, 'error') }
}
async function review(item, action) {
  const opinion = window.prompt(action === 'CONFIRM' ? '请输入确认意见' : '请输入驳回意见')
  if (!opinion) return
  try { await performanceApi.reviewCorrection(item.id, { action, opinion, rowVersion: item.row_version }); notice(action === 'CONFIRM' ? '修正已确认并形成有效结果' : '修正已驳回'); await load(); selected.value = await performanceApi.correction(item.id) } catch (error) { notice(error.message, 'error') }
}
async function uploadMaterial(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !selected.value) return
  const remarks = window.prompt('材料说明（可选）') || ''
  try { await performanceApi.uploadMaterial(selected.value.id, file, remarks); selected.value = await performanceApi.correction(selected.value.id); notice('证明材料已上传') } catch (error) { notice(error.message, 'error') }
}
const display = value => value == null || value === '' ? '-' : value
onMounted(load)
</script>

<template>
  <section class="page active">
    <div class="alert alert-info">二次修正不会覆盖原始导入数据；只有“已确认”修正参与当前有效结果计算。</div>
    <div v-if="message" class="alert" :class="messageType === 'error' ? 'alert-danger' : 'alert-success'">{{ message }}</div>
    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ records.length }}</div><div class="label">当前绩效记录</div></div>
      <div class="stat-card"><div class="num orange">{{ corrections.length }}</div><div class="label">修正记录</div></div>
      <div class="stat-card"><div class="num green">{{ confirmedCount }}</div><div class="label">已确认修正</div></div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">🛠️</span>原始结果与当前有效结果</h3><button class="btn btn-outline" @click="load">刷新</button></div>
      <table>
        <thead><tr><th>机构</th><th>年度</th><th>原等次 / 有效等次</th><th>原得分 / 有效得分</th><th>原班子评价 / 有效评价</th><th>结果来源</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!records.length"><td colspan="7">请先在 m2-7 导入绩效数据</td></tr>
          <tr v-for="item in records" :key="item.id">
            <td>{{ item.unit_name }}</td><td>{{ item.evaluation_year }}</td>
            <td>{{ display(item.performance_grade) }} / <b>{{ display(item.effective_grade) }}</b></td>
            <td>{{ display(item.key_work_score) }} / <b>{{ display(item.effective_key_work_score) }}</b></td>
            <td>{{ display(item.leadership_rating) }} / <b>{{ display(item.effective_leadership_rating) }}</b></td>
            <td><span class="tag" :class="item.correction_id ? 'tag-warning' : 'tag-info'">{{ item.correction_id ? '组织部确认修正' : '原始同步' }}</span></td>
            <td><button class="btn btn-sm btn-primary" @click="create(item)">创建修正</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">📜</span>二次修正历史</h3><span class="extra">全过程留痕</span></div>
      <table>
        <thead><tr><th>机构</th><th>年度</th><th>范围</th><th>修正等次</th><th>修正得分</th><th>原因</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!corrections.length"><td colspan="8">暂无修正记录</td></tr>
          <tr v-for="item in corrections" :key="item.id">
            <td>{{ item.unit_name }}</td><td>{{ item.evaluation_year }}</td><td>{{ item.correction_scope }}</td>
            <td>{{ display(item.corrected_grade) }}</td><td>{{ display(item.corrected_key_work_score) }}</td><td>{{ item.correction_reason }}</td>
            <td><span class="tag" :class="item.status === 'CONFIRMED' ? 'tag-success' : item.status === 'REJECTED' ? 'tag-danger' : 'tag-warning'">{{ statusLabels[item.status] }}</span></td>
            <td><button class="btn btn-sm btn-outline" @click="detail(item)">详情</button> <button v-if="['DRAFT','REJECTED'].includes(item.status)" class="btn btn-sm btn-outline" @click="edit(item)">编辑</button> <button v-if="['DRAFT','REJECTED'].includes(item.status)" class="btn btn-sm btn-primary" @click="submit(item)">提交</button> <button v-if="item.status === 'SUBMITTED'" class="btn btn-sm btn-primary" @click="review(item, 'CONFIRM')">确认</button> <button v-if="item.status === 'SUBMITTED'" class="btn btn-sm btn-outline" @click="review(item, 'REJECT')">驳回</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false"><div class="modal"><div class="modal-header"><h3>{{ form.id ? '编辑' : '创建' }}二次修正</h3><button @click="showForm = false">×</button></div>
      <div class="form-grid">
        <div class="form-item"><label>适用范围 *</label><select v-model="form.correctionScope"><option value="ALL">全部绩效字段</option><option value="PERFORMANCE_GRADE">绩效等次</option><option value="KEY_WORK_SCORE">重点工作得分</option><option value="LEADERSHIP_RATING">班子评价</option></select></div>
        <div class="form-item"><label>修正等次</label><select v-model="form.correctedGrade"><option value="">不修正</option><option v-for="grade in ['A','B','C','D','优秀','良好','合格','不合格']" :key="grade">{{ grade }}</option></select></div>
        <div class="form-item"><label>修正得分（0-100）</label><input v-model.number="form.correctedKeyWorkScore" type="number" min="0" max="100"></div>
        <div class="form-item"><label>修正班子评价</label><input v-model.trim="form.correctedLeadershipRating"></div>
        <div class="form-item full"><label>修正原因 *</label><textarea v-model.trim="form.correctionReason" rows="4"></textarea></div>
      </div><div class="btn-group"><button class="btn btn-primary" @click="save">保存草稿</button><button class="btn btn-outline" @click="showForm = false">取消</button></div>
    </div></div>

    <div v-if="selected" class="modal-overlay" @click.self="selected = null"><div class="modal large-modal"><div class="modal-header"><h3>修正详情与证明材料</h3><button @click="selected = null">×</button></div>
      <div class="field-grid"><div class="field-box"><div class="name">机构/年度</div><div class="val">{{ selected.unit_name }} / {{ selected.evaluation_year }}</div></div><div class="field-box"><div class="name">原始得分 → 修正得分</div><div class="val">{{ display(selected.key_work_score) }} → {{ display(selected.corrected_key_work_score) }}</div></div><div class="field-box"><div class="name">状态</div><div class="val">{{ statusLabels[selected.status] }}</div></div><div class="field-box"><div class="name">复核意见</div><div class="val">{{ selected.review_opinion || '-' }}</div></div></div>
      <div v-if="['DRAFT','REJECTED'].includes(selected.status)" class="btn-group"><button class="btn btn-outline" @click="materialInput.click()">上传证明材料</button><input ref="materialInput" type="file" hidden @change="uploadMaterial"></div>
      <h4>证明材料</h4><div v-if="!selected.materials?.length" class="alert alert-info">暂无证明材料</div><div v-for="item in selected.materials" :key="item.attachment_id" class="log-item"><span>{{ item.original_name }}（{{ item.remarks || '无说明' }}）</span><a class="btn btn-sm btn-outline" :href="performanceApi.materialUrl(item.attachment_id)">下载</a></div>
      <h4>状态历史</h4><div v-for="item in selected.history" :key="item.id" class="log-item"><span>{{ item.changed_at }}　{{ item.from_status || '创建' }} → {{ item.to_status }}</span><span>{{ item.operator_name || '张主任' }}：{{ item.opinion || '-' }}</span></div>
    </div></div>
  </section>
</template>

<style scoped>
.modal-overlay{position:fixed;inset:0;background:#0006;display:flex;align-items:center;justify-content:center;z-index:1000}.modal{background:white;border-radius:12px;padding:22px;width:min(720px,92vw);max-height:88vh;overflow:auto}.large-modal{width:min(960px,94vw)}.modal-header{display:flex;justify-content:space-between;align-items:center}.modal-header button{border:0;background:none;font-size:26px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px;margin:18px 0}.full{grid-column:1/-1}h4{margin:18px 0 8px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.full{grid-column:auto}}
</style>
