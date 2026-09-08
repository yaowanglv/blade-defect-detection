<template>
  <div class="video-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>风机视频缺陷检测与分析</h2>
      <p class="subtitle">使用深度学习模型与AI大模型对风机视频进行逐帧缺陷检测</p>
    </div>

    <!-- 操作区域 -->
    <div class="operation-area">
      <!-- 第一行：检测操作 -->
      <div class="operation-row">
        <!-- 视频上传 -->
        <el-upload
          class="upload-component"
          action="http://localhost:1234/video/upload"
          :data="uploadData"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :before-upload="beforeUpload"
          accept=".mp4,.avi,.mov,.mkv,.wmv,.flv,.webm"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            上传待检测视频
          </el-button>
        </el-upload>

        <!-- 模型选择下拉框 -->
        <el-select
          v-model="modelPath"
          placeholder="选择检测模型"
          class="model-select"
          size="default"
          :loading="modelLoading"
          filterable
        >
          <template #prefix>模型</template>
          <el-option
            v-for="item in modelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <!-- 置信度设置 -->
        <el-tooltip content="置信度阈值" placement="top">
          <el-input-number
            v-model="confThreshold"
            :min="0"
            :max="1"
            :step="0.1"
            class="threshold-input"
            size="default"
          />
        </el-tooltip>

        <!-- 跳帧设置 -->
        <el-tooltip content="跳帧数：0=每帧处理，1=隔一帧处理（数值越大处理越快但可能漏检）" placement="top">
          <el-input-number
            v-model="skipFrames"
            :min="0"
            :max="10"
            :step="1"
            class="skip-input"
            size="default"
          />
        </el-tooltip>

        <!-- 开始检测按钮 -->
        <el-button
          type="success"
          @click="startDetection"
          :loading="detecting"
          :disabled="!currentRecord || currentRecord.detectStatus === 1 || !modelPath"
          class="control-button"
        >
          <el-icon><VideoPlay /></el-icon>
          {{ detecting ? '检测中...' : '开始检测' }}
        </el-button>

        <!-- 导出检测视频 -->
        <el-button
          type="primary"
          @click="exportVideo"
          :disabled="!resultVideoUrl"
          class="control-button"
        >
          <el-icon><Download /></el-icon>
          导出检测视频
        </el-button>
      </div>
    </div>

    <!-- 检测进度提示 -->
    <div v-if="detecting" class="detect-progress-panel">
      <div class="detect-progress-header">
        <span>正在进行视频检测，处理时间可能较长，请耐心等待...</span>
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
    </div>

    <div v-if="aiProgressVisible" class="ai-progress-panel">
      <div class="ai-progress-header">
        <span>正在生成AI大模型辅助分析报告，请稍后</span>
        <strong>{{ aiAnalysisProgress }}%</strong>
      </div>
      <el-progress
        :percentage="aiAnalysisProgress"
        :stroke-width="12"
        :color="aiProgressColors"
      />
    </div>

    <!-- 视频对比区域 -->
    <div class="video-compare-area" v-if="currentRecord">
      <el-row :gutter="20">
        <!-- 原始视频 -->
        <el-col :span="12">
          <div class="video-card">
            <div class="video-title">原始视频</div>
            <div class="video-wrapper">
              <video
                v-if="originalVideoUrl"
                :src="originalVideoUrl"
                class="detect-video"
                controls
                preload="metadata"
              >
                您的浏览器不支持视频播放
              </video>
              <el-empty v-else description="暂无视频" />
            </div>
            <div class="video-info" v-if="currentRecord.originalVideoName">
              <span>文件名: {{ currentRecord.originalVideoName }}</span>
              <span>大小: {{ formatFileSize(currentRecord.originalVideoSize) }}</span>
            </div>
          </div>
        </el-col>

        <!-- 检测结果视频 -->
        <el-col :span="12">
          <div class="video-card">
            <div class="video-title">
              检测结果
              <el-tag
                :type="detectStatusType"
                size="small"
                class="title-status"
              >
                {{ detectStatusText }}
              </el-tag>
            </div>
            <div class="video-wrapper">
              <template v-if="resultVideoUrl">
                <!-- 点击播放遮罩 -->
                <div v-if="!videoStarted" class="video-play-overlay" @click="playResultVideo">
                  <div class="play-button-circle">
                    <el-icon :size="48"><VideoPlay /></el-icon>
                  </div>
                  <span class="play-hint">点击播放检测结果视频</span>
                </div>
                <video
                  ref="resultVideoRef"
                  :src="resultVideoUrl"
                  class="detect-video"
                  controls
                  preload="auto"
                  @play="onResultVideoPlay"
                  v-show="videoStarted"
                >
                  您的浏览器不支持视频播放
                </video>
              </template>
              <el-empty v-else description="等待检测">
                <template #description>
                  <div class="empty-detect-state">
                    <p>等待检测</p>
                    <p v-if="currentRecord.detectStatus === 1" class="running-tip">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      正在分析视频...
                    </p>
                  </div>
                </template>
              </el-empty>
            </div>
            <!-- 检测数据概览 -->
            <div class="detection-summary" v-if="detectionData">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="主要病变类型（仅供参考）" :span="2">
                  <span class="defect-type-text">
                    {{ getMainDefectType() }}
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="缺陷帧数量">
                  {{ detectionData.frame_results?.length || 0 }} 帧
                </el-descriptions-item>
                <el-descriptions-item label="检测总缺陷数">
                  {{ detectionData.total_defects_detected || 0 }} 个
                </el-descriptions-item>
                <el-descriptions-item label="视频帧率">
                  {{ detectionData.fps || '-' }} FPS
                </el-descriptions-item>
                <el-descriptions-item label="视频时长">
                  {{ formatDuration(detectionData.duration) }}
                </el-descriptions-item>
                <el-descriptions-item label="总帧数">
                  {{ detectionData.total_frames || '-' }} 帧
                </el-descriptions-item>
                <el-descriptions-item label="处理帧数">
                  {{ detectionData.total_frames_processed || '-' }} 帧
                </el-descriptions-item>
                <el-descriptions-item label="处理时间" :span="2">
                  {{ detectionData.processing_time_ms || '-' }} ms
                </el-descriptions-item>
              </el-descriptions>

              <!-- 缺陷类型分布 -->
              <div v-if="detectionData.defect_type_counts && Object.keys(detectionData.defect_type_counts).length > 0" class="defect-distribution">
                <div class="defect-dist-title">检测类型分布：</div>
                <div v-for="(count, type) in detectionData.defect_type_counts" :key="type" class="defect-dist-item">
                  <span class="defect-dist-type">{{ mapDefectTypeName(type) }}</span>
                  <el-progress
                    :percentage="calcDefectPercentage(count)"
                    :color="defectDistColor(type)"
                    :stroke-width="12"
                  />
                  <span class="defect-dist-count">{{ count }} 个</span>
                </div>
              </div>

              <div v-else-if="detectionData.defect_detected === false" class="no-defect-alert">
                <el-alert
                  title="正常"
                  type="success"
                  description="视频分析完成，未发现明显病变。"
                  :closable="false"
                />
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- AI分析结果 -->
    <div class="ai-analysis-area" v-if="currentRecord?.aiAnalysisResult">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>
              <el-icon><ChatLineRound /></el-icon>
              AI辅助分析报告（风机视频检测分析）
              <el-tag v-if="formatAiModelName(currentRecord.aiModel)" class="title-status" size="small" type="warning">
                {{ formatAiModelName(currentRecord.aiModel) }}
              </el-tag>
            </span>
            <span class="analysis-time">
              {{ formatDateTime(currentRecord.aiAnalysisTime) }}
            </span>
          </div>
        </template>
        <div class="analysis-content">
          <div class="analysis-text">
            <p
              v-for="(line, index) in aiAnalysisDisplayLines"
              :key="index"
              :class="{
                'analysis-disclaimer': isAnalysisDisclaimerLine(line),
                'analysis-label-line': !isAnalysisDisclaimerLine(line) && line.includes('：')
              }"
            >
              <template v-if="!isAnalysisDisclaimerLine(line) && line.includes('：')">
                <span class="analysis-label">{{ line.substring(0, line.indexOf('：') + 1) }}</span>
                {{ line.substring(line.indexOf('：') + 1) }}
              </template>
              <template v-else>
                {{ line }}
              </template>
            </p>
          </div>
        </div>
      </el-card>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, VideoPlay, ChatDotRound, ChatLineRound, Loading, Download } from '@element-plus/icons-vue'
