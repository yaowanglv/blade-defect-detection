<template>
  <div class="history-page page-shell">
    <section class="page-header">
      <div>
        <h2>检测历史</h2>
        <p class="subtitle">查看、复查和删除历史风机缺陷检测记录</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadHistory">
          刷新
        </el-button>
      </div>
    </section>

    <section class="filter-card">
      <div class="filter-grid">
        <el-select
          v-if="isAdmin"
          v-model="selectedUserId"
          clearable
          filterable
          placeholder="检测用户"
          :loading="userLoading"
        >
          <el-option
            v-for="item in userOptions"
            :key="item.id"
            :label="getUserOptionLabel(item)"
            :value="item.id"
          />
        </el-select>

        <el-select v-model="selectedAiModel" clearable placeholder="大模型">
          <el-option
            v-for="item in aiModelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-date-picker
          v-model="selectedCreateDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="构建日期"
        />

        <el-select v-model="selectedDetectStatus" clearable placeholder="检测状态">
          <el-option
            v-for="item in detectStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select
          v-model="selectedAiUsed"
          clearable
          placeholder="是否使用AI分析"
          @change="handleFilterChange"
          @clear="handleFilterChange"
        >
          <el-option label="已使用AI分析" :value="true" />
          <el-option label="未使用AI分析" :value="false" />
        </el-select>

        <el-select
          v-model="selectedFormat"
          clearable
          placeholder="分析格式"
          @change="handleFilterChange"
          @clear="handleFilterChange"
        >
          <el-option label="全部" value="all" />
          <el-option label="图片" value="image" />
          <el-option label="视频" value="video" />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">
          查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="history-card">
      <el-table :data="historyList" class="history-table" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="检测用户" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getDetectorName(row) }}
          </template>
        </el-table-column>
        <el-table-column label="分析格式" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row._formatType === 'video' ? 'primary' : 'success'" size="small">
              {{ row._formatType === 'video' ? '视频' : '图片' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件名" show-overflow-tooltip min-width="180">
          <template #default="{ row }">
            {{ row.originalImageName || row.originalVideoName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="detectStatus" label="检测状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.detectStatus)" size="small">
              {{ getStatusText(row.detectStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="aiStatus" label="AI分析" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getAiStatusType(row.aiStatus)" size="small">
              {{ getAiStatusText(row.aiStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大模型" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="getAiModelName(row)" size="small" type="warning">
              {{ getAiModelName(row) }}
            </el-tag>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="构建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewRecord(row)">查看</el-button>
            <el-button
              v-if="isAdmin"
              type="danger"
              size="small"
              @click="deleteRecord(row.id, row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request, { getLoginUser } from '@/utils/request.js'

const router = useRouter()
const historyList = ref([])
const loading = ref(false)
const userLoading = ref(false)
const userOptions = ref([])
const selectedUserId = ref(null)
const selectedAiModel = ref('')
const selectedCreateDate = ref('')
const selectedDetectStatus = ref(null)
const selectedAiUsed = ref(null)
const selectedFormat = ref('all')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const currentUser = ref(getLoginUser())

const aiModelOptions = [
  { label: 'Deepseek-V4', value: 'deepseek' },
  { label: 'GLM-5.1', value: 'glm' },
  { label: 'Kimi-k2.6', value: 'kimi' }
]

const detectStatusOptions = [
  { label: '待检测', value: 0 },
  { label: '检测中', value: 1 },
  { label: '完成', value: 2 },
  { label: '失败', value: 3 }
]

const isAdmin = computed(() => {
  const roles = Array.isArray(currentUser.value?.roles) ? currentUser.value.roles : []
  const permissions = Array.isArray(currentUser.value?.permissions) ? currentUser.value.permissions : []
  return String(currentUser.value?.role || '').toLowerCase() === 'admin'
    || roles.map(role => String(role).toUpperCase()).includes('ADMIN')
    || permissions.includes('admin:read')
})

const loadHistory = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    appendParam(params, 'userId', selectedUserId.value)
    appendParam(params, 'aiModel', selectedAiModel.value)
    appendParam(params, 'createDate', selectedCreateDate.value)
    appendParam(params, 'detectStatus', selectedDetectStatus.value)
    if (selectedAiUsed.value !== null && selectedAiUsed.value !== undefined) {
      params.aiUsed = selectedAiUsed.value ? '1' : '0'
    }

    const isVideo = selectedFormat.value === 'video'

    if (selectedFormat.value === '' || selectedFormat.value === 'all') {
      let imageList = []
      let videoList = []
      let imageTotal = 0
      let videoTotal = 0

      try {
        const imageRes = await request.get('/detect/selectPage', { params })
        if (imageRes.code === '200' && imageRes.data) {
          imageList = (imageRes.data.list || []).map(item => ({ ...item, _formatType: 'image' }))
          imageTotal = imageRes.data.total || 0
        }
      } catch (error) {
        console.warn('加载图片检测历史失败', error)
      }

      try {
        const videoRes = await request.get('/video/selectPage', { params })
        if (videoRes.code === '200' && videoRes.data) {
          videoList = (videoRes.data.list || []).map(item => ({ ...item, _formatType: 'video' }))
          videoTotal = videoRes.data.total || 0
        }
      } catch (error) {
        console.warn('加载视频检测历史失败', error)
      }

      const merged = [...imageList, ...videoList].sort((a, b) => {
        const ta = a.createTime || ''
        const tb = b.createTime || ''
        return tb.localeCompare(ta)
      })
      historyList.value = merged
      total.value = imageTotal + videoTotal
    } else {
      const apiUrl = isVideo ? '/video/selectPage' : '/detect/selectPage'
      try {
        const res = await request.get(apiUrl, { params })
        if (res.code === '200') {
          const list = (res.data.list || []).map(item => ({
            ...item,
            _formatType: isVideo ? 'video' : 'image'
          }))
          historyList.value = list
          total.value = res.data.total
        } else {
          ElMessage.error(res.msg || '加载历史记录失败')
        }
      } catch (error) {
        console.error('加载历史记录失败', error)
        ElMessage.error('加载历史记录失败')
      }
    }
  } catch (error) {
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

const appendParam = (params, key, value) => {
  if (value !== null && value !== undefined && value !== '') {
    params[key] = value
  }
}

const loadUsers = async () => {
  if (!isAdmin.value) {
    userOptions.value = []
    selectedUserId.value = null
    return
  }

  userLoading.value = true
  try {
    const res = await request.get('/admin/selectAll', { silentForbidden: true })
    if (res.code === '200') {
      userOptions.value = res.data || []
    }
  } catch (error) {
    console.warn('加载检测用户失败', error)
  } finally {
    userLoading.value = false
  }
}

const handleFilterChange = () => {
  pageNum.value = 1
  loadHistory()
}

const handleSearch = () => {
  handleFilterChange()
}

const resetFilters = () => {
  selectedUserId.value = null
  selectedAiModel.value = ''
  selectedCreateDate.value = ''
  selectedDetectStatus.value = null
  selectedAiUsed.value = null
  selectedFormat.value = 'all'
  handleFilterChange()
}

const viewRecord = (row) => {
  const path = row._formatType === 'video' ? '/manager/video' : '/manager/detect'
  router.push({
    path,
    query: { recordId: row.id }
  })
}

const deleteRecord = async (id, row) => {
  if (!isAdmin.value) return

  try {
    await ElMessageBox.confirm('确定删除这条检测记录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    const isVideo = row?._formatType === 'video'
    const apiUrl = isVideo ? `/video/delete/${id}` : `/detect/delete/${id}`
    const res = await request.delete(apiUrl)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadHistory()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

const getUserOptionLabel = (user) => {
  return user.name || user.username || `用户ID:${user.id || '-'}`
}

const getDetectorName = (row) => {
  return row.detectorName || row.userName || `用户ID:${row.userId || '-'}`
}

const getStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待检测', 1: '检测中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

const getAiStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getAiStatusText = (status) => {
  const texts = { 0: '未分析', 1: '分析中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

const getAiModelName = (row) => {
  if (row.aiStatus !== 2 || !row.aiModel) return ''
  const model = String(row.aiModel).toLowerCase()
  const names = {
    deepseek: 'Deepseek-V4',
    glm: 'GLM-5.1',
    kimi: 'Kimi-k2.6',
    doubao: '豆包'
  }
  return Object.prototype.hasOwnProperty.call(names, model) ? names[model] : row.aiModel
}

onMounted(() => {
  loadUsers()
  loadHistory()
})
</script>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header h2 {
  margin: 0 0 6px;
  color: var(--app-text);
  font-size: 20px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.filter-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.filter-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(6, minmax(140px, 1fr));
  gap: 10px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

:deep(.filter-grid .el-date-editor) {
  width: 100%;
}

.history-card {
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.history-table {
  width: 100%;
}

.pagination-wrapper {
  margin-top: 18px;
  text-align: right;
}

.empty-text {
  color: var(--app-text-muted);
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .header-actions,
  .filter-card,
  .filter-actions {
    width: 100%;
  }

  .filter-card {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
