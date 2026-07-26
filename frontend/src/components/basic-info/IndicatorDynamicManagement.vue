<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  copyIndicatorVersion,
  createIndicatorRule,
  fetchIndicatorRules,
  fetchIndicatorTree,
  fetchIndicatorVersions,
  updateIndicatorItem,
  updateIndicatorItemStatus,
  updateIndicatorRule,
  updateIndicatorRuleStatus,
} from '../../indicatorApi'

const versions = ref([])
const tree = ref([])
const rules = ref([])
const selectedVersionId = ref('')
const error = ref('')
const message = ref('')
const modal = ref('')
const itemForm = reactive({})
const ruleForm = reactive(emptyRule())

function emptyRule() {
  return {
    id: null, indicatorId: '', ruleType: 'THRESHOLD_DEDUCTION',
    ruleName: '', configText: '{\n  "threshold": 60,\n  "deduction": 4\n}',
    description: '', sortOrder: 0, rowVersion: null,
  }
}

const currentVersion = computed(() =>
  versions.value.find((item) => item.id === Number(selectedVersionId.value)))
const flatTree = computed(() => {
  const result = []
  const walk = (nodes, depth = 0) => nodes.forEach((node) => {
    result.push({ ...node, depth }); walk(node.children || [], depth + 1)
  })
  walk(tree.value)
  return result
})
const level3Items = computed(() =>
  flatTree.value.filter((item) => item.indicator_level === 3 && item.status === 'ACTIVE'))
const parentOptions = computed(() =>
  flatTree.value.filter((item) =>
    item.indicator_level === itemForm.indicatorLevel - 1 && item.status === 'ACTIVE'))
const rootWeight = computed(() =>
  flatTree.value.filter(i => i.indicator_level === 1 && i.status === 'ACTIVE')
    .reduce((sum, item) => sum + Number(item.weight), 0))

async function load() {
  error.value = ''
  try {
    versions.value = await fetchIndicatorVersions()
    if (!selectedVersionId.value && versions.value.length) {
      selectedVersionId.value = versions.value.find(v => v.status === 'DRAFT')?.id || versions.value[0].id
    }
    await loadVersion()
  } catch (e) { error.value = e.message }
}

async function loadVersion() {
  if (!selectedVersionId.value) { tree.value = []; rules.value = []; return }
  try {
    [tree.value, rules.value] = await Promise.all([
      fetchIndicatorTree(selectedVersionId.value),
      fetchIndicatorRules({ versionId: selectedVersionId.value }),
    ])
  } catch (e) { error.value = e.message }
}

function openItem(row) {
  Object.assign(itemForm, {
    id: row.id, versionId: row.version_id, parentId: row.parent_id,
    indicatorLevel: row.indicator_level, indicatorCode: row.indicator_code,
    indicatorName: row.indicator_name, standardScore: row.standard_score,
    weight: row.weight, indicatorType: row.indicator_type,
    evaluationMethod: row.evaluation_method || '', sortOrder: row.sort_order,
    rowVersion: row.row_version,
  })
  modal.value = 'item'
}

async function saveItem() {
  try {
    await updateIndicatorItem(itemForm.id, { ...itemForm })
    message.value = '指标配置已更新'
    modal.value = ''
    await loadVersion()
  } catch (e) { error.value = e.message }
}

async function toggleItem(row) {
  try {
    await updateIndicatorItemStatus(row.id, {
      status: row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      rowVersion: row.row_version,
    })
    message.value = '指标状态已更新'
    await loadVersion()
  } catch (e) { error.value = e.message }
}

function changeRuleType() {
  const defaults = {
    THRESHOLD_DEDUCTION: '{\n  "threshold": 60,\n  "deduction": 4\n}',
    STEP_SCORE: '{\n  "steps": [\n    { "min": 90, "scoreRate": 100 },\n    { "min": 80, "scoreRate": 80 }\n  ]\n}',
    VETO: '{\n  "condition": "发生红线问题",\n  "result": "不合格"\n}',
  }
  ruleForm.configText = defaults[ruleForm.ruleType]
}

function openRule(rule = null) {
  if (rule) {
    Object.assign(ruleForm, {
      id: rule.id, indicatorId: rule.indicator_id, ruleType: rule.rule_type,
      ruleName: rule.rule_name, configText: JSON.stringify(JSON.parse(rule.config_json), null, 2),
      description: rule.description || '', sortOrder: rule.sort_order,
      rowVersion: rule.row_version,
    })
  } else {
    Object.assign(ruleForm, emptyRule(), { indicatorId: level3Items.value[0]?.id || '' })
  }
  modal.value = 'rule'
}

