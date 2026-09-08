<template>
  <div class="ai-config-panel" v-loading="loading">
    <div v-for="model in modelGroups" :key="model.name" class="model-card">
      <div class="model-header">
        <img :src="model.icon" :alt="model.label" class="model-icon" />
        <h3>{{ model.label }}</h3>
      </div>

      <div class="config-item">
        <label>{{ model.apiKey.label || 'API Key' }}</label>
        <div class="input-row">
          <el-input
            v-model="model.apiKey.value"
            :type="model.apiKey.showPassword ? 'text' : 'password'"
            placeholder="请输入 API Key"
            autocomplete="off"
            show-word-limit
            maxlength="500"
          >
            <template #suffix>
              <el-tooltip :content="model.apiKey.showPassword ? '隐藏密钥' : '显示密钥'" placement="top">
                <el-icon class="password-toggle" @click="model.apiKey.showPassword = !model.apiKey.showPassword">
                  <View v-if="model.apiKey.showPassword" />
                  <Hide v-else />
                </el-icon>
              </el-tooltip>
            </template>
          </el-input>
          <el-button @click="handleReset(model)">重置</el-button>
        </div>

        <div class="status-row">
          <span :class="['status', model.configured ? 'configured' : 'unconfigured']">
            状态: {{ model.configured ? '已配置' : '未配置' }}
          </span>
          <el-button
            type="success"
            :loading="model.testing"
            :disabled="!model.configured && !model.apiKey.value"
            @click="handleTest(model)"
          >
            测试连接
          </el-button>
        </div>

        <div v-if="model.testResult" :class="['test-result', model.testResult.success ? 'success' : 'error']">
          {{ model.testResult.message }}
        </div>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      title="API Key 保存后立即生效；清空并保存后将自动回退到 application.yml 默认配置。"
      class="config-alert"
    />

    <div class="action-bar">
      <el-button @click="loadConfig">刷新</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Hide, View } from '@element-plus/icons-vue'
import {
  getAiConfigStatus,
  getConfigByGroup,
  testAiConnection,
  updateConfig
} from '@/utils/config'
import deepseekLogo from '@/assets/imgs/deepseek - logo.png'
import glmLogo from '@/assets/imgs/GLM.webp'
import kimiLogo from '@/assets/imgs/kimi.webp'

const AI_CONFIG_GROUP = 'ai_model'

const modelConfig = [
  { name: 'deepseek', label: 'DeepSeek', keyField: 'deepseek_api_key', icon: deepseekLogo, sortOrder: 1 },
  { name: 'glm', label: 'GLM-5.1', keyField: 'glm_api_key', icon: glmLogo, sortOrder: 2 },
  { name: 'kimi', label: 'Kimi', keyField: 'kimi_api_key', icon: kimiLogo, sortOrder: 3 }
]

const createDefaultConfigItem = (model) => reactive({
  key: model.keyField,
  label: `${model.label} API Key`,
  value: '',
  defaultValue: '',
  sortOrder: model.sortOrder,
  showPassword: false,
  testing: false,
  testResult: null
})

const loading = ref(false)
const saving = ref(false)
const configItems = ref(modelConfig.map(createDefaultConfigItem))
const configStatus = ref({})

const hasInputValue = (value) => String(value || '').trim().length > 0

const modelGroups = computed(() => modelConfig.map(model => {
  const apiKey = configItems.value.find(config => config.key === model.keyField) || createDefaultConfigItem(model)
  return {
    ...model,
    apiKey,
    configured: hasInputValue(apiKey.value) || Boolean(configStatus.value[model.name]),
    testing: apiKey.testing || false,
    testResult: apiKey.testResult || null
  }
}))

const normalizeConfigs = (items = []) => {
  configItems.value = items.map(item => reactive({
    id: item.id,
    key: item.configKey,
    label: item.configLabel,
    value: item.configValue || '',
    defaultValue: item.defaultValue || '',
    sortOrder: item.sortOrder || 999,
    showPassword: false,
    testing: false,
    testResult: null
  }))
  modelConfig.forEach(model => {
    if (!configItems.value.some(item => item.key === model.keyField)) {
      configItems.value.push(createDefaultConfigItem(model))
    }
  })
}

