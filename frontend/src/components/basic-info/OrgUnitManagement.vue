<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createOrgUnit,
  fetchOrgDetail,
  fetchOrgOptions,
  fetchOrgStats,
  fetchOrgTree,
  fetchOrgUnits,
  updateOrgStatus,
  updateOrgUnit,
  verifyOrgUnit,
} from '../../orgUnitApi'

const tree = ref([])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const selectedNode = ref(null)
const detail = ref(null)
const stats = reactive({ totalUnits: 0, administrativeUnits: 0, publicInstitutions: 0 })
const options = reactive({ unitTypes: [], unitLevels: [], organizationNatures: [], statuses: [], verificationStatuses: [] })
const filters = reactive({ keyword: '', scope: 'SUBTREE', unitType: '', unitLevel: '', organizationNature: '', status: '', verificationStatus: '' })
const pager = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const formModal = reactive({ visible: false, mode: 'create', title: '' })
const verifyModal = reactive({ visible: false, id: null, versionNo: null, unitName: '', result: 'VERIFIED', opinion: '' })
const form = reactive(emptyForm())

const flatTree = computed(() => {
  const result = []
  const walk = (nodes, depth = 0) => nodes.forEach((node) => {
    result.push({ ...node, depth })
    walk(node.children || [], depth + 1)
  })
  walk(tree.value)
  return result
})

function emptyForm() {
  return {
    id: null, parentId: null, unitCode: '', unitName: '', unitShortName: '',
    unitType: 'ADMIN_AGENCY', unitLevel: 'CITY',
    organizationNature: 'GOVERNMENT_AGENCY', approvedStaffing: null,
    sortOrder: 0, versionNo: null,
  }
}

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    const [treeData, optionData] = await Promise.all([
      fetchOrgTree({ includeInactive: true }),
      fetchOrgOptions(),
    ])
    tree.value = treeData
    Object.assign(options, optionData)
    if (!selectedNode.value && flatTree.value.length) selectedNode.value = flatTree.value[0]
    await loadData(1)
  } catch (e) {
    error.value = e.message || '机构数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadData(page = pager.page) {
  loading.value = true
  error.value = ''
  const params = {
    parentId: selectedNode.value?.id,
    page, size: pager.size, ...filters,
  }
  try {
    const [pageData, statData] = await Promise.all([
      fetchOrgUnits(params),
      fetchOrgStats({ parentId: selectedNode.value?.id, scope: filters.scope }),
    ])
    rows.value = pageData.items
    Object.assign(pager, { page: pageData.page, size: pageData.size, total: pageData.total, totalPages: pageData.totalPages })
    Object.assign(stats, statData)
  } catch (e) {
    error.value = e.message || '机构数据加载失败'
  } finally {
    loading.value = false
  }
}

function selectNode(node) {
  selectedNode.value = node
  loadData(1)
}

function resetFilters() {
  Object.assign(filters, { keyword: '', scope: 'SUBTREE', unitType: '', unitLevel: '', organizationNature: '', status: '', verificationStatus: '' })
  loadData(1)
}

function openCreate() {
  Object.assign(form, emptyForm(), {
    parentId: selectedNode.value?.unitType === 'ROOT' || selectedNode.value?.unitType === 'GROUP'
      ? selectedNode.value.id : selectedNode.value?.parentId,
  })
  formModal.mode = 'create'
  formModal.title = '新增机构'
  formModal.visible = true
}

async function openEdit(row) {
  try {
    const data = await fetchOrgDetail(row.id)
    Object.assign(form, {
      id: data.id, parentId: data.parentId, unitCode: data.unitCode,
      unitName: data.unitName, unitShortName: data.unitShortName || '',
      unitType: data.unitType, unitLevel: data.unitLevel,
      organizationNature: data.organizationNature,
      approvedStaffing: data.approvedStaffing, sortOrder: data.sortOrder,
      versionNo: data.versionNo,
    })
    formModal.mode = 'edit'
    formModal.title = '编辑机构'
    formModal.visible = true
  } catch (e) {
    error.value = e.message
  }
}