async function saveRule() {
  try {
    const body = {
      indicatorId: Number(ruleForm.indicatorId), ruleType: ruleForm.ruleType,
      ruleName: ruleForm.ruleName, config: JSON.parse(ruleForm.configText),
      description: ruleForm.description, sortOrder: ruleForm.sortOrder,
      rowVersion: ruleForm.rowVersion,
    }
    if (ruleForm.id) await updateIndicatorRule(ruleForm.id, body)
    else await createIndicatorRule(body)
    message.value = ruleForm.id ? '评分规则已更新' : '评分规则已新增'
    modal.value = ''
    await loadVersion()
  } catch (e) { error.value = e instanceof SyntaxError ? '规则配置JSON格式错误' : e.message }
}

async function toggleRule(rule) {
  try {
    await updateIndicatorRuleStatus(rule.id, {
      status: rule.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      rowVersion: rule.row_version,
    })
    message.value = '规则状态已更新'
    await loadVersion()
  } catch (e) { error.value = e.message }
}

async function copyToDraft() {
  if (!currentVersion.value) return
  const year = Number(window.prompt('新草稿年度', String(currentVersion.value.evaluation_year + 1)))
  if (!year) return
  try {
    const copied = await copyIndicatorVersion(currentVersion.value.id, {
      targetYear: year, versionName: `${year}年度调整草稿`,
    })
    selectedVersionId.value = copied.id
    message.value = '已复制为可编辑草稿'
    await load()
  } catch (e) { error.value = e.message }
}

function ruleTypeName(type) {
  return { THRESHOLD_DEDUCTION: '阈值扣分', STEP_SCORE: '阶梯评分', VETO: '一票否决' }[type]
}

onMounted(load)
</script>

