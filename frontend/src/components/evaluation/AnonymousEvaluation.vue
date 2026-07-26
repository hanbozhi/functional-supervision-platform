<script setup>
import { onMounted, ref } from 'vue'
import { counterpartApi } from '../../counterpartEvaluationApi'

const batches = ref([])
const selectedBatch = ref('')
const recipients = ref([])
const token = ref('')
const questionnaire = ref(null)
const answers = ref({})
const message = ref('')
const restored = ref(null)

async function loadBatches() {
  try { batches.value = await counterpartApi.questionnaires(); if (batches.value.length) { selectedBatch.value = batches.value[0].id; await loadRecipients() } } catch (error) { message.value = error.message }
}
async function loadRecipients() {
  if (!selectedBatch.value) return
  try { recipients.value = await counterpartApi.recipients(selectedBatch.value) } catch (error) { message.value = error.message }
}
async function openToken(value = token.value) {
  if (!value) return
  try { token.value = value; questionnaire.value = await counterpartApi.fill(value); answers.value = {} } catch (error) { message.value = error.message }
}
async function submit() {
  try {
    const payload = questionnaire.value.questions.map((question) => ({
      questionId: question.id,
      scoreValue: question.question_type === 'SCORE' ? Number(answers.value[question.id]) || null : null,
      textValue: question.question_type === 'TEXT' ? answers.value[question.id] || null : null,
    })).filter((answer) => answer.scoreValue !== null || answer.textValue)
    await counterpartApi.submit(token.value, { answers: payload, elapsedSeconds: 30 })
    message.value = '匿名问卷提交成功，不能重复提交'; questionnaire.value = null; await loadRecipients()
  } catch (error) { message.value = error.message }
}
async function restore(row) {
  if (!window.confirm('这是模拟后台还原操作，将记录访问日志。是否继续？')) return
  try { restored.value = await counterpartApi.restore(row.id) } catch (error) { message.value = error.message }
}
onMounted(loadBatches)
</script>

<template>
  <section class="page active">
    <div class="alert alert-warning">本页面为业务匿名演示：普通列表仅显示匿名编号；Token不是高安全认证凭证，后台还原会明确留痕。</div>
    <div v-if="message" class="alert alert-info">{{ message }}</div>
    <div class="card"><div class="card-header"><h3>🕶️ 批量匿名评价支持</h3></div><div class="search-bar"><div class="form-item"><label>问卷批次</label><select v-model="selectedBatch" @change="loadRecipients"><option v-for="batch in batches" :key="batch.id" :value="batch.id">{{ batch.title }}（{{ batch.status }}）</option></select></div><div class="form-item"><label>填写Token</label><input v-model.trim="token" placeholder="粘贴Token"></div><button class="btn btn-primary" @click="openToken()">打开匿名问卷</button><button class="btn btn-outline" @click="loadRecipients">刷新</button></div>
      <div class="table-scroll"><table><thead><tr><th>匿名编号</th><th>问卷状态</th><th>推送时间</th><th>提交时间</th><th>匿名说明</th><th>操作</th></tr></thead><tbody><tr v-if="!recipients.length"><td colspan="6" class="empty-cell">暂无接收对象</td></tr><tr v-for="row in recipients" :key="row.id"><td><span class="code-badge">{{ row.anonymous_code }}</span></td><td><span class="tag tag-info">{{ row.status }}</span></td><td>{{ row.sent_at || '-' }}</td><td>{{ row.submitted_at || '-' }}</td><td>真实机构在普通结果页隐藏</td><td><div class="btn-group"><button class="btn btn-sm btn-outline" @click="openToken(row.fill_token)">模拟填写</button><button class="btn btn-sm btn-outline" @click="restore(row)">模拟后台还原</button></div></td></tr></tbody></table></div>
    </div>
    <div v-if="questionnaire" class="evidence-modal show" @click.self="questionnaire=null"><div class="evidence-panel"><div class="panel-head"><h3>{{ questionnaire.title }} / {{ questionnaire.anonymousCode }}</h3><button class="btn btn-outline" @click="questionnaire=null">关闭</button></div><div class="panel-body"><div v-for="question in questionnaire.questions" :key="question.id" class="version-item"><div style="flex:1"><strong>{{ question.question_text }}</strong><div v-if="question.question_type==='SCORE'" class="btn-group" style="margin-top:8px"><label v-for="score in 5" :key="score"><input v-model="answers[question.id]" type="radio" :name="`q${question.id}`" :value="score"> {{ score }}</label></div><textarea v-else v-model="answers[question.id]" rows="3" style="width:100%;margin-top:8px"></textarea></div></div><button class="btn btn-primary" @click="submit">提交匿名评价</button></div></div></div>
    <div v-if="restored" class="evidence-modal show" @click.self="restored=null"><div class="evidence-panel"><div class="panel-head"><h3>模拟后台还原结果</h3><button class="btn btn-outline" @click="restored=null">关闭</button></div><div class="panel-body"><div class="alert alert-warning">{{ restored.warning }}</div><div class="evidence-grid"><div class="evidence-box"><div class="label">匿名编号</div><div class="value">{{ restored.anonymous_code }}</div></div><div class="evidence-box"><div class="label">评价机构</div><div class="value">{{ restored.evaluator_org_name }}</div></div><div class="evidence-box"><div class="label">被评机构</div><div class="value">{{ restored.target_org_name }}</div></div></div></div></div></div>
  </section>
</template>
