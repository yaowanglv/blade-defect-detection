<template>
  <div class="model-config-panel">
    <h3 class="panel-title">模型扫描根目录</h3>
    <div class="input-row">
      <el-input v-model="modelRootPath" placeholder="请输入模型扫描根目录" clearable />
      <el-button type="primary" :loading="scanning" @click="handleScan">扫描模型</el-button>
      <el-button @click="handleResetPath">重置为默认</el-button>
    </div>

    <div v-if="scannedModels.length > 0" class="model-list">
      <div class="table-header">
        <h3 class="panel-title">已扫描模型（{{ visibleModelCount }} / {{ scannedModels.length }}）</h3>
        <span class="hint-text">删除仅从前端列表隐藏模型，不会删除源文件</span>
      </div>

      <el-table :data="scannedModels" size="small" style="width: 100%" row-key="value">
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="isModelHidden(row) ? 'info' : 'success'" size="small">
              {{ isModelHidden(row) ? '已移除' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="originalLabel" label="原模型名称" min-width="160" />
        <el-table-column label="自定义名称" min-width="220">
          <template #default="{ row }">
            <el-input
              v-model="row.customLabel"
              maxlength="60"
              clearable
              :disabled="isModelHidden(row)"
              placeholder="留空则显示原模型名称"
            />
          </template>
        </el-table-column>
        <el-table-column prop="value" label="模型路径" min-width="320" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isModelHidden(row)"
              link
              type="primary"
              @click="restoreModel(row)"
            >
              恢复
            </el-button>
            <el-popconfirm
              v-else
              title="只从前端列表移除，不会删除源文件。确认移除？"
              confirm-button-text="移除"
              cancel-button-text="取消"
              @confirm="hideModel(row)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <h3 class="panel-title threshold-title">默认置信度阈值</h3>
    <div class="input-row">
      <el-input-number v-model="defaultThreshold" :min="0" :max="1" :step="0.1" :precision="2" />
      <span class="hint-text">范围 0-1，步长 0.1</span>
      <el-button @click="handleResetThreshold">重置为默认</el-button>
    </div>

    <template v-if="isDetectGroup">
      <h3 class="panel-title dataset-title">数据集图像配对</h3>
      <div class="config-field">
        <label class="field-label">数据集根目录</label>
        <div class="input-row">
          <el-input
            v-model="datasetRootPath"
            placeholder="例如: D:\opendataset\Wind-Turbine"
            clearable
          />
          <el-button @click="handleResetDatasetRoot">重置为默认</el-button>
        </div>
      </div>
      <div class="config-field">
        <label class="field-label">图像匹配逻辑</label>
        <div class="input-row">
          <el-radio-group v-model="imagePairingMode">
            <el-radio-button
              v-for="item in imagePairingModeOptions"
              :key="item.value"
              :label="item.value"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <span class="hint-text">{{ selectedPairingModeHint }}</span>
          <el-button @click="handleResetImagePairingMode">重置为默认</el-button>
        </div>
      </div>
    </template>

    <div class="action-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getConfigByGroup,
  listConfigModels,
  updateConfig,
  updateHiddenModels,
  updateModelAliases
} from '@/utils/config'

const props = defineProps({
  group: {
    type: String,
    required: true
  }
})

const IMAGE_PAIRING_MODES = {
  SPLIT_DIRS: 'split_dirs',
  SAME_DIR_SUFFIX: 'same_dir_suffix'
}
const DEFAULT_DATASET_ROOT = 'D:/opendataset/Wind-Turbine'

const modelRootPath = ref('')
const defaultThreshold = ref(props.group === 'detect' ? 0.55 : 0.6)
const datasetRootPath = ref(DEFAULT_DATASET_ROOT)
const datasetRootDefaultPath = ref(DEFAULT_DATASET_ROOT)
const imagePairingMode = ref(IMAGE_PAIRING_MODES.SPLIT_DIRS)
const imagePairingModeDefault = ref(IMAGE_PAIRING_MODES.SPLIT_DIRS)
const scannedModels = ref([])
const hiddenModelPaths = ref([])
const scanning = ref(false)
const saving = ref(false)
const defaultPath = ref('')
const defaultThresholdValue = ref(props.group === 'detect' ? 0.55 : 0.6)

const isDetectGroup = computed(() => props.group === 'detect')

const imagePairingModeOptions = [
  {
    label: '目录分离',
    value: IMAGE_PAIRING_MODES.SPLIT_DIRS,
    hint: '旧逻辑：RGB 位于 images，IR 位于 images_ir。'
  },
  {
    label: '同文件夹 -ir 后缀',
    value: IMAGE_PAIRING_MODES.SAME_DIR_SUFFIX,
    hint: '新逻辑：1.png 为 RGB，1-ir.png 为 IR，二者在同一文件夹。'
  }
]

const visibleModelCount = computed(() =>
  scannedModels.value.filter(item => !isModelHidden(item)).length
)

const selectedPairingModeHint = computed(() => {
  return imagePairingModeOptions.find(item => item.value === imagePairingMode.value)?.hint || ''
})

const loadConfig = async () => {
  try {
    const res = await getConfigByGroup(props.group)
    if (res.code !== '200') {
      ElMessage.error(res.msg || '加载配置失败')
      return
    }
    for (const item of res.data || []) {
      if (item.configKey === 'model_root_path') {
        modelRootPath.value = item.configValue || ''
        defaultPath.value = item.defaultValue || ''
      }
      if (item.configKey === 'default_threshold') {
        defaultThreshold.value = Number(item.configValue || defaultThreshold.value)
        defaultThresholdValue.value = Number(item.defaultValue || defaultThresholdValue.value)
      }
      if (item.configKey === 'hidden_models') {
        hiddenModelPaths.value = parseHiddenModels(item.configValue)
      }
      if (isDetectGroup.value && item.configKey === 'dataset_root_path') {
        datasetRootPath.value = item.configValue || DEFAULT_DATASET_ROOT
        datasetRootDefaultPath.value = item.defaultValue || DEFAULT_DATASET_ROOT
      }
      if (isDetectGroup.value && item.configKey === 'image_pairing_mode') {
        imagePairingMode.value = Object.values(IMAGE_PAIRING_MODES).includes(item.configValue)
          ? item.configValue
          : IMAGE_PAIRING_MODES.SPLIT_DIRS
        imagePairingModeDefault.value = Object.values(IMAGE_PAIRING_MODES).includes(item.defaultValue)
          ? item.defaultValue
          : IMAGE_PAIRING_MODES.SPLIT_DIRS
      }
    }
  } catch (error) {
    ElMessage.error('加载配置失败')
  }
}

const parseHiddenModels = (value) => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : []
  } catch {
    return []
  }
}