<template>
  <section class="page active dynamic-page">
    <div class="alert alert-info">草稿可调整指标、权重、启停状态和确定性评分规则；已发布版本只读，需要复制为新草稿后修改。</div>
    <div v-if="message" class="alert alert-success">{{ message }}</div>
    <div v-if="error" class="alert alert-danger">{{ error }}</div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">⚙️</span>指标动态管理</h3><button class="btn btn-outline" @click="copyToDraft">复制为新草稿</button></div>
      <div class="version-row"><label>指标版本</label><select v-model="selectedVersionId" @change="loadVersion"><option v-for="version in versions" :key="version.id" :value="version.id">{{ version.system_name }} · {{ version.version_name }} · {{ version.status }}</option></select><span class="tag" :class="currentVersion?.status === 'DRAFT' ? 'tag-warning' : 'tag-success'">{{ currentVersion?.status }}</span><span :class="Math.abs(rootWeight - 100) < .001 ? 'ok' : 'warn'">一级启用权重：{{ rootWeight }}%</span></div>
      <table>
        <thead><tr><th>指标</th><th>层级</th><th>权重</th><th>标准分</th><th>类型</th><th>评估方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in flatTree" :key="item.id">
          <td><span :style="{ paddingLeft: `${item.depth * 24}px` }"><span class="code-badge">{{ item.indicator_code }}</span> {{ item.indicator_name }}</span></td>
          <td>{{ item.indicator_level }}级</td><td>{{ item.weight }}%</td><td>{{ item.indicator_level === 3 ? item.standard_score : '—' }}</td>
          <td>{{ item.indicator_type === 'COMMON' ? '共性' : '个性' }}</td><td>{{ item.evaluation_method || '—' }}</td>
          <td><span class="tag" :class="item.status === 'ACTIVE' ? 'tag-success' : 'tag-default'">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td>
          <td><div class="btn-group"><button class="btn btn-sm btn-outline" :disabled="currentVersion?.status !== 'DRAFT'" @click="openItem(item)">编辑</button><button class="btn btn-sm btn-outline" :disabled="currentVersion?.status !== 'DRAFT'" @click="toggleItem(item)">{{ item.status === 'ACTIVE' ? '停用' : '启用' }}</button></div></td>
        </tr></tbody>
      </table>
      <div v-if="!flatTree.length" class="empty">当前版本暂无指标。</div>
    </div>

    <div class="card">
      <div class="card-header"><h3><span class="icon">📐</span>三级指标评分规则</h3><button class="btn btn-primary" :disabled="currentVersion?.status !== 'DRAFT' || !level3Items.length" @click="openRule()">＋ 新增规则</button></div>
      <div class="rule-grid">
        <article v-for="rule in rules" :key="rule.id" class="rule-card">
          <div class="rule-head"><h4>{{ rule.rule_name }}</h4><span class="tag" :class="rule.rule_type === 'VETO' ? 'tag-danger' : 'tag-info'">{{ ruleTypeName(rule.rule_type) }}</span></div>
          <p><b>{{ rule.indicator_code }} {{ rule.indicator_name }}</b></p><p>{{ rule.description || '无说明' }}</p>
          <pre>{{ JSON.stringify(JSON.parse(rule.config_json), null, 2) }}</pre>
          <div class="btn-group"><span class="tag" :class="rule.status === 'ACTIVE' ? 'tag-success' : 'tag-default'">{{ rule.status === 'ACTIVE' ? '启用' : '停用' }}</span><button class="btn btn-sm btn-outline" :disabled="currentVersion?.status !== 'DRAFT'" @click="openRule(rule)">编辑</button><button class="btn btn-sm btn-outline" :disabled="currentVersion?.status !== 'DRAFT'" @click="toggleRule(rule)">{{ rule.status === 'ACTIVE' ? '停用' : '启用' }}</button></div>
        </article>
        <div v-if="!rules.length" class="empty">当前版本暂无评分规则。</div>
      </div>
    </div>

    <div v-if="modal" class="modal-mask" @click.self="modal = ''"><div class="modal-card">
      <div class="card-header"><h3>{{ modal === 'item' ? '编辑指标' : (ruleForm.id ? '编辑规则' : '新增规则') }}</h3><button class="btn btn-sm btn-outline" @click="modal = ''">关闭</button></div>
      <form v-if="modal === 'item'" class="form-grid" @submit.prevent="saveItem">
        <label>指标编码<input v-model="itemForm.indicatorCode" required></label><label>指标名称<input v-model="itemForm.indicatorName" required></label>
        <label v-if="itemForm.indicatorLevel > 1">父指标<select v-model="itemForm.parentId"><option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">{{ parent.indicator_code }} {{ parent.indicator_name }}</option></select></label>
        <label>权重（%）<input v-model.number="itemForm.weight" type="number" min="0" max="100" step=".01"></label>
        <label v-if="itemForm.indicatorLevel === 3">标准分<input v-model.number="itemForm.standardScore" type="number" min="0" step=".01"></label>
        <label>指标类型<select v-model="itemForm.indicatorType"><option value="COMMON">共性</option><option value="CUSTOM">个性</option></select></label>
        <label class="span-2">评估方式<textarea v-model="itemForm.evaluationMethod" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">保存调整</button>
      </form>
      <form v-else class="form-grid" @submit.prevent="saveRule">
        <label>三级指标<select v-model="ruleForm.indicatorId" required><option v-for="item in level3Items" :key="item.id" :value="item.id">{{ item.indicator_code }} {{ item.indicator_name }}</option></select></label>
        <label>规则类型<select v-model="ruleForm.ruleType" @change="changeRuleType"><option value="THRESHOLD_DEDUCTION">阈值扣分</option><option value="STEP_SCORE">阶梯评分</option><option value="VETO">一票否决</option></select></label>
        <label>规则名称<input v-model="ruleForm.ruleName" required></label><label>排序<input v-model.number="ruleForm.sortOrder" type="number" min="0"></label>
        <label class="span-2">规则配置JSON<textarea v-model="ruleForm.configText" rows="8" required></textarea></label>
        <label class="span-2">说明<textarea v-model="ruleForm.description" rows="3"></textarea></label>
        <button class="btn btn-primary span-2" type="submit">保存规则</button>
      </form>
    </div></div>
  </section>
</template>

<style scoped>
.version-row{display:flex;align-items:center;gap:12px;margin:14px 0}.version-row select{min-width:440px}.ok{color:#16a34a;font-weight:700}.warn{color:#ea580c;font-weight:700}.rule-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.rule-card{border:1px solid #e2e8f0;border-radius:10px;padding:14px}.rule-head{display:flex;justify-content:space-between;gap:10px}.rule-card pre{background:#f8fafc;padding:10px;white-space:pre-wrap;font-size:12px}.empty{text-align:center;color:#94a3b8;padding:28px}.modal-mask{position:fixed;inset:0;background:#0f172a88;display:flex;align-items:center;justify-content:center;z-index:1000}.modal-card{background:#fff;border-radius:14px;padding:20px;width:min(760px,94vw);max-height:90vh;overflow:auto}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form-grid label{display:flex;flex-direction:column;gap:6px;font-weight:600}.span-2{grid-column:1/-1}@media(max-width:900px){.rule-grid,.form-grid{grid-template-columns:1fr}.span-2{grid-column:auto}}
</style>
