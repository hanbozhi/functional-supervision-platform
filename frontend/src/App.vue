<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  fetchRightsDetail,
  fetchRightsItems,
  fetchRightsOptions,
  fetchRightsStats,
} from './api'
import staticPages from './staticPages'
import OrgUnitManagement from './components/basic-info/OrgUnitManagement.vue'
import ThreeFixedPlanManagement from './components/basic-info/ThreeFixedPlanManagement.vue'

const navGroups = [
  {
    id: 'm1',
    icon: '📁',
    title: '基础信息管理',
    children: [
      {
        title: '机构编制基础数据库管理',
        children: [
          ['m1-1', '单位架构管理'],
          ['m1-2', '三定方案信息归集'],
          ['m1-3', '权责清单管理'],
          ['m1-4', '实有人员与领导职数管理'],
          ['m1-5', '部门核心职能清单库'],
          ['m1-6', '历史评估档案电子化管理'],
        ],
      },
      {
        title: '三级量化评估指标体系配置',
        children: [
          ['m1-7', '指标体系初始化配置'],
          ['m1-8', '指标动态管理功能'],
          ['m1-9', '评分规则配置'],
        ],
      },
    ],
  },
  {
    id: 'm2',
    icon: '📊',
    title: '四方多维评价管理',
    children: [
      { title: '对口部门评作为（横向协同评价）', children: [['m2-1', '评价关系图谱'], ['m2-2', '在线匿名问卷'], ['m2-3', '评价异常识别']] },
      { title: '编办评实绩（内部评估）', children: [['m2-4', '评估任务派发'], ['m2-5', '指标评分工作台'], ['m2-6', '绩效数据同步']] },
      { title: '机构自评功能', children: [['m2-7', '自评报告填报'], ['m2-8', '佐证材料上传'], ['m2-9', '自评结果比对']] },
      { title: '服务对象满意度评价', children: [['m2-10', '隐私保护与脱敏'], ['m2-11', '服务对象名单抽取'], ['m2-12', '问卷短信推送'], ['m2-13', '满意度自动汇总'], ['m2-14', '低分样本核查']] },
    ],
  },
  { id: 'm4', icon: '🗂️', title: '数据采集与评估', children: [{ title: '', children: [['m4-1', '数据采集与解析'], ['m4-2', 'AI评估模块']] }] },
  { id: 'm3', icon: '🔔', title: '智能预警与疑点核查', direct: ['m3-1', '智能预警与疑点核查'] },
  { id: 'm5', icon: '✅', title: '问题整改闭环管理', children: [{ title: '', children: [['m5-1', '整改通知书下发'], ['m5-2', '整改方案填报'], ['m5-3', 'AI智能核验'], ['m5-4', '验收销号'], ['m5-5', '逾期预警']] }] },
  { id: 'm6', icon: '📈', title: '大数据可视化驾驶舱', children: [{ title: '', children: [['m6-1', '全市总览大屏'], ['m6-2', '部门专题分析'], ['m6-3', '编制资源分析'], ['m6-4', '整改督办看板']] }] },
]

