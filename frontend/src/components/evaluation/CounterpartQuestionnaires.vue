<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { counterpartApi } from '../../counterpartEvaluationApi'

const batches = ref([])
const relations = ref([])
const selected = ref(null)
const showCreate = ref(false)
const showRecipients = ref(false)
const showLogs = ref(false)
const selectedRelations = ref([])
const logs = ref([])
const message = ref('')
const form = reactive({ batchCode: '', title: '', evaluationYear: new Date().getFullYear(), deadlineAt: '', description: '', dimensionNames: '履职响应时效,业务配合度,职责边界清晰度,联合工作成效' })
const draftQuestions = ref([
  { text: '是否按约定时限响应协作请求', type: 'SCORE', required: true, dimensionIndex: 0 },
  { text: '业务配合和材料质量评价', type: 'SCORE', required: true, dimensionIndex: 1 },
  { text: '职责边界是否清晰', type: 'SCORE', required: true, dimensionIndex: 2 },
  { text: '联合工作是否形成明确成效', type: 'SCORE', required: true, dimensionIndex: 3 },
  { text: '其他意见和改进建议', type: 'TEXT', required: false, dimensionIndex: 3 },
])
const published = computed(() => batches.value.filter((row) => row.status === 'PUBLISHED').length)
const dimensionNames = computed(() => form.dimensionNames.split(/[,，]/).map((name) => name.trim()).filter(Boolean))

async function load() {
  try { [batches.value, relations.value] = await Promise.all([counterpartApi.questionnaires(), counterpartApi.relations('CONFIRMED')]) } catch (error) { message.value = error.message }
}
async function create() {
  try {
    const deadlineAt = form.deadlineAt ? new Date(form.deadlineAt).toISOString() : null
    if (!dimensionNames.value.length || !draftQuestions.value.length) throw new Error('请至少配置一个评价维度和一道题目')
    const dimensions = dimensionNames.value.map((name, index) => ({ code: `D${index + 1}`, name, sortOrder: index + 1 }))
    const questions = draftQuestions.value.map((question, index) => ({
      dimensionCode: `D${Math.min(Number(question.dimensionIndex) || 0, dimensions.length - 1) + 1}`,
      code: `Q${index + 1}`, text: question.text, type: question.type,
      required: question.required, sortOrder: index + 1,
    }))
    const body = {
      ...form, deadlineAt,
      dimensions, questions,
    }
    await counterpartApi.createQuestionnaire(body); showCreate.value = false; message.value = '问卷草稿已创建'; await load()
  } catch (error) { message.value = error.message }
}
async function detail(row) {
  try { selected.value = await counterpartApi.questionnaire(row.id) } catch (error) { message.value = error.message }
}
function chooseRecipients(row) { selected.value = row; selectedRelations.value = []; showRecipients.value = true }
async function addRecipients() {
  try { await counterpartApi.addRecipients(selected.value.id, selectedRelations.value); showRecipients.value = false; await detail(selected.value); await load() } catch (error) { message.value = error.message }
}
async function action(row, type) {
  try {
    if (type === 'publish') await counterpartApi.publish(row.id)
    if (type === 'push') await counterpartApi.push(row.id)
    if (type === 'deadline') await counterpartApi.deadline(row.id)
    if (type === 'close') await counterpartApi.close(row.id)
    message.value = '操作成功'; await load(); if (selected.value?.id === row.id) await detail(row)
  } catch (error) { message.value = error.message }
}
async function copy(row) {
  const code = window.prompt('新批次编码', `${row.batch_code}-COPY`)
  if (!code) return
  try { await counterpartApi.copyQuestionnaire(row.id, { batchCode: code, title: `${row.title}（副本）`, evaluationYear: row.evaluation_year }); await load() } catch (error) { message.value = error.message }
}
async function openLogs(row) {
  try { logs.value = await counterpartApi.pushLogs(row.id); selected.value = row; showLogs.value = true } catch (error) { message.value = error.message }
}
function addQuestion() {
  draftQuestions.value.push({ text: '', type: 'SCORE', required: true, dimensionIndex: 0 })
}
onMounted(load)
</script>

