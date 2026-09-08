<template>
  <div class="ui-config-panel">
    <section class="panel-block">
      <div class="block-header">
        <div>
          <h3>品牌与标题</h3>
          <p>统一管理浏览器标题、登录页标题、左上角品牌标题与 Logo 显示。默认使用 <code>/lyw.png</code>，也可关闭 Logo、上传本机文件或手动填写 Logo 路径。</p>
        </div>
      </div>

      <el-form label-width="130px" class="config-form">
        <el-form-item label="系统标题">
          <el-input v-model="form.appTitle" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="登录页标题">
          <el-input v-model="form.loginTitle" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="显示 Logo">
          <el-switch v-model="form.showLogo" />
        </el-form-item>
        <el-form-item label="当前 Logo">
          <div class="logo-row">
            <div class="logo-preview">
              <img v-if="form.showLogo && form.logoUrl" :src="form.logoUrl" alt="logo">
              <span v-else>无 Logo</span>
            </div>
            <el-upload
              class="logo-upload"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleLogoChange"
              accept=".png,.jpg,.jpeg,.gif,.webp,.svg,.ico"
            >
              <el-button :loading="logoUploading">上传 Logo</el-button>
            </el-upload>
            <el-button @click="restoreDefaultLogo">恢复默认 Logo</el-button>
            <el-button @click="clearLogo">清空 Logo</el-button>
          </div>
        </el-form-item>
        <el-form-item label="Logo 路径">
          <el-input
            v-model="form.logoUrl"
            placeholder="例如 /lyw.png、/files/branding/xxx.png、http://localhost:1234/files/branding/xxx.png"
          />
        </el-form-item>
        <el-form-item label="显示标签页 Logo">
          <el-switch v-model="form.showFavicon" />
        </el-form-item>
        <el-form-item label="当前标签页 Logo">
          <div class="logo-row">
            <div class="logo-preview">
              <img v-if="form.showFavicon && form.faviconUrl" :src="form.faviconUrl" alt="favicon">
              <span v-else>无标签页 Logo</span>
            </div>
            <el-upload
              class="logo-upload"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFaviconChange"
              accept=".png,.jpg,.jpeg,.gif,.webp,.svg,.ico"
            >
              <el-button :loading="faviconUploading">上传标签页 Logo</el-button>
            </el-upload>
            <el-button @click="restoreDefaultFavicon">恢复默认标签页 Logo</el-button>
            <el-button @click="clearFavicon">取消标签页 Logo</el-button>
          </div>
        </el-form-item>
        <el-form-item label="标签页 Logo 路径">
          <el-input
            v-model="form.faviconUrl"
            placeholder="例如 /lyw.png、/files/branding/xxx.ico、http://localhost:1234/files/branding/xxx.png"
          />
        </el-form-item>
      </el-form>
    </section>

    <section class="panel-block">
      <div class="block-header">
        <div>
          <h3>菜单分组标题</h3>
          <p>修改左侧菜单分组名称。</p>
        </div>
      </div>

      <div class="group-grid">
        <div v-for="group in form.menuGroups" :key="group.key" class="group-card">
          <span class="group-key">{{ group.key }}</span>
          <el-input v-model="group.title" />
        </div>
      </div>
    </section>

    <section class="panel-block">
      <div class="block-header">
        <div>
          <h3>路由显示与名称</h3>
          <p>修改左侧菜单项名称，并配置是否显示。系统配置页固定显示，不能隐藏。</p>
        </div>
      </div>

      <el-table :data="form.routes" border class="route-table">
        <el-table-column prop="path" label="路由路径" min-width="180" />
        <el-table-column label="菜单标题" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.title" />
          </template>
        </el-table-column>
        <el-table-column label="显示" width="120">
          <template #default="{ row }">
            <el-switch v-model="row.visible" :disabled="row.path === '/manager/config'" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <div class="action-row">
      <el-button @click="resetForm">重置未保存修改</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存界面配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DEFAULT_UI_CONFIG, getUiConfig, saveUiConfig, uploadUiLogo } from '@/utils/ui-config.js'

const saving = ref(false)
const logoUploading = ref(false)
const faviconUploading = ref(false)

const createForm = () => JSON.parse(JSON.stringify(getUiConfig()))
const form = reactive(createForm())

const syncForm = () => {
  const next = createForm()
  Object.keys(form).forEach((key) => {
    delete form[key]
  })
  Object.assign(form, next)
}

const resetForm = () => {
  syncForm()
}

const handleLogoChange = async (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return

  logoUploading.value = true
  try {
    const res = await uploadUiLogo(file)
    if (res.code === '200' && res.data?.logoUrl) {
      form.logoUrl = res.data.logoUrl
      form.showLogo = true
      ElMessage.success('Logo 上传成功')
    } else {
      ElMessage.error(res.msg || 'Logo 上传失败')
    }
  } catch (error) {
    console.error('上传 Logo 失败:', error)
    ElMessage.error(error?.response?.data?.msg || error?.message || 'Logo 上传失败')
  } finally {
    logoUploading.value = false
  }
}

const handleFaviconChange = async (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return

  faviconUploading.value = true
  try {
    const res = await uploadUiLogo(file)
    if (res.code === '200' && res.data?.logoUrl) {
      form.faviconUrl = res.data.logoUrl
      form.showFavicon = true
      ElMessage.success('标签页 Logo 上传成功')
    } else {
      ElMessage.error(res.msg || '标签页 Logo 上传失败')
    }
  } catch (error) {
    console.error('上传标签页 Logo 失败:', error)
    ElMessage.error(error?.response?.data?.msg || error?.message || '标签页 Logo 上传失败')
  } finally {
    faviconUploading.value = false
  }
}

const clearLogo = () => {
  form.logoUrl = DEFAULT_UI_CONFIG.logoUrl
  form.showLogo = false
}

const restoreDefaultLogo = () => {
  form.logoUrl = DEFAULT_UI_CONFIG.logoUrl
  form.showLogo = true
}

const clearFavicon = () => {
  form.faviconUrl = DEFAULT_UI_CONFIG.faviconUrl
  form.showFavicon = false
}

const restoreDefaultFavicon = () => {
  form.faviconUrl = DEFAULT_UI_CONFIG.faviconUrl
  form.showFavicon = true
}

const save = async () => {
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(form))
    const res = await saveUiConfig(payload)
    if (res.code === '200') {
      syncForm()
      ElMessage.success('界面配置已保存')
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存界面配置失败:', error)
    ElMessage.error(error?.response?.data?.msg || error?.message || '保存界面配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  syncForm()
})

defineExpose({
  resetForm
})
</script>

<style scoped>
.ui-config-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.panel-block {
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: var(--app-surface-soft);
}

.block-header {
  margin-bottom: 16px;
}

.block-header h3 {
  margin: 0 0 6px;
  font-size: 18px;
  color: var(--app-text);
}

.block-header p {
  margin: 0;
  color: var(--app-text-muted);
}

.config-form {
  max-width: 860px;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.logo-preview {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--app-border-strong);
  border-radius: 14px;
  background: #fff;
  color: var(--app-text-muted);
  overflow: hidden;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.group-card {
  padding: 14px;
  border-radius: 14px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.group-key {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--app-text-muted);
  text-transform: uppercase;
}

.route-table {
  background: #fff;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