const pageMeta = {
  'm1-1': ['单位架构管理', '维护政府组织机构层级、机构性质、核定编制和启停状态。'],
  'm1-2': ['三定方案信息归集', '归集三定方案文本、机构职责、内设机构与编制依据。'],
  'm1-4': ['实有人员与领导职数管理', '维护实有人员、领导职数、编制使用率与空编情况。'],
  'm1-5': ['部门核心职能清单库', '沉淀部门核心职能、重点职责和履职边界。'],
  'm1-6': ['历史评估档案电子化管理', '归档历年评估材料、评分结果、整改闭环资料。'],
  'm1-7': ['指标体系初始化配置', '按对象类型初始化一级、二级、三级指标及权重。'],
  'm1-8': ['指标动态管理功能', '维护指标版本、启停状态、适用对象和规则变更。'],
  'm1-9': ['评分规则配置', '配置评分公式、扣分规则、阈值和样例指标。'],
  'm2-1': ['评价关系图谱', '配置评价对象之间的横向协同评价关系。'],
  'm2-2': ['在线匿名问卷', '面向评价主体配置匿名问卷和量表规则。'],
  'm2-3': ['评价异常识别', '识别集中打分、异常低分、重复提交等异常样本。'],
  'm2-4': ['评估任务派发', '面向编办内部评估人员分配评分任务和评估对象。'],
  'm2-5': ['指标评分工作台', '按指标、部门、材料完成评分和意见填报。'],
  'm2-6': ['绩效数据同步', '同步绩效、实名制、业务系统等外部数据。'],
  'm2-7': ['自评报告填报', '支持机构在线填报自评报告、说明和改进计划。'],
  'm2-8': ['佐证材料上传', '上传制度文件、工作台账、照片、表格等佐证材料。'],
  'm2-9': ['自评结果比对', '比对自评分与外部评分、历史评分和系统测算结果。'],
  'm2-10': ['隐私保护与脱敏', '对服务对象评价名单、手机号、身份信息进行脱敏处理。'],
  'm2-11': ['服务对象名单抽取', '按服务事项和评价周期抽取服务对象样本。'],
  'm2-12': ['问卷短信推送', '配置满意度问卷投放、短信推送和回收进度。'],
  'm2-13': ['满意度自动汇总', '按部门、事项、维度自动汇总满意度评价结果。'],
  'm2-14': ['低分样本核查', '对低分样本和异常评价进行人工核查。'],
  'm3-1': ['智能预警与疑点核查', '围绕权责清单、编制使用、机构设置等触发疑点预警。'],
  'm4-1': ['数据采集与解析', '采集结构化表格、文档、系统接口数据并完成解析。'],
  'm4-2': ['AI评估模块', '基于指标规则、材料证据和业务数据生成辅助评估结果。'],
  'm5-1': ['整改通知书下发', '根据评估和预警结果生成整改通知书并下发。'],
  'm5-2': ['整改方案填报', '部门在线填报整改方案、责任人、措施和时限。'],
  'm5-3': ['AI智能核验', '对整改材料和验收标准进行辅助核验。'],
  'm5-4': ['验收销号', '完成整改验收、销号审批和闭环归档。'],
  'm5-5': ['逾期预警', '对逾期未整改、临期整改事项进行提醒。'],
  'm6-1': ['全市总览大屏', '总览全市职能运行评估态势、预警分布和排名。'],
  'm6-2': ['部门专题分析', '围绕单部门展示履职效能、协同评价和整改趋势。'],
  'm6-3': ['编制资源分析', '展示编制总量、使用率、空编与需求缺口。'],
  'm6-4': ['整改督办看板', '展示整改事项分布、督办进度、逾期预警和闭环情况。'],
}

const activePage = ref(window.location.hash?.slice(1) || 'm6-1')
const openGroups = reactive({ m1: true, m2: false, m4: false, m5: false, m6: true })
const openSubgroups = reactive({ 'm1-0': true })

const filters = reactive({ keyword: '', department: '', powerType: '', sourceFile: '' })
const pager = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const rows = ref([])
const options = reactive({ departments: [], powerTypes: [], sourceFiles: [] })
const stats = reactive({ totalItems: 0, totalSourceFiles: 0, totalDepartments: 0, totalPowerTypes: 0, powerTypeDistribution: [], departmentTop: [] })
const loading = ref(false)
const errorMessage = ref('')
const selected = ref(null)
const detailLoading = ref(false)
const toastMessage = ref('')
const staticModal = reactive({ visible: false, title: '', summary: '', type: '' })
let toastTimer = null
let rightsLoaded = false