import request from '@/utils/request.js'
import { getAccessToken } from '@/utils/request.js'
import { getConfigByGroup } from '@/utils/config'
import { isWorkspaceAutoRefreshEnabled } from '@/utils/pageRefresh'
import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'

// ==================== 响应式数据 ====================

const AI_DISCLAIMER_TEXT = '本分析报告仅供参考，仅为辅助检测分析，不替代现场专业检测和维护决策。'
const LEGACY_AI_DISCLAIMER_TEXT = '本分析报告仅供参考，仅为辅助检测分析，不替代现场专业检测和维护决策。'
const AI_MODEL_LABELS = {
  deepseek: 'Deepseek-V4',
  kimi: 'Kimi-k2.6',
  glm: '',
  doubao: '豆包'
}
const AI_ANALYSIS_SECTION_TITLES = [
  '缺陷类别分析',
  '严重程度评估',
  '严重程度初步判断',
  '时序变化分析',
  '位置与范围评估',
  '可能原因分析',
  '运维处置建议',
  '建议的运维处置方案',
  '复检与巡检建议',
  '巡检建议',
  '维护周期建议',
  '安全风险与注意事项',
  '需要注意的安全事项',
  '环境因素评估',
  '后续检测计划建议'
]

const route = useRoute()
const pagePath = '/manager/video'
let hasMounted = false
let hasActivated = false
const currentRecord = ref(null)
const detecting = ref(false)
const aiAnalyzing = ref(false)
const exporting = ref(false)
const aiProgressVisible = ref(false)
const aiAnalysisProgress = ref(0)
const selectedModel = ref('deepseek')
const aiModelOptions = ref([
  { label: AI_MODEL_LABELS.deepseek, value: 'deepseek', configured: false },
  { label: AI_MODEL_LABELS.kimi, value: 'kimi', configured: false }
])

