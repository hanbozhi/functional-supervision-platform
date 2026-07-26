<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { counterpartApi } from '../../counterpartEvaluationApi'

const organizations = ref([])
const rows = ref([])
const filter = ref('')
const message = ref('')
const form = reactive({ id: null, subjectOrgId: '', counterpartOrgId: '', collaborationItem: '', confidence: 100, rowVersion: null })
const confirmed = computed(() => rows.value.filter((row) => row.status === 'CONFIRMED').length)
const suggested = computed(() => rows.value.filter((row) => row.status === 'SUGGESTED').length)
const average = computed(() => rows.value.length ? Math.round(rows.value.reduce((sum, row) => sum + Number(row.confidence || 0), 0) / rows.value.length) : 0)

async function load() {
  try {
    [organizations.value, rows.value] = await Promise.all([counterpartApi.organizations(), counterpartApi.relations(filter.value)])
  } catch (error) { message.value = error.message }
}
function edit(row = null) {
  Object.assign(form, row ? {
    id: row.id, subjectOrgId: row.subject_org_id, counterpartOrgId: row.counterpart_org_id,
    collaborationItem: row.collaboration_item, confidence: row.confidence, rowVersion: row.row_version,
  } : { id: 0, subjectOrgId: '', counterpartOrgId: '', collaborationItem: '', confidence: 100, rowVersion: null })
}
async function save() {
  try {
    const body = { ...form, subjectOrgId: Number(form.subjectOrgId), counterpartOrgId: Number(form.counterpartOrgId), confidence: Number(form.confidence) }
    form.id ? await counterpartApi.updateRelation(form.id, body) : await counterpartApi.createRelation(body)
    message.value = '协作关系已保存'; form.id = null; await load()
  } catch (error) { message.value = error.message }
}
async function verify(row, result) {
  const opinion = window.prompt(result === 'CONFIRMED' ? '请输入确认意见' : '请输入驳回意见')
  if (!opinion) return
  try { await counterpartApi.verifyRelation(row.id, { result, opinion, rowVersion: row.row_version }); await load() } catch (error) { message.value = error.message }
}
async function toggle(row) {
  try { await counterpartApi.relationStatus(row.id, { status: row.status === 'INACTIVE' ? 'CONFIRMED' : 'INACTIVE', rowVersion: row.row_version }); await load() } catch (error) { message.value = error.message }
}
async function suggest() {
  try { const result = await counterpartApi.generateSuggestions(); message.value = `规则匹配新增 ${result.created} 条待核验建议`; await load() } catch (error) { message.value = error.message }
}
onMounted(load)
</script>

<template>
  <section class="page active">
    <div class="alert alert-info">根据已确认三定职责的共享关键词提供可解释建议；建议必须人工确认后才成为正式关系。</div>
    <div v-if="message" class="alert alert-warning">{{ message }}</div>
    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ rows.length }}</div><div class="label">协作关系</div><div class="sub">SQLite真实数据</div></div>
      <div class="stat-card"><div class="num green">{{ confirmed }}</div><div class="label">已确认</div><div class="sub">可用于问卷</div></div>
      <div class="stat-card"><div class="num orange">{{ suggested }}</div><div class="label">待核验建议</div><div class="sub">规则识别</div></div>
      <div class="stat-card"><div class="num">{{ average }}%</div><div class="label">平均置信度</div><div class="sub">仅作人工参考</div></div>
    </div>
    <div class="card">
      <div class="card-header"><h3>🔗 协作关系自动匹配</h3><div class="btn-group"><button class="btn btn-primary" @click="edit()">人工新增</button><button class="btn btn-outline" @click="suggest">规则重新匹配</button></div></div>
      <div class="search-bar"><div class="form-item"><label>状态</label><select v-model="filter" @change="load"><option value="">全部</option><option value="SUGGESTED">待核验</option><option value="CONFIRMED">已确认</option><option value="REJECTED">已驳回</option><option value="INACTIVE">已停用</option></select></div><button class="btn btn-outline" @click="load">刷新</button></div>
      <div class="table-scroll"><table><thead><tr><th>评价主体</th><th>对口机构</th><th>协作事项</th><th>来源</th><th>置信度</th><th>核验状态</th><th>核验意见</th><th>操作</th></tr></thead><tbody>
        <tr v-if="!rows.length"><td colspan="8" class="empty-cell">暂无关系数据</td></tr>
        <tr v-for="row in rows" :key="row.id"><td>{{ row.subject_org_name }}</td><td>{{ row.counterpart_org_name }}</td><td>{{ row.collaboration_item }}</td><td>{{ row.source === 'RULE_SUGGESTION' ? '职责关键词规则' : '人工维护' }}</td><td>{{ row.confidence }}%</td><td><span class="tag tag-info">{{ row.status }}</span></td><td>{{ row.verification_opinion || '-' }}</td><td><div class="btn-group"><button class="btn btn-sm btn-outline" @click="edit(row)">编辑</button><button v-if="row.status === 'SUGGESTED'" class="btn btn-sm btn-primary" @click="verify(row,'CONFIRMED')">确认</button><button v-if="row.status === 'SUGGESTED'" class="btn btn-sm btn-outline" @click="verify(row,'REJECTED')">驳回</button><button v-if="['CONFIRMED','INACTIVE'].includes(row.status)" class="btn btn-sm btn-outline" @click="toggle(row)">{{ row.status === 'INACTIVE' ? '启用' : '停用' }}</button></div></td></tr>
      </tbody></table></div>
    </div>
    <div v-if="form.id !== null" class="evidence-modal show" @click.self="form.id = null"><div class="evidence-panel"><div class="panel-head"><h3>{{ form.id ? '编辑关系' : '新增关系' }}</h3><button class="btn btn-outline" @click="form.id=null">关闭</button></div><div class="panel-body"><div class="search-bar">
      <div class="form-item"><label>评价主体</label><select v-model="form.subjectOrgId"><option value="">请选择</option><option v-for="org in organizations" :key="org.id" :value="org.id">{{ org.unit_name }}</option></select></div>
      <div class="form-item"><label>对口机构</label><select v-model="form.counterpartOrgId"><option value="">请选择</option><option v-for="org in organizations" :key="org.id" :value="org.id">{{ org.unit_name }}</option></select></div>
      <div class="form-item"><label>协作事项</label><input v-model.trim="form.collaborationItem"></div><div class="form-item"><label>置信度</label><input v-model.number="form.confidence" type="number" min="0" max="100"></div>
    </div><button class="btn btn-primary" @click="save">保存</button></div></div></div>
  </section>
</template>