const activeTitle = computed(() => pageMeta[activePage.value]?.[0] || '职能运行评估')
const breadcrumb = computed(() => findBreadcrumb(activePage.value) || '大数据可视化驾驶舱 / 全市总览大屏')
const isDarkPage = computed(() => activePage.value.startsWith('m6-'))
const visiblePages = computed(() => {
  const total = pager.totalPages || 1
  const start = Math.max(1, pager.page - 2)
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})
const ledgerHint = computed(() => {
  if (loading.value) return `正在加载第 ${pager.page || 1} 页数据...`
  if (errorMessage.value) return errorMessage.value
  return `已加载 ${rows.value.length} 条，当前第 ${pager.page} / ${pager.totalPages || 1} 页，共 ${pager.total} 条。`
})
const maxPowerTypeCount = computed(() => Math.max(1, ...stats.powerTypeDistribution.map((item) => item.value || 0)))

function findBreadcrumb(pageId) {
  for (const group of navGroups) {
    if (group.direct?.[0] === pageId) return group.title
    for (const [subIndex, subgroup] of (group.children || []).entries()) {
      for (const item of subgroup.children || []) {
        if (item[0] === pageId) return subgroup.title ? `${group.title} / ${subgroup.title} / ${item[1]}` : `${group.title} / ${item[1]}`
      }
      if (subIndex === -1) return ''
    }
  }
  return ''
}

function isGroupActive(group) {
  if (group.direct?.[0] === activePage.value) return true
  return (group.children || []).some((subgroup) => subgroup.children.some((item) => item[0] === activePage.value))
}

function isSubgroupActive(group, subgroup) {
  return subgroup.children.some((item) => item[0] === activePage.value)
}

function toggleGroup(group) {
  if (group.direct) {
    activate(group.direct[0])
    return
  }
  openGroups[group.id] = !openGroups[group.id]
}

function toggleSubgroup(key) {
  openSubgroups[key] = !openSubgroups[key]
}

async function activate(pageId) {
  activePage.value = pageId
  window.location.hash = pageId
  if (pageId === 'm1-3') await ensureRightsLoaded()
}

async function ensureRightsLoaded() {
  if (rightsLoaded) return
  rightsLoaded = true
  try {
    await loadMeta()
  } catch (error) {
    errorMessage.value = error.message || '基础数据加载失败'
  }
  await loadRows(1)
}

async function loadMeta() {
  const [optionData, statsData] = await Promise.all([fetchRightsOptions(), fetchRightsStats()])
  Object.assign(options, optionData)
  Object.assign(stats, statsData)
}

async function loadRows(nextPage = pager.page) {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await fetchRightsItems({ page: nextPage, size: pager.size, ...filters })
    rows.value = data.items
    pager.page = data.page
    pager.size = data.size
    pager.total = data.total
    pager.totalPages = data.totalPages
  } catch (error) {
    errorMessage.value = error.message || '数据加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailLoading.value = true
  selected.value = row
  try {
    selected.value = await fetchRightsDetail(row.id)
  } catch (error) {
    errorMessage.value = error.message || '详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function search() { loadRows(1) }
function resetFilters() {
  filters.keyword = ''
  filters.department = ''
  filters.powerType = ''
  filters.sourceFile = ''
  loadRows(1)
}

function exportCurrentPage() {
  if (!rows.value.length) return
  const headers = ['序号','事项名称','子项名称','权力类型','实施依据','行使主体','承办机构','实施层级及权限','部门职责','责任事项内容','责任事项依据','追责对象范围','追责情形','备注','状态']
  const keys = ['sequenceNo','itemName','subitemName','powerType','basis','exercisingBody','undertakingOrg','implementationLevelAuthority','departmentDuty','responsibilityContent','responsibilityBasis','accountabilityScope','accountabilitySituation','remark','status']
  const csv = [headers.join(','), ...rows.value.map((row) => keys.map((key) => csvCell(row[key])).join(','))].join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `责权清单_第${pager.page}页.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

function csvCell(value) {
  const text = value == null ? '' : String(value)
  return `"${text.replaceAll('"', '""')}"`
}
function shortText(value, length = 42) {
  if (!value) return '-'
  return value.length > length ? `${value.slice(0, length)}...` : value
}
function statusClass(status) {
  if (!status) return 'tag tag-default'
  if (status.includes('正常') || status.includes('有效')) return 'tag tag-success'
  if (status.includes('停用') || status.includes('失效')) return 'tag tag-danger'
  return 'tag tag-info'
}

function showToastMessage(message) {
  toastMessage.value = message || '操作已记录'
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toastMessage.value = ''
  }, 2200)
}