<template>
  <section class="page active">
    <div class="alert alert-info">发布后的问卷结构只读；短信推送为本地模拟，仅记录发送与送达状态。</div>
    <div v-if="message" class="alert alert-warning">{{ message }}</div>
    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ batches.length }}</div><div class="label">问卷批次</div><div class="sub">真实批次</div></div>
      <div class="stat-card"><div class="num green">{{ published }}</div><div class="label">开放填写</div><div class="sub">已发布批次</div></div>
      <div class="stat-card"><div class="num orange">{{ batches.reduce((n,x)=>n+Number(x.recipient_count||0),0) }}</div><div class="label">接收对象</div><div class="sub">来自确认关系</div></div>
      <div class="stat-card"><div class="num">{{ batches.reduce((n,x)=>n+Number(x.submitted_count||0),0) }}</div><div class="label">已提交</div><div class="sub">匿名回收</div></div>
    </div>
    <div class="card"><div class="card-header"><h3>📨 评价问卷管理/推送</h3><button class="btn btn-primary" @click="showCreate=true">创建问卷批次</button></div>
      <div class="table-scroll"><table><thead><tr><th>批次编码</th><th>标题</th><th>年度</th><th>截止时间</th><th>题目</th><th>接收/提交</th><th>状态</th><th>操作</th></tr></thead><tbody>
        <tr v-if="!batches.length"><td colspan="8" class="empty-cell">暂无问卷批次</td></tr>
        <tr v-for="row in batches" :key="row.id"><td>{{ row.batch_code }}</td><td>{{ row.title }}</td><td>{{ row.evaluation_year }}</td><td>{{ row.deadline_at || '-' }}</td><td>{{ row.question_count }}</td><td>{{ row.recipient_count }} / {{ row.submitted_count }}</td><td><span class="tag tag-info">{{ row.status }}</span></td><td><div class="btn-group">
          <button class="btn btn-sm btn-outline" @click="detail(row)">预览</button><button v-if="row.status==='DRAFT'" class="btn btn-sm btn-outline" @click="chooseRecipients(row)">选择关系</button><button v-if="row.status==='DRAFT'" class="btn btn-sm btn-primary" @click="action(row,'publish')">发布</button><button v-if="row.status==='PUBLISHED'" class="btn btn-sm btn-primary" @click="action(row,'push')">模拟推送</button><button v-if="row.status==='PUBLISHED'" class="btn btn-sm btn-outline" @click="action(row,'deadline')">截止</button><button v-if="row.status==='PUBLISHED'" class="btn btn-sm btn-outline" @click="action(row,'close')">关闭</button><button class="btn btn-sm btn-outline" @click="openLogs(row)">推送日志</button><button v-if="row.status!=='DRAFT'" class="btn btn-sm btn-outline" @click="copy(row)">复制</button>
        </div></td></tr>
      </tbody></table></div>
    </div>
    <div v-if="showCreate" class="evidence-modal show" @click.self="showCreate=false"><div class="evidence-panel"><div class="panel-head"><h3>创建问卷批次</h3><button class="btn btn-outline" @click="showCreate=false">关闭</button></div><div class="panel-body"><div class="alert alert-info">在保存草稿前配置维度和1～5分/文字题；发布后结构只读。</div><div class="search-bar"><div class="form-item"><label>批次编码</label><input v-model.trim="form.batchCode"></div><div class="form-item"><label>标题</label><input v-model.trim="form.title"></div><div class="form-item"><label>年度</label><input v-model.number="form.evaluationYear" type="number"></div><div class="form-item"><label>截止时间</label><input v-model="form.deadlineAt" type="datetime-local"></div><div class="form-item"><label>说明</label><input v-model.trim="form.description"></div><div class="form-item" style="min-width:360px"><label>评价维度（逗号分隔）</label><input v-model.trim="form.dimensionNames"></div></div><div class="table-scroll"><table><thead><tr><th>维度</th><th>题目</th><th>类型</th><th>必答</th><th>操作</th></tr></thead><tbody><tr v-for="(question,index) in draftQuestions" :key="index"><td><select v-model.number="question.dimensionIndex"><option v-for="(name,dIndex) in dimensionNames" :key="name" :value="dIndex">{{ name }}</option></select></td><td><input v-model.trim="question.text"></td><td><select v-model="question.type"><option value="SCORE">1～5分</option><option value="TEXT">文字题</option></select></td><td><input v-model="question.required" type="checkbox"></td><td><button class="btn btn-sm btn-outline" :disabled="draftQuestions.length<=1" @click="draftQuestions.splice(index,1)">移除</button></td></tr></tbody></table></div><div class="btn-group"><button class="btn btn-outline" @click="addQuestion">添加题目</button><button class="btn btn-primary" @click="create">保存草稿</button></div></div></div></div>
    <div v-if="showRecipients" class="evidence-modal show" @click.self="showRecipients=false"><div class="evidence-panel"><div class="panel-head"><h3>选择已确认协作关系</h3><button class="btn btn-outline" @click="showRecipients=false">关闭</button></div><div class="panel-body"><label v-for="row in relations" :key="row.id" class="version-item"><span><input v-model="selectedRelations" type="checkbox" :value="row.id"> {{ row.subject_org_name }} → {{ row.counterpart_org_name }} / {{ row.collaboration_item }}</span></label><button class="btn btn-primary" @click="addRecipients">生成接收对象与匿名Token</button></div></div></div>
    <div v-if="selected && !showRecipients && !showLogs" class="evidence-modal show" @click.self="selected=null"><div class="evidence-panel"><div class="panel-head"><h3>问卷预览</h3><button class="btn btn-outline" @click="selected=null">关闭</button></div><div class="panel-body"><div class="alert alert-info">{{ selected.title }} / {{ selected.status }}</div><div v-for="question in selected.questions" :key="question.id" class="version-item"><span>{{ question.question_code }}. {{ question.question_text }}</span><span class="tag tag-info">{{ question.question_type === 'SCORE' ? '1～5分' : '文字题' }}</span></div></div></div></div>
    <div v-if="showLogs" class="evidence-modal show" @click.self="showLogs=false"><div class="evidence-panel"><div class="panel-head"><h3>本地模拟推送日志</h3><button class="btn btn-outline" @click="showLogs=false">关闭</button></div><div class="panel-body"><div v-if="!logs.length" class="empty">暂无推送记录</div><div v-for="log in logs" :key="log.id" class="version-item"><span>{{ log.anonymous_code }} / {{ log.message_summary }}</span><span class="tag tag-success">{{ log.delivery_status }}</span></div></div></div></div>
  </section>
</template>