const loadConfig = async () => {
  loading.value = true
  try {
    const [configRes, statusRes] = await Promise.all([
      getConfigByGroup(AI_CONFIG_GROUP),
      getAiConfigStatus()
    ])

    if (configRes.code !== '200') {
      ElMessage.error(configRes.msg || '加载AI配置失败')
      normalizeConfigs([])
      return
    }

    normalizeConfigs(configRes.data || [])
    configStatus.value = statusRes.code === '200' ? (statusRes.data || {}) : {}
  } catch (error) {
    ElMessage.error('加载AI配置失败')
    normalizeConfigs([])
  } finally {
    loading.value = false
  }
}

const saveItem = (item) => updateConfig({
  id: item.id,
  configKey: item.key,
  configValue: item.value || '',
  configGroup: AI_CONFIG_GROUP,
  configLabel: item.label,
  defaultValue: item.defaultValue || '',
  sortOrder: item.sortOrder || 999
})

const refreshStatus = async () => {
  try {
    const res = await getAiConfigStatus()
    if (res.code === '200') {
      configStatus.value = res.data || {}
    }
  } catch {
    configStatus.value = {}
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const responses = await Promise.all(configItems.value.map(saveItem))
    const failed = responses.find(response => response.code !== '200')
    if (failed) {
      ElMessage.error(failed.msg || '保存失败')
      return
    }
    await refreshStatus()
    ElMessage.success('AI配置已保存')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleReset = (model) => {
  model.apiKey.value = ''
  model.apiKey.testResult = null
}

const handleTest = async (model) => {
  model.apiKey.testing = true
  model.apiKey.testResult = null
  try {
    const saveRes = await saveItem(model.apiKey)
    if (saveRes.code !== '200') {
      model.apiKey.testResult = { success: false, message: saveRes.msg || '保存当前密钥失败' }
      ElMessage.error(saveRes.msg || '保存当前密钥失败')
      return
    }

    const res = await testAiConnection(model.name)
    if (res.code === '200') {
      const responseTime = res.data?.responseTime
      model.apiKey.testResult = {
        success: true,
        message: `连接成功${responseTime != null ? `，响应时间 ${responseTime}ms` : ''}`
      }
      await refreshStatus()
      ElMessage.success(`${model.label} 连接测试成功`)
      return
    }

    model.apiKey.testResult = {
      success: false,
      message: res.msg || '连接失败'
    }
    ElMessage.error(`${model.label} 连接测试失败`)
  } catch (error) {
    model.apiKey.testResult = {
      success: false,
      message: error?.message ? `连接失败: ${error.message}` : '连接失败'
    }
    ElMessage.error(`${model.label} 连接测试失败`)
  } finally {
    model.apiKey.testing = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.ai-config-panel {
  padding: 2px 0 4px;
}

.model-card {
  padding: 16px;
  margin-bottom: 16px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.model-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.model-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
  flex: 0 0 24px;
}

.model-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--app-text);
}

.config-item label {
  display: block;
  margin-bottom: 7px;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text);
}

.input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.password-toggle {
  cursor: pointer;
  color: var(--app-text-muted);
}

.password-toggle:hover {
  color: var(--app-text);
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.status {
  font-size: 13px;
  color: var(--app-text-muted);
}

.status.configured {
  color: #2f8f45;
}

.status.unconfigured {
  color: #c45656;
}

.test-result {
  margin-top: 10px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  border-radius: 4px;
}

.test-result.success {
  color: #2f8f45;
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
}

.test-result.error {
  color: #c45656;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
}

.config-alert {
  margin: 16px 0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}

@media (max-width: 640px) {
  .input-row,
  .status-row {
    grid-template-columns: 1fr;
    display: grid;
  }

  .status-row {
    justify-items: stretch;
  }
}
</style>