function openStaticModal(title, summary, type = 'info') {
  staticModal.visible = true
  staticModal.title = title
  staticModal.summary = summary
  staticModal.type = type
}

function closeStaticModal() {
  staticModal.visible = false
}

onMounted(() => {
  window.showToast = showToastMessage
  window.showEvidence = (dept, score, grade) => openStaticModal(
    '评分依据说明',
    `${dept} 综合总分 ${score}，系统自动划档为“${grade}”。以下为本次评分的数据来源、引用原始值、计算公式和加减分明细。`,
    'evidence'
  )
  window.closeEvidence = closeStaticModal
  window.showWarningAudit = (id, dept, type) => openStaticModal(
    '预警核查审核',
    `${id} / ${dept} / ${type}预警。当前预警处于“核查中”，请查看提交证据并给出通过或不通过审核意见。`,
    'warning'
  )
  window.closeWarningAudit = closeStaticModal
  if (activePage.value === 'm1-3') ensureRightsLoaded()
})
</script>

<template>
  <div class="app-frame" :class="{ 'dark-global': isDarkPage }">
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">📋</div>
        <div class="logo-text">职能运行评估<small>智能监管平台</small></div>
      </div>
      <nav class="nav-list">
        <div
          v-for="group in navGroups"
          :key="group.id"
          class="nav-group"
          :class="{ open: openGroups[group.id] || isGroupActive(group), active: isGroupActive(group), direct: group.direct }"
        >
          <div class="group-title" @click="toggleGroup(group)">
            <span class="group-icon">{{ group.icon }}</span><span>{{ group.title }}</span><span class="group-arrow">▶</span>
          </div>
          <div v-if="!group.direct" class="group-children nested-menu">
            <div
              v-for="(subgroup, subIndex) in group.children"
              :key="`${group.id}-${subIndex}`"
              class="nav-subgroup"
              :class="{ open: openSubgroups[`${group.id}-${subIndex}`] || isSubgroupActive(group, subgroup) }"
            >
              <div v-if="subgroup.title" class="subgroup-title" @click.stop="toggleSubgroup(`${group.id}-${subIndex}`)">
                <span>{{ subgroup.title }}</span><span class="subgroup-arrow">▶</span>
              </div>
              <div class="subgroup-children" :class="{ directChildren: !subgroup.title }">
                <a v-for="item in subgroup.children" :key="item[0]" href="#" :class="{ active: activePage === item[0] }" @click.prevent="activate(item[0])">{{ item[1] }}</a>
              </div>
            </div>
          </div>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="header">
        <div class="breadcrumb">📍 <span>{{ breadcrumb }}</span></div>
        <div class="user-area"><span class="badge">管理员</span><span class="username">张主任</span><div class="avatar">张</div></div>
      </header>

      <div class="content" id="mainContent">
        <OrgUnitManagement v-if="activePage === 'm1-1'" />

        <ThreeFixedPlanManagement v-else-if="activePage === 'm1-2'" />

        <section v-else-if="activePage === 'm1-3'" class="page active">
          <div class="alert" :class="errorMessage ? 'alert-danger' : 'alert-success'">{{ ledgerHint }}</div>
          <div class="card authority-ledger">
            <div class="card-header"><h3><span class="icon">🧾</span>责权清单管理</h3><span class="extra">SQLite 实时查询</span></div>
            <div class="search-bar">
              <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="事项名称、依据、部门、原始值" @keyup.enter="search"></div>
              <div class="form-item"><label>部门</label><select v-model="filters.department"><option value="">全部部门</option><option v-for="item in options.departments" :key="item" :value="item">{{ item }}</option></select></div>
              <div class="form-item"><label>权力类型</label><select v-model="filters.powerType"><option value="">全部类型</option><option v-for="item in options.powerTypes" :key="item" :value="item">{{ item }}</option></select></div>
              <div class="form-item"><label>来源文件</label><select v-model="filters.sourceFile"><option value="">全部来源</option><option v-for="item in options.sourceFiles" :key="item" :value="item">{{ item }}</option></select></div>
              <div class="toolbar-actions"><button class="btn btn-primary" @click="search">🔍 查询</button><button class="btn btn-outline" @click="resetFilters">重置</button><button class="btn btn-outline" :disabled="!rows.length" @click="exportCurrentPage">导出当前页</button><button class="btn btn-outline" @click="loadRows()">刷新</button></div>
            </div>
            <div class="stat-grid">
              <div class="stat-card"><div class="num">{{ stats.totalItems }}</div><div class="label">权责事项</div><div class="sub">标准化事项总数</div></div>
              <div class="stat-card"><div class="num green">{{ stats.totalSourceFiles }}</div><div class="label">来源文件</div><div class="sub">已入库文档</div></div>
              <div class="stat-card"><div class="num orange">{{ stats.totalDepartments }}</div><div class="label">涉及部门</div><div class="sub">部门维度统计</div></div>
              <div class="stat-card"><div class="num red">{{ stats.totalPowerTypes }}</div><div class="label">权力类型</div><div class="sub">事项分类维度</div></div>
            </div>
            <div class="table-toolbar"><div class="table-summary">共 {{ pager.total }} 条，当前第 {{ pager.page }} / {{ pager.totalPages || 1 }} 页</div><div class="form-item page-size"><select v-model.number="pager.size" @change="loadRows(1)"><option :value="10">10 条/页</option><option :value="20">20 条/页</option><option :value="50">50 条/页</option><option :value="100">100 条/页</option></select></div></div>
            <div class="table-scroll">
              <table id="rightsLedgerTable">
                <thead><tr><th>序号</th><th>事项名称</th><th>子项名称</th><th>权力类型</th><th>实施依据</th><th>行使主体</th><th>承办机构</th><th>实施层级及权限</th><th>部门职责</th><th>责任事项内容</th><th>责任事项依据</th><th>追责对象范围</th><th>追责情形</th><th>备注</th><th>状态</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-if="loading"><td colspan="16" class="empty-cell">数据加载中...</td></tr>
                  <tr v-else-if="!rows.length"><td colspan="16" class="empty-cell">暂无匹配数据</td></tr>
                  <tr v-for="row in rows" v-else :key="row.id">
                    <td>{{ row.sequenceNo || row.id }}</td><td :title="row.itemName">{{ shortText(row.itemName, 26) }}</td><td :title="row.subitemName">{{ shortText(row.subitemName, 20) }}</td><td><span class="tag tag-info">{{ row.powerType || '未分类' }}</span></td><td :title="row.basis">{{ shortText(row.basis, 26) }}</td><td :title="row.exercisingBody">{{ shortText(row.exercisingBody, 18) }}</td><td :title="row.undertakingOrg">{{ shortText(row.undertakingOrg, 18) }}</td><td :title="row.implementationLevelAuthority">{{ shortText(row.implementationLevelAuthority, 20) }}</td><td :title="row.departmentDuty">{{ shortText(row.departmentDuty, 22) }}</td><td :title="row.responsibilityContent">{{ shortText(row.responsibilityContent, 22) }}</td><td :title="row.responsibilityBasis">{{ shortText(row.responsibilityBasis, 22) }}</td><td :title="row.accountabilityScope">{{ shortText(row.accountabilityScope, 18) }}</td><td :title="row.accountabilitySituation">{{ shortText(row.accountabilitySituation, 18) }}</td><td :title="row.remark">{{ shortText(row.remark, 16) }}</td><td><span :class="statusClass(row.status)">{{ row.status || '未标注' }}</span></td><td><button class="btn btn-sm btn-outline" @click="openDetail(row)">查看</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="btn-group pager-group"><button class="btn btn-outline" :disabled="pager.page <= 1" @click="loadRows(pager.page - 1)">上一页</button><button v-for="pageNo in visiblePages" :key="pageNo" class="btn" :class="pageNo === pager.page ? 'btn-primary' : 'btn-outline'" @click="loadRows(pageNo)">{{ pageNo }}</button><button class="btn btn-outline" :disabled="pager.page >= pager.totalPages" @click="loadRows(pager.page + 1)">下一页</button></div>
          </div>
          <div class="analysis-grid">
            <div class="compare-box"><h4>权力类型分布</h4><div class="log-list"><div v-for="item in stats.powerTypeDistribution.slice(0, 10)" :key="item.name" class="log-item"><span :title="item.name">{{ item.name }}</span><div class="progress-bar"><div class="fill fill-blue" :style="{ width: `${Math.max(6, item.value / maxPowerTypeCount * 100)}%` }"></div></div><span class="tag tag-info">{{ item.value }}</span></div></div></div>
            <div class="compare-box"><h4>部门事项排行</h4><div class="log-list"><button v-for="item in stats.departmentTop" :key="item.name" class="log-item dept-link" @click="filters.department = item.name; search()"><span :title="item.name">{{ item.name }}</span><span class="tag tag-default">{{ item.value }}</span></button></div></div>
          </div>
        </section>

        <div v-else-if="staticPages[activePage]" v-html="staticPages[activePage]"></div>

        <section v-else class="page active">
          <div class="alert alert-info">{{ pageMeta[activePage]?.[1] }}</div>
          <div v-if="activePage.startsWith('m6-')" class="stat-grid">
            <div class="stat-card"><div class="num">86.5</div><div class="label">综合指数</div><div class="sub">全市平均</div></div>
            <div class="stat-card"><div class="num green">38</div><div class="label">运行正常</div><div class="sub">部门数量</div></div>
            <div class="stat-card"><div class="num orange">12</div><div class="label">关注预警</div><div class="sub">待核查事项</div></div>
            <div class="stat-card"><div class="num red">5</div><div class="label">高风险</div><div class="sub">重点督办</div></div>
          </div>
          <div class="placeholder-grid">
            <div class="card">
              <div class="card-header"><h3><span class="icon">📌</span>{{ activeTitle }}</h3><span class="extra">原型页面</span></div>
              <div class="feature-grid">
                <div class="feature-card"><div class="k">数据维护</div><div class="v">待接入</div><div class="sub">保留 ui 原型交互位置</div></div>
                <div class="feature-card"><div class="k">业务审核</div><div class="v">待接入</div><div class="sub">后续对接流程接口</div></div>
                <div class="feature-card"><div class="k">统计分析</div><div class="v">待接入</div><div class="sub">使用统一卡片与表格样式</div></div>
              </div>
              <div class="table-scroll"><table><thead><tr><th>业务对象</th><th>管理内容</th><th>状态</th><th>操作</th></tr></thead><tbody><tr><td>{{ activeTitle }}</td><td>{{ pageMeta[activePage]?.[1] }}</td><td><span class="tag tag-info">原型展示</span></td><td><button class="btn btn-sm btn-outline">查看</button></td></tr><tr><td>数据接口</td><td>等待后续业务模块实现</td><td><span class="tag tag-default">未接入</span></td><td><button class="btn btn-sm btn-outline">配置</button></td></tr></tbody></table></div>
            </div>
            <div class="card">
              <div class="card-header"><h3><span class="icon">📊</span>页面概览</h3><span class="extra">设计占位</span></div>
              <div class="mock-panel-list">
                <div class="mock-row"><strong>业务流程</strong><span>采集、校验、分析、反馈、归档</span><span class="tag tag-info">5 步</span></div>
                <div class="mock-row"><strong>数据来源</strong><span>部门填报、系统同步、材料解析</span><span class="tag tag-success">可扩展</span></div>
                <div class="mock-row"><strong>风险提示</strong><span>异常数据将进入疑点核查与整改闭环</span><span class="tag tag-warning">关注</span></div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <div v-if="toastMessage" class="toast">{{ toastMessage }}</div>

    <div v-if="staticModal.visible" class="evidence-modal show" @click.self="closeStaticModal">
      <div class="evidence-panel">
        <div class="panel-head"><h3>{{ staticModal.title }}</h3><button class="btn btn-sm btn-outline" @click="closeStaticModal">关闭</button></div>
        <div class="panel-body">
          <div class="alert" :class="staticModal.type === 'warning' ? 'alert-warning' : 'alert-info'">{{ staticModal.summary }}</div>
          <div class="evidence-grid">
            <div class="evidence-box"><div class="label">数据来源</div><div class="value">基础库、评估指标、佐证材料、业务系统同步记录</div></div>
            <div class="evidence-box"><div class="label">处理状态</div><div class="value">静态原型展示，后续可接入真实审核流程</div></div>
          </div>
          <div class="calc-line">示例公式：综合得分 = ∑（指标得分 × 指标权重） - 风险扣分 + 创新加分</div>
        </div>
      </div>
    </div>

    <div v-if="selected" class="evidence-modal show" @click.self="selected = null">
      <div class="evidence-panel">
        <div class="panel-head"><h3>权责事项详情</h3><button class="btn btn-sm btn-outline" @click="selected = null">关闭</button></div>
        <div class="panel-body">
          <div class="alert alert-info">{{ selected.sourceFile || '未知来源' }} / {{ selected.sheetName || '未知工作表' }} / 第 {{ selected.sourceRowNumber || '-' }} 行</div>
          <div v-if="detailLoading" class="empty">正在加载详情...</div>
          <template v-else>
            <div class="evidence-grid">
              <div class="evidence-box"><div class="label">部门</div><div class="value">{{ selected.department || '-' }}</div></div><div class="evidence-box"><div class="label">年度</div><div class="value">{{ selected.year || '-' }}</div></div><div class="evidence-box wide"><div class="label">事项名称</div><div class="value">{{ selected.itemName || '-' }}</div></div><div class="evidence-box"><div class="label">子项名称</div><div class="value">{{ selected.subitemName || '-' }}</div></div><div class="evidence-box"><div class="label">权力类型</div><div class="value">{{ selected.powerType || '-' }}</div></div><div class="evidence-box"><div class="label">行使主体</div><div class="value">{{ selected.exercisingBody || '-' }}</div></div><div class="evidence-box"><div class="label">承办机构</div><div class="value">{{ selected.undertakingOrg || '-' }}</div></div><div class="evidence-box wide"><div class="label">实施层级及权限</div><div class="value">{{ selected.implementationLevelAuthority || '-' }}</div></div><div class="evidence-box wide"><div class="label">部门职责</div><div class="value">{{ selected.departmentDuty || '-' }}</div></div><div class="evidence-box wide"><div class="label">责任事项内容</div><div class="value">{{ selected.responsibilityContent || '-' }}</div></div><div class="evidence-box wide"><div class="label">责任事项依据</div><div class="value">{{ selected.responsibilityBasis || '-' }}</div></div><div class="evidence-box"><div class="label">追责对象范围</div><div class="value">{{ selected.accountabilityScope || '-' }}</div></div><div class="evidence-box"><div class="label">追责情形</div><div class="value">{{ selected.accountabilitySituation || '-' }}</div></div><div class="evidence-box wide"><div class="label">实施依据</div><div class="value">{{ selected.basis || '-' }}</div></div><div class="evidence-box"><div class="label">备注</div><div class="value">{{ selected.remark || '-' }}</div></div><div class="evidence-box"><div class="label">状态</div><div class="value">{{ selected.status || '-' }}</div></div>
            </div>
            <div class="calc-line">原始记录：{{ shortText(selected.rawJson, 700) }}</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