async function saveForm() {
  if (!form.unitCode.trim() || !form.unitName.trim()) {
    error.value = '单位编码和机构名称不能为空'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const body = {
      parentId: form.unitType === 'ROOT' ? null : Number(form.parentId),
      unitCode: form.unitCode, unitName: form.unitName,
      unitShortName: form.unitShortName, unitType: form.unitType,
      unitLevel: form.unitLevel, organizationNature: form.organizationNature,
      approvedStaffing: form.approvedStaffing === '' ? null : form.approvedStaffing,
      sortOrder: form.sortOrder, versionNo: form.versionNo,
    }
    if (formModal.mode === 'create') await createOrgUnit(body)
    else await updateOrgUnit(form.id, body)
    formModal.visible = false
    await initialize()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  try {
    detail.value = await fetchOrgDetail(row.id)
  } catch (e) {
    error.value = e.message
  }
}

function openVerify(row) {
  Object.assign(verifyModal, {
    visible: true, id: row.id, versionNo: row.versionNo,
    unitName: row.unitName, result: 'VERIFIED', opinion: '',
  })
}

async function submitVerification() {
  if (verifyModal.result === 'REJECTED' && !verifyModal.opinion.trim()) {
    error.value = '核验不通过时必须填写意见'
    return
  }
  saving.value = true
  try {
    await verifyOrgUnit(verifyModal.id, {
      result: verifyModal.result, opinion: verifyModal.opinion,
      versionNo: verifyModal.versionNo,
    })
    verifyModal.visible = false
    await initialize()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  if (!window.confirm(`确认${next === 'ACTIVE' ? '启用' : '停用'}“${row.unitName}”吗？`)) return
  try {
    await updateOrgStatus(row.id, { status: next, versionNo: row.versionNo })
    await initialize()
  } catch (e) {
    error.value = e.message
  }
}

function label(items, code) {
  return items.find((item) => item.code === code)?.label || code || '-'
}

function displayStatus(row) {
  if (row.status === 'INACTIVE') return ['已停用', 'tag-default']
  if (row.verificationStatus === 'VERIFIED') return ['已核验', 'tag-success']
  if (row.verificationStatus === 'REJECTED') return ['核验不通过', 'tag-danger']
  return ['待核验', 'tag-warning']
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(initialize)
</script>

<template>
  <section class="page active org-unit-page">
    <div class="alert" :class="error ? 'alert-danger' : 'alert-info'">
      {{ error || '单位架构数据来自 SQLite；点击左侧节点可查看对应机构及下级。' }}
    </div>
    <div class="module-layout org-layout">
      <aside class="side-panel org-tree-panel">
        <div class="tree-header"><h4>组织机构树</h4><button class="btn btn-sm btn-outline" @click="initialize">刷新</button></div>
        <button
          v-for="node in flatTree" :key="node.id"
          class="org-tree-node" :class="{ active: selectedNode?.id === node.id, inactive: node.status === 'INACTIVE' }"
          :style="{ paddingLeft: `${10 + node.depth * 18}px` }"
          @click="selectNode(node)"
        >
          <span>{{ node.children?.length ? '▾' : '•' }}</span>{{ node.unitName }}
        </button>
      </aside>

      <div class="detail-panel">
        <div class="card-header org-title">
          <h3><span class="icon">🏛️</span>单位架构详情</h3>
          <div class="toolbar-actions"><button class="btn btn-primary" @click="openCreate">＋ 新增机构</button><button class="btn btn-outline" @click="loadData()">刷新</button></div>
        </div>
        <div class="search-bar org-search">
          <div class="form-item"><label>关键词</label><input v-model.trim="filters.keyword" placeholder="机构名称、简称或编码" @keyup.enter="loadData(1)"></div>
          <div class="form-item"><label>查询范围</label><select v-model="filters.scope"><option value="SUBTREE">当前及全部下级</option><option value="DIRECT">仅直接下级</option></select></div>
          <div class="form-item"><label>机构类型</label><select v-model="filters.unitType"><option value="">全部</option><option v-for="item in options.unitTypes" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="form-item"><label>启停状态</label><select v-model="filters.status"><option value="">全部</option><option v-for="item in options.statuses" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="form-item"><label>核验状态</label><select v-model="filters.verificationStatus"><option value="">全部</option><option v-for="item in options.verificationStatuses" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="toolbar-actions"><button class="btn btn-primary" @click="loadData(1)">🔍 查询</button><button class="btn btn-outline" @click="resetFilters">重置</button></div>
        </div>

        <div class="field-grid">
          <div class="field-box"><div class="name">机构总数</div><div class="val">{{ stats.totalUnits }}</div></div>
          <div class="field-box"><div class="name">行政机关</div><div class="val">{{ stats.administrativeUnits }}</div></div>
          <div class="field-box"><div class="name">事业单位</div><div class="val">{{ stats.publicInstitutions }}</div></div>
        </div>

        <div class="table-toolbar"><span class="table-summary">当前节点：{{ selectedNode?.unitName || '全部' }}，共 {{ pager.total }} 条</span></div>
        <div class="table-scroll">
          <table>
            <thead><tr><th>机构名称</th><th>统一代码</th><th>层级</th><th>机构性质</th><th>核定编制</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="loading"><td colspan="7" class="empty-cell">数据加载中...</td></tr>
              <tr v-else-if="!rows.length"><td colspan="7" class="empty-cell">暂无匹配机构</td></tr>
              <tr v-for="row in rows" v-else :key="row.id">
                <td>{{ row.unitName }}</td><td><span class="code-badge">{{ row.unitCode }}</span></td>
                <td>{{ label(options.unitLevels, row.unitLevel) }}</td>
                <td>{{ label(options.organizationNatures, row.organizationNature) }}</td>
                <td>{{ row.approvedStaffing ?? '未填写' }}</td>
                <td><span class="tag" :class="displayStatus(row)[1]">{{ displayStatus(row)[0] }}</span></td>
                <td class="org-actions">
                  <button class="btn btn-sm btn-outline" @click="openDetail(row)">详情</button>
                  <button class="btn btn-sm btn-outline" @click="openEdit(row)">编辑</button>
                  <button v-if="row.status === 'ACTIVE'" class="btn btn-sm btn-primary" @click="openVerify(row)">核验</button>
                  <button class="btn btn-sm btn-outline" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="btn-group pager-group">
          <button class="btn btn-outline" :disabled="pager.page <= 1" @click="loadData(pager.page - 1)">上一页</button>
          <span class="table-summary">第 {{ pager.page }} / {{ pager.totalPages || 1 }} 页</span>
          <button class="btn btn-outline" :disabled="pager.page >= pager.totalPages" @click="loadData(pager.page + 1)">下一页</button>
        </div>
      </div>
    </div>

    <div v-if="formModal.visible" class="org-modal-backdrop" @click.self="formModal.visible = false">
      <div class="org-modal">
        <div class="card-header"><h3>{{ formModal.title }}</h3><button class="btn btn-sm btn-outline" @click="formModal.visible = false">关闭</button></div>
        <div class="org-form-grid">
          <div class="form-item"><label class="required">单位编码</label><input v-model.trim="form.unitCode" maxlength="50"></div>
          <div class="form-item"><label class="required">机构名称</label><input v-model.trim="form.unitName" maxlength="100"></div>
          <div class="form-item"><label>机构简称</label><input v-model.trim="form.unitShortName" maxlength="50"></div>
          <div class="form-item"><label class="required">机构类型</label><select v-model="form.unitType"><option v-for="item in options.unitTypes" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="form-item"><label>上级机构</label><select v-model="form.parentId" :disabled="form.unitType === 'ROOT'"><option :value="null">请选择</option><option v-for="node in flatTree.filter(n => n.id !== form.id)" :key="node.id" :value="node.id">{{ '　'.repeat(node.depth) }}{{ node.unitName }}</option></select></div>
          <div class="form-item"><label class="required">机构层级</label><select v-model="form.unitLevel"><option v-for="item in options.unitLevels" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="form-item"><label class="required">机构性质</label><select v-model="form.organizationNature"><option v-for="item in options.organizationNatures" :key="item.code" :value="item.code">{{ item.label }}</option></select></div>
          <div class="form-item"><label>核定编制</label><input v-model.number="form.approvedStaffing" type="number" min="0"></div>
          <div class="form-item"><label>排序</label><input v-model.number="form.sortOrder" type="number"></div>
        </div>
        <div class="btn-group org-modal-actions"><button class="btn btn-primary" :disabled="saving" @click="saveForm">{{ saving ? '保存中...' : '保存' }}</button><button class="btn btn-outline" @click="formModal.visible = false">取消</button></div>
      </div>
    </div>

    <div v-if="verifyModal.visible" class="org-modal-backdrop" @click.self="verifyModal.visible = false">
      <div class="org-modal compact">
        <div class="card-header"><h3>机构核验</h3><button class="btn btn-sm btn-outline" @click="verifyModal.visible = false">关闭</button></div>
        <p class="org-modal-hint">核验机构：{{ verifyModal.unitName }}</p>
        <div class="form-item"><label class="required">核验结果</label><select v-model="verifyModal.result"><option value="VERIFIED">核验通过</option><option value="REJECTED">核验不通过</option></select></div>
        <div class="form-item"><label :class="{ required: verifyModal.result === 'REJECTED' }">核验意见</label><textarea v-model.trim="verifyModal.opinion" placeholder="填写核验意见"></textarea></div>
        <div class="btn-group org-modal-actions"><button class="btn btn-primary" :disabled="saving" @click="submitVerification">提交核验</button><button class="btn btn-outline" @click="verifyModal.visible = false">取消</button></div>
      </div>
    </div>

    <div v-if="detail" class="org-modal-backdrop" @click.self="detail = null">
      <div class="org-modal">
        <div class="card-header"><h3>机构详情</h3><button class="btn btn-sm btn-outline" @click="detail = null">关闭</button></div>
        <div class="org-detail-grid">
          <div><span>机构名称</span><b>{{ detail.unitName }}</b></div><div><span>单位编码</span><b>{{ detail.unitCode }}</b></div>
          <div><span>上级机构</span><b>{{ detail.parentName || '-' }}</b></div><div><span>核定编制</span><b>{{ detail.approvedStaffing ?? '未填写' }}</b></div>
          <div><span>创建人/时间</span><b>{{ detail.createdByName || '-' }} / {{ formatTime(detail.createdAt) }}</b></div>
          <div><span>修改人/时间</span><b>{{ detail.updatedByName || '-' }} / {{ formatTime(detail.updatedAt) }}</b></div>
          <div><span>核验人/时间</span><b>{{ detail.verifiedByName || '-' }} / {{ formatTime(detail.verifiedAt) }}</b></div>
          <div><span>核验意见</span><b>{{ detail.verificationOpinion || '-' }}</b></div>
        </div>
        <h4 class="org-history-title">核验历史</h4>
        <div v-if="detail.verificationHistory?.length" class="version-list">
          <div v-for="item in detail.verificationHistory" :key="item.id" class="version-item"><span>{{ formatTime(item.verifiedAt) }}　{{ item.verifierName }}　{{ item.opinion || '无意见' }}</span><span class="tag" :class="item.result === 'VERIFIED' ? 'tag-success' : 'tag-danger'">{{ item.result === 'VERIFIED' ? '通过' : '不通过' }}</span></div>
        </div>
        <div v-else class="empty-cell">暂无核验历史</div>
      </div>
    </div>
  </section>
</template>
