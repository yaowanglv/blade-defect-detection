<template>
  <div class="global-config-panel">
    <div v-for="item in configItems" :key="item.key" class="config-item">
      <label class="config-label">{{ item.label }}</label>
      <div class="input-row">
        <el-switch
          v-if="item.type === 'switch'"
          v-model="item.value"
          active-value="true"
          inactive-value="false"
          active-text="自动刷新"
          inactive-text="保持页面"
        />
        <el-select
          v-else-if="item.type === 'select'"
          v-model="item.value"
          class="config-select"
          placeholder="请选择默认跳转页面"
        >
          <el-option
            v-for="option in routeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input v-else v-model="item.value" :placeholder="item.label" />
        <el-button size="small" @click="item.value = item.defaultValue">重置</el-button>
      </div>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      style="margin: 16px 0;"
      title="全局服务地址修改后，可能需要重启后端服务才能完全生效"
    />

    <div class="action-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfigByGroup, updateConfig } from '@/utils/config'
import { WORKSPACE_AUTO_REFRESH_CONFIG } from '@/utils/pageRefresh'
import { ensureUiConfigLoaded, getAvailableManagerRoutes, getRouteTitle } from '@/utils/ui-config.js'

const configItems = ref([])
const routeOptions = ref([])
const saving = ref(false)
const switchConfigKeys = new Set([WORKSPACE_AUTO_REFRESH_CONFIG.key])
const LOGIN_DEFAULT_ROUTE_CONFIG = {
  key: 'login_default_route',
  label: '登录后默认跳转页面',
  defaultValue: '/manager/dataview',
  sortOrder: 31
}

const buildRouteOptions = () =>
  getAvailableManagerRoutes(true).map((item) => ({
    label: getRouteTitle(item.path, item.title || item.path),
    value: item.path
  }))

const normalizeConfigItems = (items = []) => {
  const normalized = items.map(item => ({
    key: item.configKey,
    label: item.configLabel,
    value: item.configValue,
    defaultValue: item.defaultValue,
    sortOrder: item.sortOrder,
    type: switchConfigKeys.has(item.configKey)
      ? 'switch'
      : item.configKey === LOGIN_DEFAULT_ROUTE_CONFIG.key
        ? 'select'
        : 'input'
  }))

  if (!normalized.some(item => item.key === WORKSPACE_AUTO_REFRESH_CONFIG.key)) {
    normalized.push({
      key: WORKSPACE_AUTO_REFRESH_CONFIG.key,
      label: WORKSPACE_AUTO_REFRESH_CONFIG.label,
      value: WORKSPACE_AUTO_REFRESH_CONFIG.defaultValue,
      defaultValue: WORKSPACE_AUTO_REFRESH_CONFIG.defaultValue,
      sortOrder: WORKSPACE_AUTO_REFRESH_CONFIG.sortOrder,
      type: 'switch'
    })
  }

  if (!normalized.some(item => item.key === LOGIN_DEFAULT_ROUTE_CONFIG.key)) {
    normalized.push({
      key: LOGIN_DEFAULT_ROUTE_CONFIG.key,
      label: LOGIN_DEFAULT_ROUTE_CONFIG.label,
      value: LOGIN_DEFAULT_ROUTE_CONFIG.defaultValue,
      defaultValue: LOGIN_DEFAULT_ROUTE_CONFIG.defaultValue,
      sortOrder: LOGIN_DEFAULT_ROUTE_CONFIG.sortOrder,
      type: 'select'
    })
  }

  return normalized.sort((a, b) => (a.sortOrder || 999) - (b.sortOrder || 999))
}

const ensureSelectableRoute = (value) => {
  if (!value) {
    return LOGIN_DEFAULT_ROUTE_CONFIG.defaultValue
  }
  return routeOptions.value.some(item => item.value === value)
    ? value
    : LOGIN_DEFAULT_ROUTE_CONFIG.defaultValue
}

const loadConfig = async () => {
  try {
    await ensureUiConfigLoaded()
    routeOptions.value = buildRouteOptions()
    const res = await getConfigByGroup('global')
    if (res.code !== '200') {
      ElMessage.error(res.msg || '加载全局配置失败')
      return
    }
    configItems.value = normalizeConfigItems(res.data || [])
    const loginRouteItem = configItems.value.find(item => item.key === LOGIN_DEFAULT_ROUTE_CONFIG.key)
    if (loginRouteItem) {
      loginRouteItem.value = ensureSelectableRoute(loginRouteItem.value)
      loginRouteItem.defaultValue = ensureSelectableRoute(loginRouteItem.defaultValue)
    }
  } catch (error) {
    ElMessage.error('加载全局配置失败')
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const responses = await Promise.all(
      configItems.value.map(item => updateConfig({
        configKey: item.key,
        configGroup: 'global',
        configValue: item.value,
        configLabel: item.label,
        defaultValue: item.defaultValue,
        sortOrder: item.sortOrder
      }))
    )
    const failed = responses.find(item => item.code !== '200')
    if (failed) {
      ElMessage.error(failed.msg || '保存失败')
      return
    }
    await ensureUiConfigLoaded(true)
    ElMessage.success('全局配置已保存')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.global-config-panel {
  padding: 8px 0;
}

.config-item {
  margin-bottom: 18px;
}

.config-label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: var(--app-text);
  font-weight: 500;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.config-select {
  width: 100%;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .input-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