// 模型配置
const modelPath = ref('')
const modelOptions = ref([])
const modelLoading = ref(false)
const confThreshold = ref(0.6)

const loadDefaultThreshold = async () => {
  try {
    const res = await getConfigByGroup('video', { silentForbidden: true })
    if (res.code !== '200') return
    const thresholdItem = (res.data || []).find(item => item.configKey === 'default_threshold')
    if (thresholdItem?.configValue !== undefined && thresholdItem?.configValue !== null && thresholdItem.configValue !== '') {
      confThreshold.value = Number(thresholdItem.configValue)
    }
  } catch (error) {
    console.error('????????', error)
  }
}

const skipFrames = ref(0)
let aiProgressTimer = null

// 视频播放状态
const resultVideoRef = ref(null)
const videoStarted = ref(false)

const aiProgressColors = [
  { color: '#2B7DE9', percentage: 55 },
  { color: '#06B6D4', percentage: 92 },
  { color: '#10B981', percentage: 100 }
]

// 上传数据
const uploadData = computed(() => {
  const userStr = localStorage.getItem('code_user')
  const user = userStr ? JSON.parse(userStr) : {}
  return {
    userId: user.id || 1,
    userName: user.name || '管理员'
  }
})

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`
}))

// 视频URL
const originalVideoUrl = computed(() => {
  if (!currentRecord.value?.originalVideoUrl) return ''
  if (currentRecord.value.originalVideoUrl.startsWith('http')) {
    return currentRecord.value.originalVideoUrl
  }
  return `http://localhost:1234/files/${currentRecord.value.originalVideoUrl}`
})

const resultVideoUrl = computed(() => {
  if (!currentRecord.value?.resultVideoUrl) return ''
  if (currentRecord.value.resultVideoUrl.startsWith('http')) {
    return currentRecord.value.resultVideoUrl
  }
  return `http://localhost:1234/files/${currentRecord.value.resultVideoUrl}`
})

const aiAnalysisDisplayLines = computed(() => {
  return String(currentRecord.value?.aiAnalysisResult || '').split(/\r?\n/)
})

const isAnalysisDisclaimerLine = (line = '') => {
  const text = String(line).trim()
  return text.includes('本分析报告仅供参考')
}

const isAnalysisSectionTitleLine = (line = '') => {
  const text = String(line).trim().replace(/^(?:[\s\d一二三四五六七八九十]+[.、．)]|[（(][\d一二三四五六七八九十]+[）)])\s*/, '').replace(/[：:].*$/, '').trim()
  return !text
}

const formatAiModelName = (model) => {
  if (!model) return ''
  const key = String(model).toLowerCase()
  return Object.prototype.hasOwnProperty.call(AI_MODEL_LABELS, key) ? AI_MODEL_LABELS[key] : model
}

// 风机缺陷类型映射
// damage(损伤), dirt(污垢)
const defectTypeMapping = {
  'normal': '正常',
  'Damage': '损伤',
  'damage': '损伤',
  '0': '损伤',
  'Dirt': '污垢',
  'dirt': '污垢',
  '1': '污垢'
}

const mapDefectTypeName = (value) => {
  if (value === null || value === undefined) return ''
  const rawValue = String(value).trim()
  if (!rawValue) return ''
  const lowerValue = rawValue.toLowerCase()
  if (['未知', 'unknown', 'none', 'null', 'undefined', '-'].includes(lowerValue)) return ''
  return defectTypeMapping[lowerValue] ||
    defectTypeMapping[rawValue] ||
    rawValue
}

// 解析检测数据
const detectionData = computed(() => {
  if (!currentRecord.value?.detectionData) return null
  try {
    const data = JSON.parse(currentRecord.value.detectionData)
    return data
  } catch {
    return null
  }
})

// 检测状态
const detectStatusType = computed(() => {
  const status = currentRecord.value?.detectStatus
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
})

const detectStatusText = computed(() => {
  const status = currentRecord.value?.detectStatus
  const texts = { 0: '待检测', 1: '检测中', 2: '检测完成', 3: '检测失败' }
  return texts[status] || '未知'
})

// 是否可进行AI分析
const canAiAnalyze = computed(() => {
  return currentRecord.value?.detectStatus === 2 &&
         currentRecord.value?.aiStatus !== 1
})

// ==================== 方法 ====================

