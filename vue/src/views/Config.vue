<template>
  <div class="config-container">
    <div class="page-header">
      <h2>系统配置</h2>
      <p class="subtitle">管理检测默认参数、模型扫描目录、全局服务地址与界面展示配置</p>
    </div>

    <div class="config-content">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="图片检测配置" name="detect">
          <ModelConfigPanel group="detect" />
        </el-tab-pane>
        <el-tab-pane label="视频检测配置" name="video">
          <ModelConfigPanel group="video" />
        </el-tab-pane>
        <el-tab-pane label="全局设置" name="global">
          <GlobalConfigPanel />
        </el-tab-pane>
        <el-tab-pane label="AI大模型配置" name="ai">
          <AIConfigPanel />
        </el-tab-pane>
        <el-tab-pane label="界面配置" name="ui">
          <UiConfigPanel />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ModelConfigPanel from './config/ModelConfigPanel.vue'
import GlobalConfigPanel from './config/GlobalConfigPanel.vue'
import AIConfigPanel from './config/AIConfigPanel.vue'
import UiConfigPanel from './config/UiConfigPanel.vue'

const activeTab = ref('detect')
</script>

<style scoped>
.config-container {
  min-height: calc(100vh - 64px);
  padding: 20px;
  background: var(--app-bg);
}

.page-header {
  margin-bottom: 18px;
  padding: 20px 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--app-text);
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
}

.config-content {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  padding: 18px;
}

@media (max-width: 900px) {
  .config-container {
    padding: 12px;
  }
}
</style>
