<script setup>
import { computed, onMounted, ref } from 'vue'
import { counterpartApi } from '../../counterpartEvaluationApi'

const questionnaires = ref([])
const questionnaireId = ref('')
const runs = ref([])
const runId = ref('')
const cases = ref([])
const stats = ref({ organizations: [], questions: [] })
const selected = ref(null)
const message = ref('')
const pending = computed(() => cases.value.filter((row) => ['PENDING', 'ASSIGNED'].includes(row.status)).length)

async function loadQuestionnaires() {
  try {
    questionnaires.value = await counterpartApi.questionnaires()
    if (questionnaires.value.length) { questionnaireId.value = questionnaires.value[0].id; await loadBatch() }
  } catch (error) { message.value = error.message }
}
async function loadBatch() {
  if (!questionnaireId.value) return
  try {
    [runs.value, stats.value] = await Promise.all([counterpartApi.runs(questionnaireId.value), counterpartApi.statistics(questionnaireId.value)])
    if (runs.value.length) { runId.value = runs.value[0].id; await loadCases() } else { cases.value = []; runId.value = '' }
  } catch (error) { message.value = error.message }
}
async function loadCases() {
  if (!runId.value) return
  try { cases.value = await counterpartApi.anomalies(runId.value) } catch (error) { message.value = error.message }
}
async function detect() {
  try { const result = await counterpartApi.detect(questionnaireId.value); message.value = `规则识别完成，共发现 ${result.cases.length} 条异常`; await loadBatch() } catch (error) { message.value = error.message }
}
async function detail(row) {
  try { selected.value = await counterpartApi.anomaly(row.id) } catch (error) { message.value = error.message }
}
async function assign(row) {
  const opinion = window.prompt('分派说明', '分派张主任复核')
  if (opinion === null) return
  try { await counterpartApi.assign(row.id, { userId: null, opinion, rowVersion: row.row_version }); await loadCases() } catch (error) { message.value = error.message }
}
async function review(row, action) {
  const opinion = window.prompt(action === 'ACCEPT' ? '请输入采纳意见' : '请输入驳回意见')
  if (!opinion) return
  try { await counterpartApi.review(row.id, { action, opinion, rowVersion: row.row_version }); await loadCases(); if (selected.value?.id === row.id) await detail(row) } catch (error) { message.value = error.message }
}
onMounted(loadQuestionnaires)
</script>

<template>
  <section class="page active">
    <div class="alert alert-warning">异常由确定性规则识别，不是AI：端点分、与同题均值偏差≥1.5分（样本至少5个）、填写时间少于20秒。</div>
    <div v-if="message" class="alert alert-info">{{ message }}</div>
    <div class="stat-grid">
      <div class="stat-card"><div class="num">{{ runs.length }}</div><div class="label">检测批次</div><div class="sub">历史保留</div></div>
      <div class="stat-card"><div class="num orange">{{ cases.length }}</div><div class="label">规则异常</div><div class="sub">当前检测批次</div></div>
      <div class="stat-card"><div class="num red">{{ pending }}</div><div class="label">待复核</div><div class="sub">含已分派</div></div>
      <div class="stat-card"><div class="num green">{{ stats.questions?.length || 0 }}</div><div class="label">题目统计</div><div class="sub">数据库实时均值</div></div>
    </div>
    <div class="card"><div class="card-header"><h3>📈 异常评分预警与复核</h3><button class="btn btn-primary" :disabled="!questionnaireId" @click="detect">执行规则识别</button></div><div class="search-bar"><div class="form-item"><label>问卷批次</label><select v-model="questionnaireId" @change="loadBatch"><option v-for="q in questionnaires" :key="q.id" :value="q.id">{{ q.title }}</option></select></div><div class="form-item"><label>检测批次</label><select v-model="runId" @change="loadCases"><option v-for="run in runs" :key="run.id" :value="run.id">{{ run.run_code }}（{{ run.anomaly_count }}条）</option></select></div><button class="btn btn-outline" @click="loadCases">刷新</button></div>
      <div class="table-scroll"><table><thead><tr><th>匿名编号</th><th>题目</th><th>异常类型</th><th>观测值</th><th>规则说明</th><th>状态</th><th>分派人</th><th>操作</th></tr></thead><tbody><tr v-if="!cases.length"><td colspan="8" class="empty-cell">暂无规则异常</td></tr><tr v-for="row in cases" :key="row.id"><td>{{ row.anonymous_code }}</td><td>{{ row.question_text || '整份问卷' }}</td><td><span class="tag tag-warning">{{ row.anomaly_type }}</span></td><td>{{ row.observed_value ?? '-' }}</td><td>{{ row.rule_explanation }}</td><td>{{ row.status }}</td><td>{{ row.assigned_to_name || '-' }}</td><td><div class="btn-group"><button class="btn btn-sm btn-outline" @click="detail(row)">详情</button><button v-if="row.status==='PENDING'" class="btn btn-sm btn-outline" @click="assign(row)">分派</button><button v-if="['PENDING','ASSIGNED'].includes(row.status)" class="btn btn-sm btn-primary" @click="review(row,'ACCEPT')">采纳</button><button v-if="['PENDING','ASSIGNED'].includes(row.status)" class="btn btn-sm btn-outline" @click="review(row,'REJECT')">驳回</button></div></td></tr></tbody></table></div>
    </div>
    <div class="card"><div class="card-header"><h3>📊 真实评分汇总</h3><span class="extra">机构 / 维度 / 题目均值</span></div><div class="analysis-grid"><div class="compare-box"><h4>机构平均分</h4><div v-for="row in stats.organizations" :key="row.org_unit_id" class="version-item"><span>{{ row.unit_name }}</span><span class="tag tag-info">{{ row.average_score }}（{{ row.sample_count }}样本）</span></div><div v-if="!stats.organizations?.length" class="empty">暂无已提交评分</div></div><div class="compare-box"><h4>维度与题目平均分</h4><div v-for="row in stats.questions" :key="row.question_text" class="version-item"><span>{{ row.dimension_name || '未分组' }} / {{ row.question_text }}</span><span class="tag tag-success">{{ row.average_score }}（{{ row.sample_count }}样本）</span></div><div v-if="!stats.questions?.length" class="empty">暂无已提交评分</div></div></div></div>
    <div v-if="selected" class="evidence-modal show" @click.self="selected=null"><div class="evidence-panel"><div class="panel-head"><h3>异常复核详情</h3><button class="btn btn-outline" @click="selected=null">关闭</button></div><div class="panel-body"><div class="alert alert-warning">{{ selected.rule_explanation }}</div><div class="evidence-grid"><div class="evidence-box"><div class="label">匿名编号</div><div class="value">{{ selected.anonymous_code }}</div></div><div class="evidence-box"><div class="label">状态</div><div class="value">{{ selected.status }}</div></div></div><h4>复核历史</h4><div v-for="review in selected.reviews" :key="review.id" class="version-item"><span>{{ review.review_action }} / {{ review.review_opinion || '-' }}</span><span>{{ review.reviewer_name }} {{ review.reviewed_at }}</span></div></div></div></div>
  </section>
</template>