const playResultVideo = () => {
  videoStarted.value = true
  // 等待DOM更新后，等视频加载完成再播放
  nextTick(() => {
    const video = resultVideoRef.value
    if (video) {
      // 确保视频源已加载
      video.load()
      const tryPlay = () => {
        video.play().catch(e => {
          console.warn('视频播放失败(重试中):', e)
          // 若浏览器阻止自动播放，用户手动再点一次即可
        })
      }
      // 如果视频已足够加载，直接播放；否则等待 canplay 事件
      if (video.readyState >= 2) {
        tryPlay()
      } else {
        video.addEventListener('canplay', tryPlay, { once: true })
        // 兜底：5秒后无论是否加载完成都尝试播放
        setTimeout(() => {
          if (video.paused) tryPlay()
        }, 5000)
      }
    }
  })
}

const onResultVideoPlay = () => {
  // 视频已经开始播放
}

const clearAiProgressTimer = () => {
  if (aiProgressTimer) {
    clearInterval(aiProgressTimer)
    aiProgressTimer = null
  }
}

const startAiProgress = () => {
  clearAiProgressTimer()
  aiProgressVisible.value = true
  aiAnalysisProgress.value = 3
  aiProgressTimer = setInterval(() => {
    if (aiAnalysisProgress.value >= 92) return
    const step = aiAnalysisProgress.value < 35 ? 7 : aiAnalysisProgress.value < 70 ? 4 : 1
    aiAnalysisProgress.value = Math.min(92, aiAnalysisProgress.value + step)
  }, 500)
}

const completeAiProgress = () => {
  clearAiProgressTimer()
  aiAnalysisProgress.value = 100
  setTimeout(() => {
    aiProgressVisible.value = false
  }, 1200)
}

const stopAiProgress = () => {
  clearAiProgressTimer()
  aiProgressVisible.value = false
  aiAnalysisProgress.value = 0
}

// 上传前校验
const beforeUpload = (file) => {
  const validExts = ['mp4', 'avi', 'mov', 'mkv', 'wmv', 'flv', 'webm']
  const ext = file.name.split('.').pop().toLowerCase()

  if (!validExts.includes(ext)) {
    ElMessage.error('请上传 MP4、AVI、MOV、MKV、WMV、FLV、WEBM 格式的视频文件')
    return false
  }

  const maxSize = 500 * 1024 * 1024 // 500MB
  if (file.size > maxSize) {
    ElMessage.error('视频文件大小不能超过 500MB')
    return false
  }

  return true
}

// 上传成功
const handleUploadSuccess = (res) => {
  if (res.code === '200') {
    ElMessage.success('视频上传成功')
    videoStarted.value = false
    loadRecordById(res.data.id)
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

// 上传失败
const handleUploadError = (error) => {
  console.error('上传错误详情:', error)
  let msg = '上传失败'
  if (error?.status === 0) {
    msg = '无法连接到服务器，请确保后端服务已启动 (http://localhost:1234)'
  } else if (error?.message) {
    msg = '上传失败: ' + error.message
  }
  ElMessage.error(msg)
}

// 开始检测
const startDetection = async () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先上传视频')
    return
  }

  if (!modelPath.value) {
    ElMessage.warning('请选择检测模型')
    return
  }

  detecting.value = true
  videoStarted.value = false
  try {
    const res = await request.post(`/video/startDetect/${currentRecord.value.id}`, null, {
      params: {
        ptPath: modelPath.value,
        conf: confThreshold.value,
        skipFrames: skipFrames.value
      },
      timeout: 600000 // 10分钟超时
    })
    if (res.code === '200') {
      ElMessage.success('视频检测完成')
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '检测失败')
    }
  } catch (error) {
    console.error('视频检测失败:', error)
    ElMessage.error(error.response?.data?.msg || '视频检测请求失败，处理超时或服务异常')
  } finally {
    detecting.value = false
  }
}

// AI辅助分析
const startAiAnalysis = async () => {
  if (!selectedModel.value) {
    ElMessage.warning('请选择AI模型')
    return
  }

  if (!currentRecord.value?.id) {
    ElMessage.warning('请先选择检测记录')
    return
  }

  aiAnalyzing.value = true
  startAiProgress()
  try {
    const res = await request.post(
      `/video/aiAnalysis/${currentRecord.value.id}?model=${selectedModel.value}`
    )
    if (res.code === '200') {
      completeAiProgress()
      ElMessage.success('AI大模型辅助分析报告已生成')
      currentRecord.value = res.data
    } else {
      stopAiProgress()
      ElMessage.error(res.msg || '分析失败')
    }
  } catch (error) {
    stopAiProgress()
    ElMessage.error('AI分析请求失败')
  } finally {
    aiAnalyzing.value = false
  }
}

// 获取主要缺陷类型
const getMainDefectType = () => {
  if (!detectionData.value) return '未知'
  if (!detectionData.value.defect_detected) return '正常'
  if (detectionData.value.main_defect_type_cn) return detectionData.value.main_defect_type_cn
  if (detectionData.value.main_defect_type) return mapDefectTypeName(detectionData.value.main_defect_type)
  return '未知'
}

// 计算缺陷占比
const calcDefectPercentage = (count) => {
  const total = detectionData.value?.total_defects_detected || 1
  return Math.round((count / total) * 100)
}