const normalizeModelRows = (models) => {
  return (models || []).map((item) => ({
    ...item,
    originalLabel: item.originalLabel || item.label || '',
    customLabel: item.customLabel || '',
    label: item.label || item.originalLabel || '',
    hidden: item.hidden === true || item.hidden === 'true'
  }))
}

const handleScan = async () => {
  scanning.value = true
  try {
    const res = await listConfigModels(props.group, modelRootPath.value, true)
    if (res.code === '200') {
      scannedModels.value = normalizeModelRows(res.data)
      hiddenModelPaths.value = scannedModels.value
        .filter(item => item.hidden)
        .map(item => item.value)
      ElMessage.success(`扫描完成，共找到 ${scannedModels.value.length} 个模型`)
    } else {
      ElMessage.error(res.msg || '扫描失败')
    }
  } catch (error) {
    ElMessage.error('扫描失败')
  } finally {
    scanning.value = false
  }
}

const handleResetPath = () => {
  modelRootPath.value = defaultPath.value
}

const handleResetThreshold = () => {
  defaultThreshold.value = defaultThresholdValue.value
}

const handleResetDatasetRoot = () => {
  datasetRootPath.value = datasetRootDefaultPath.value
}

const handleResetImagePairingMode = () => {
  imagePairingMode.value = imagePairingModeDefault.value
}

const isModelHidden = (model) => hiddenModelPaths.value.includes(model.value)

const hideModel = (model) => {
  if (!hiddenModelPaths.value.includes(model.value)) {
    hiddenModelPaths.value = [...hiddenModelPaths.value, model.value]
  }
  model.hidden = true
}

const restoreModel = (model) => {
  hiddenModelPaths.value = hiddenModelPaths.value.filter(item => item !== model.value)
  model.hidden = false
}

const buildAliasesPayload = () => {
  const aliases = {}
  for (const item of scannedModels.value) {
    aliases[item.value] = (item.customLabel || '').trim()
  }
  return aliases
}

const handleSave = async () => {
  saving.value = true
  try {
    const updates = [
      updateConfig({
        configKey: 'model_root_path',
        configGroup: props.group,
        configValue: modelRootPath.value
      }),
      updateConfig({
        configKey: 'default_threshold',
        configGroup: props.group,
        configValue: String(defaultThreshold.value)
      }),
      updateModelAliases(props.group, buildAliasesPayload()),
      updateHiddenModels(props.group, hiddenModelPaths.value)
    ]
    if (isDetectGroup.value) {
      updates.push(
        updateConfig({
          configKey: 'dataset_root_path',
          configGroup: props.group,
          configValue: datasetRootPath.value,
          configLabel: '数据集根目录',
          defaultValue: datasetRootDefaultPath.value,
          sortOrder: 3
        }),
        updateConfig({
          configKey: 'image_pairing_mode',
          configGroup: props.group,
          configValue: imagePairingMode.value,
          configLabel: '图像匹配逻辑',
          defaultValue: imagePairingModeDefault.value,
          sortOrder: 4
        })
      )
    }
    const responses = await Promise.all(updates)
    const failed = responses.find(item => item.code !== '200')
    if (failed) {
      ElMessage.error(failed.msg || '保存失败')
      return
    }
    await handleScan()
    ElMessage.success('配置已保存')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadConfig()
  await handleScan()
})
</script>

<style scoped>
.model-config-panel {
  padding: 8px 0;
}

.panel-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--app-text);
  font-weight: 600;
}

.threshold-title,
.dataset-title {
  margin-top: 24px;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.config-field {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: var(--app-text);
  font-weight: 500;
}

.model-list {
  margin-bottom: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.hint-text {
  font-size: 13px;
  color: var(--app-text-muted);
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .input-row,
  .table-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