// 检测分布颜色
const defectDistColor = (type) => {
  const lowerType = (type || '').toLowerCase()
  if (lowerType.includes('damage')) return '#EF4444'
  if (lowerType.includes('dirt')) return '#F59E0B'
  if (lowerType.includes('normal')) return '#10B981'
  return '#94A3B8'
}

// ==================== PDF 工具函数 ====================

const escapeHtml = (value) => {
  return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

const drawText = (ctx, text, x, y, options = {}) => {
  const { align = 'left', color = '#1A2D3D', font = '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' } = options
  ctx.font = font
  ctx.fillStyle = color
  ctx.textAlign = align
  ctx.textBaseline = 'top'
  if (align === 'center') ctx.fillText(text, x, y)
  else if (align === 'right') ctx.fillText(text, x, y)
  else ctx.fillText(text, x, y)
}

const wrapCanvasAnalysisText = (ctx, text, maxWidth) => {
  const lines = []
  const paragraphs = String(text || '-').split('\n')
  paragraphs.forEach((paragraph) => {
    const content = paragraph.trimEnd()
    const isDisclaimer = isAnalysisDisclaimerLine(content)
    const isSectionTitle = isAnalysisSectionTitleLine(content)
    if (!content) {
      lines.push({ text: '', isDisclaimer: false, isSectionTitle: false })
      return
    }
    ctx.font = `${isDisclaimer || isSectionTitle ? '700 ' : ''}14px "Microsoft YaHei", "SimHei", Arial, sans-serif`
    let line = ''
    for (const char of content) {
      const testLine = line + char
      if (ctx.measureText(testLine).width > maxWidth && line) {
        lines.push({ text: line, isDisclaimer, isSectionTitle })
        line = char
      } else {
        line = testLine
      }
    }
    if (line) lines.push({ text: line, isDisclaimer, isSectionTitle })
  })
  return lines
}

const createAiAnalysisCanvas = (record) => {
  const scale = 2
  const width = 800
  const padding = 30
  const contentWidth = width - padding * 2
  const lineHeight = 24
  const measureCanvas = document.createElement('canvas')
  const measureCtx = measureCanvas.getContext('2d')
  measureCtx.font = '14px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  const lines = wrapCanvasAnalysisText(measureCtx, record?.aiAnalysisResult || '-', contentWidth)
  const height = Math.max(210, 140 + lines.length * lineHeight)
  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  const ctx = canvas.getContext('2d')
  ctx.scale(scale, scale)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  drawText(ctx, 'AI 辅助分析报告', width / 2, 20, { align: 'center', color: '#1A2D3D', font: '700 22px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  ctx.strokeStyle = '#06B6D4'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(padding, 60); ctx.lineTo(width - padding, 60); ctx.stroke()
  drawText(ctx, `分析模型: ${formatAiModelName(record?.aiModel) || '-'}`, padding, 82, { color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, `分析时间: ${formatDateTime(record?.aiAnalysisTime)}`, width - padding, 82, { align: 'right', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  let y = 120
  const safeBreaksPx = []
  lines.forEach((line) => {
    const lineY = y; const text = line.text; const isDisclaimer = line.isDisclaimer
    if (!isDisclaimer && text.includes('：')) {
      const colonIdx = text.indexOf('：'); const label = text.substring(0, colonIdx + 1); const content = text.substring(colonIdx + 1)
      drawText(ctx, label, padding, y, { color: '#1A2D3D', font: '700 14px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
      if (content) drawText(ctx, content, padding + ctx.measureText(label).width, y, { color: '#1A2D3D', font: '14px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
    } else {
      drawText(ctx, text, padding, y, { color: isDisclaimer ? '#EF4444' : '#1E293B', font: `${isDisclaimer || line.isSectionTitle ? '700 ' : ''}14px "Microsoft YaHei", "SimHei", Arial, sans-serif` })
    }
    const rowHeight = text ? lineHeight : lineHeight * 0.65; y += rowHeight; safeBreaksPx.push(Math.round((lineY + rowHeight - 3) * scale))
  })
  canvas.pdfSafeBreaksPx = safeBreaksPx
  return canvas
}

const normalizeSafeBreaksPx = (breaks, canvasHeight) => {
  return [...new Set((breaks || []).map((b) => Math.round(Number(b))).filter((b) => Number.isFinite(b) && b > 0 && b < canvasHeight))].sort((a, b) => a - b)
}

const getSafeSliceHeightPx = (sourceY, maxSliceHeightPx, canvasHeight, safeBreaksPx, minSliceHeightPx, tolerancePx) => {
  const remainingPx = canvasHeight - sourceY
  let sliceHeightPx = Math.min(maxSliceHeightPx, remainingPx)
  if (safeBreaksPx && safeBreaksPx.length > 0) {
    const nextBreak = safeBreaksPx.find((b) => b > sourceY + minSliceHeightPx && b <= sourceY + sliceHeightPx + tolerancePx)
    if (nextBreak) sliceHeightPx = nextBreak - sourceY
  }
  return Math.max(minSliceHeightPx, Math.min(sliceHeightPx, remainingPx))
}

const addCanvasToPdfPaged = (pdf, canvas, options = {}) => {
  const { y = 10, width = 190, marginTop = 0, marginBottom = 10 } = options
  const safeBreaksPx = normalizeSafeBreaksPx(canvas.pdfSafeBreaksPx, canvas.height)
  const minSliceHeightPx = options.minSliceHeightPx ?? Math.max(24, Math.round(canvas.width * 0.03))
  const tolerancePx = 5
  const maxSliceHeightPx = 1200
  let sourceY = 0
  const pdfPageHeight = 297
  const marginTopMm = marginTop, marginBottomMm = marginBottom
  const availableHeightMm = pdfPageHeight - marginTopMm - marginBottomMm
  const scaleRatio = width / canvas.width
  while (sourceY < canvas.height - tolerancePx) {
    const remainingPx = canvas.height - sourceY
    const sliceHeightPx = getSafeSliceHeightPx(sourceY, maxSliceHeightPx, canvas.height, safeBreaksPx, minSliceHeightPx, tolerancePx)
    if (sliceHeightPx <= 0) break
    const sliceCanvas = document.createElement('canvas')
    sliceCanvas.width = canvas.width; sliceCanvas.height = sliceHeightPx
    const sliceCtx = sliceCanvas.getContext('2d')
    sliceCtx.drawImage(canvas, 0, sourceY, canvas.width, sliceHeightPx, 0, 0, canvas.width, sliceHeightPx)
    const sliceHeightMm = sliceHeightPx * scaleRatio
    if (sourceY > 0) pdf.addPage()
    pdf.addImage(sliceCanvas.toDataURL('image/jpeg', 0.92), 'JPEG', (pdfPageHeight - 297) / 2 + 10, marginTopMm + 10, width, sliceHeightMm, undefined, 'FAST')
    sourceY += sliceHeightPx
  }
}

const createReportHeaderCanvas = (record, data) => {
  const canvas = document.createElement('canvas')
  const scale = 2; const width = 800; const height = 150
  canvas.width = width * scale; canvas.height = height * scale
  canvas.style.width = `${width}px`; canvas.style.height = `${height}px`
  const ctx = canvas.getContext('2d'); ctx.scale(scale, scale)
  ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, width, height)
  drawText(ctx, '风机视频检测分析报告', width / 2, 16, { align: 'center', color: '#1A2D3D', font: '700 24px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, '基于大模型和多模态数据融合的风机缺陷智能检测系统', width / 2, 52, { align: 'center', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  ctx.strokeStyle = '#2B7DE9'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(30, 76); ctx.lineTo(width - 30, 76); ctx.stroke()
  drawText(ctx, `报告编号: ${record?.id || '-'}`, 35, 94, { color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, `生成时间: ${new Date().toLocaleString('zh-CN')}`, width / 2, 94, { align: 'center', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, `检测模型: ${getModelName(record?.modelName || modelPath.value)}`, width - 35, 94, { align: 'right', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  if (data) {
    ctx.fillStyle = '#F8FAFF'; ctx.fillRect(30, 125, width - 60, 0)
    ctx.strokeStyle = '#E2E8F0'; ctx.lineWidth = 1
  }
  return canvas
}

const getResultVideoFileName = () => {
  const id = currentRecord.value?.id || Date.now()
  const originalName = currentRecord.value?.originalVideoName || `video_${id}.mp4`
  const dotIndex = originalName.lastIndexOf('.')
  const baseName = dotIndex > 0 ? originalName.slice(0, dotIndex) : originalName
  const extension = dotIndex > 0 ? originalName.slice(dotIndex + 1) : 'mp4'
  return `${baseName}_检测结果.${extension || 'mp4'}`
}

// 导出报告（PDF格式）
const exportVideo = () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先进行检测')
    return
  }
  const token = getAccessToken()
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  const a = document.createElement('a')
  a.href = `http://localhost:1234/video/download/${currentRecord.value.id}${query}`
  a.download = getResultVideoFileName()
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  ElMessage.success('正在下载检测视频')
}

// 加载记录详情
const loadRecordById = async (id) => {
  if (!id) return
  try {
    const res = await request.get(`/video/selectById/${id}`)
    if (res.code === '200') {
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '加载记录失败')
    }
  } catch (error) {
    console.error('加载记录失败', error)
    ElMessage.error('加载记录失败')
  }
}

const loadRecordFromRoute = (recordId) => {
  if (recordId) {
    loadRecordById(recordId)
  } else {
    currentRecord.value = null
    videoStarted.value = false
  }
}

// ==================== 工具函数 ====================

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

const formatDuration = (seconds) => {
  if (!seconds && seconds !== 0) return '-'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}分${s}秒`
}

// 获取模型名称
const getModelName = (fullPath) => {
  const matched = modelOptions.value.find(item => item.value === fullPath)
  if (matched?.label) return matched.label
  if (!fullPath) return '-'
  const parts = String(fullPath).split(/[\\/]/).filter(Boolean)
  if (!parts.length) return '-'

  const fileName = parts[parts.length - 1]
  const parentName = parts[parts.length - 2]
  const grandParentName = parts[parts.length - 3]
  const lowerFileName = fileName.toLowerCase()

  if ((lowerFileName === 'best.pt' || lowerFileName === 'last.pt') && parentName) {
    return parentName.toLowerCase() === 'weights' && grandParentName ? grandParentName : parentName
  }

  return fileName.replace(/\.(pt|engine|trt)$/i, '')
}

// 加载AI模型配置状态
const loadAiConfigStatus = async () => {
  try {
    const res = await request.get('/video/aiConfigStatus')
    if (res.code === '200') {
      const status = res.data
      aiModelOptions.value = [
        { label: AI_MODEL_LABELS.deepseek, value: 'deepseek', configured: status.deepseek },
        { label: AI_MODEL_LABELS.kimi, value: 'kimi', configured: status.kimi }
      ]
      const firstConfigured = aiModelOptions.value.find(m => m.configured)
      if (firstConfigured) {
        selectedModel.value = firstConfigured.value
      }
    }
  } catch (error) {
    console.error('加载AI配置状态失败', error)
  }
}

// 加载可用模型列表
const loadModels = async () => {
  modelLoading.value = true
  try {
    const res = await request.get('/video/listModels')
    if (res.code === '200') {
      modelOptions.value = res.data || []
      if (modelOptions.value.length > 0 && !modelPath.value) {
        modelPath.value = modelOptions.value[0].value
      }
    } else {
      ElMessage.warning('加载模型列表失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
    ElMessage.error('无法加载模型列表，请检查后端服务')
  } finally {
    modelLoading.value = false
  }
}

const refreshPageData = async () => {
  if (route.query.recordId) {
    await loadRecordById(route.query.recordId)
  } else if (currentRecord.value?.id) {
    await loadRecordById(currentRecord.value.id)
  }
  loadDefaultThreshold()
  loadModels()
  loadAiConfigStatus()
}

// ==================== 生命周期 ====================

onMounted(() => {
  hasMounted = true
  loadRecordFromRoute(route.query.recordId)
  loadDefaultThreshold()
  loadModels()
  loadAiConfigStatus()
})

onActivated(async () => {
  if (!hasActivated) {
    hasActivated = true
    return
  }
  if (!hasMounted) return
  if (route.path !== pagePath) return
  if (await isWorkspaceAutoRefreshEnabled()) {
    refreshPageData()
  }
})

onUnmounted(() => {
  clearAiProgressTimer()
})

watch(
  () => route.query.recordId,
  (recordId) => {
    if (route.path !== pagePath) return
    loadRecordFromRoute(recordId)
  }
)
</script>

<style scoped>
/* ===== 工业科技主题 - 风机视频检测页面样式 ===== */

.video-container {
  padding: 24px;
  min-height: calc(100vh - 60px);
  background: var(--app-bg);
}

/* ===== 页面头部 - 医疗科技风格 ===== */
.page-header {
  margin-bottom: 24px;
  padding: 24px 28px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #2B7DE9 0%, #06B6D4 70%, #10B981 100%);
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text);
  letter-spacing: -0.3px;
}

.subtitle {
  color: var(--app-text-muted);
  margin: 0;
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0.2px;
}

/* ===== 操作区域 - 白色卡片 ===== */
.operation-area {
  display: flex;
  flex-direction: column;
  margin-bottom: 24px;
  padding: 18px 20px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  gap: 14px;
}

.operation-row {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  overflow-x: auto;
}

.report-operation-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

/* ===== 选择器样式 ===== */
.model-select {
  width: 200px;
  flex: 0 0 200px;
}

.threshold-input {
  width: 120px;
  flex: 0 0 120px;
}

.skip-input {
  width: 100px;
  flex: 0 0 100px;
}

.control-button {
  flex: 0 0 auto;
  margin-left: 0;
}

.upload-component {
  flex: 0 0 auto;
}

/* ===== 检测进度面板 ===== */
.detect-progress-panel {
  margin-bottom: 24px;
  padding: 18px 22px;
  background: linear-gradient(135deg, #E8F0FE 0%, #F0F7FF 100%);
  border: 1px solid #B3CEF8;
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.detect-progress-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #1A5FC7;
  font-weight: 600;
  font-size: 15px;
}

/* ===== AI进度面板 ===== */
.ai-progress-panel {
  margin-bottom: 24px;
  padding: 18px 22px;
  background: linear-gradient(135deg, #E8F0FE 0%, #F0F7FF 100%);
  border: 1px solid #B3CEF8;
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  position: relative;
}

.ai-progress-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #2B7DE9 0%, #06B6D4 100%);
  border-radius: var(--app-radius) 0 0 var(--app-radius);
}

.ai-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #1A5FC7;
  font-weight: 600;
}

.ai-progress-header strong {
  color: #2B7DE9;
  font-size: 18px;
}

/* ===== 视频对比区域 ===== */
.video-compare-area {
  margin-bottom: 24px;
}

.video-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  overflow: hidden;
  height: 100%;
  transition: all 0.3s ease;
}

.video-card:hover {
  box-shadow: var(--app-shadow-strong);
  border-color: var(--app-border-strong);
}

.video-title {
  padding: 14px 20px;
  background: linear-gradient(180deg, #F8FAFF 0%, #EFF4FF 100%);
  color: var(--app-text);
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
}

.title-status {
  margin-left: 8px;
}

.video-wrapper {
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F8FAFF;
  padding: 16px;
  position: relative;
  overflow: hidden;
}

.detect-video {
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: var(--app-radius-sm);
  background: #000;
}

/* 视频播放遮罩 */
.video-play-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(0,0,0,0.6) 0%, rgba(0,0,0,0.4) 100%);
  cursor: pointer;
  z-index: 10;
  transition: all 0.3s ease;
  border-radius: var(--app-radius-sm);
}

.video-play-overlay:hover {
  background: linear-gradient(135deg, rgba(0,0,0,0.7) 0%, rgba(0,0,0,0.5) 100%);
}

.play-button-circle {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  border: 3px solid rgba(255, 255, 255, 0.6);
}

.video-play-overlay:hover .play-button-circle {
  background: rgba(255, 255, 255, 0.35);
  transform: scale(1.08);
}

.play-button-circle .el-icon {
  color: #ffffff;
  margin-left: 4px;
}

.play-hint {
  margin-top: 18px;
  color: #ffffff;
  font-size: 15px;
  font-weight: 500;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}

.empty-detect-state {
  text-align: center;
  color: var(--app-text-muted);
}

.empty-detect-state p {
  margin: 8px 0;
}

.running-tip {
  color: var(--app-primary);
  margin-top: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.video-info {
  padding: 10px 20px;
  font-size: 12px;
  color: var(--app-text-muted);
  background: var(--app-surface-soft);
  border-top: 1px solid var(--app-border);
}

.video-info span {
  margin-right: 20px;
}

.detection-summary {
  padding: 16px 20px;
  border-top: 1px solid var(--app-border);
}

.defect-type-text {
  display: inline-block;
  color: var(--app-danger);
  font-weight: 600;
  line-height: 1.4;
}

/* 缺陷分布样式 */
.defect-distribution {
  margin-top: 14px;
  padding: 14px 16px;
  background: var(--app-surface-soft);
  border-radius: var(--app-radius-sm);
}

.defect-dist-title {
  font-weight: 600;
  color: var(--app-text);
  margin-bottom: 10px;
  font-size: 14px;
}

.defect-dist-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.defect-dist-item:last-child {
  margin-bottom: 0;
}

.defect-dist-type {
  min-width: 80px;
  font-weight: 500;
  color: var(--app-text);
  font-size: 13px;
  flex-shrink: 0;
}

.defect-dist-item :deep(.el-progress) {
  flex: 1;
}

.defect-dist-count {
  min-width: 50px;
  text-align: right;
  color: var(--app-text-muted);
  font-size: 13px;
  flex-shrink: 0;
}

.no-defect-alert {
  margin-top: 12px;
}

/* ===== AI分析区域 ===== */
.ai-analysis-area {
  margin-bottom: 24px;
}

.ai-analysis-area :deep(.el-card) {
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  border: 1px solid var(--app-border);
}

.ai-analysis-area :deep(.el-card__header) {
  background: linear-gradient(180deg, #FDF0F1 0%, #FEF7F8 100%);
  border-bottom: 1px solid var(--app-border);
  padding: 14px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.analysis-time {
  color: var(--app-text-muted);
  font-size: 12px;
}

.analysis-content {
  max-height: 400px;
  overflow-y: auto;
  padding: 4px;
}

.analysis-text {
  margin: 0;
  font-family: inherit;
  line-height: 1.8;
  color: var(--app-text);
  font-size: 14px;
}

.analysis-text p {
  min-height: 1.8em;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

.analysis-disclaimer {
  color: var(--app-danger);
  font-weight: 700;
  background: var(--app-danger-soft);
  padding: 8px 12px;
  border-radius: var(--app-radius-sm);
  margin-top: 8px !important;
}

.analysis-label {
  font-weight: 600;
  color: var(--app-text);
}

/* ===== Element 组件深度样式优化 ===== */
:deep(.el-button .el-icon) {
  margin-right: 6px;
}

:deep(.el-card__header) {
  background: var(--app-surface-soft);
  border-bottom-color: var(--app-border);
}

:deep(.el-card__body) {
  background: var(--app-surface);
}

/* ===== 描述列表样式 ===== */
:deep(.el-descriptions__label) {
  color: var(--app-text-secondary);
  font-weight: 500;
}

:deep(.el-descriptions__content) {
  color: var(--app-text);
}

/* ===== 响应式适配 ===== */
@media (max-width: 900px) {
  .video-container {
    padding: 16px;
  }

  .page-header {
    padding: 18px 20px;
  }

  .page-header h2 {
    font-size: 20px;
  }

  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .operation-row {
    flex-wrap: wrap;
  }

  .video-wrapper {
    height: 260px;
    padding: 12px;
  }
}
</style>
