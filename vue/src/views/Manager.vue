<template>
  <div class="manager-layout">
    <header class="manager-header">
      <div class="brand-panel">
        <img v-if="logoUrl" class="brand-logo" :src="logoUrl" alt="logo">
        <div class="brand-copy">
          <span class="brand-title">{{ appTitle }}</span>
        </div>
      </div>

      <div class="topbar">
        <div class="breadcrumb">
          <button class="breadcrumb-home" type="button" @click="goHome">首页</button>
          <span class="breadcrumb-separator">/</span>
          <span>{{ currentRouteTitle }}</span>
        </div>

        <el-dropdown>
          <div class="user-entry">
            <img class="user-avatar" src="@/assets/imgs/头像.png" alt="">
            <span>{{ currentUser?.name || currentUser?.username || '用户' }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openPasswordDialog">修改密码</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="manager-body">
      <aside class="manager-sidebar">
        <el-menu
          router
          class="manager-menu"
          :default-openeds="defaultOpeneds"
          :default-active="router.currentRoute.value.path"
        >
          <el-sub-menu v-if="isAdmin && hasGroupVisibleRoutes('user')" index="user">
            <template #title>
              <el-icon><User /></el-icon>
              <span>{{ getMenuGroupTitle('user', '用户管理') }}</span>
            </template>
            <el-menu-item
              v-if="shouldRenderRoute('/manager/admin')"
              index="/manager/admin"
            >
              {{ getRouteTitle('/manager/admin', '管理员信息') }}
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="hasGroupVisibleRoutes('data')" index="data">
            <template #title>
              <el-icon><TrendCharts /></el-icon>
              <span>{{ getMenuGroupTitle('data', '数据管理') }}</span>
            </template>
            <el-menu-item v-if="shouldRenderRoute('/manager/dataview')" index="/manager/dataview">
              {{ getRouteTitle('/manager/dataview', '数据可视化') }}
            </el-menu-item>
            <el-menu-item v-if="shouldRenderRoute('/manager/history')" index="/manager/history">
              {{ getRouteTitle('/manager/history', '检测历史') }}
            </el-menu-item>
            <el-menu-item v-if="isAdmin" index="/manager/config">
              {{ getRouteTitle('/manager/config', '系统配置') }}
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="hasGroupVisibleRoutes('diagnosis')" index="diagnosis">
            <template #title>
              <el-icon><FirstAidKit /></el-icon>
              <span>{{ getMenuGroupTitle('diagnosis', '智能风机辅助检测与分析') }}</span>
            </template>
            <el-menu-item v-if="shouldRenderRoute('/manager/detect')" index="/manager/detect">
              {{ getRouteTitle('/manager/detect', '风机缺陷检测') }}
            </el-menu-item>
            <el-menu-item v-if="shouldRenderRoute('/manager/imagetovideo')" index="/manager/imagetovideo">
              {{ getRouteTitle('/manager/imagetovideo', '风机图像智能剪辑') }}
            </el-menu-item>
            <el-menu-item v-if="shouldRenderRoute('/manager/video')" index="/manager/video">
              {{ getRouteTitle('/manager/video', '风机视频检测') }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="manager-content">
        <RouterView v-slot="{ Component, route: currentRoute }">
          <KeepAlive :include="cachedPageNames">
            <component
              :is="Component"
              v-if="isCachedPage(currentRoute.path)"
              :key="currentRoute.path"
            />
          </KeepAlive>
          <component
            :is="Component"
            v-if="!isCachedPage(currentRoute.path)"
            :key="currentRoute.path"
          />
        </RouterView>
      </main>
    </div>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      destroy-on-close
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        class="password-form"
        label-width="92px"
      >
        <el-form-item prop="oldPassword" label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-form-item prop="newPassword" label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword" label="确认密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">
            确认修改
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import router from '@/router/index.js'
import request, { clearAuth, getLoginUser } from '@/utils/request.js'
import {
  ensureUiConfigLoaded,
  getAppTitle,
  getDefaultManagerRoute,
  getLogoUrl,
  getMenuGroupTitle,
  getRouteTitle,
  isRouteVisible,
  useUiConfigState
} from '@/utils/ui-config.js'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FirstAidKit, TrendCharts, User } from '@element-plus/icons-vue'

const route = useRoute()
const uiConfigState = useUiConfigState()
const cachedPageNames = ['Detect', 'ImageToVideo', 'Video']
const cachedPagePaths = ['/manager/detect', '/manager/imagetovideo', '/manager/video']

const currentUser = ref(null)
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const appTitle = computed(() => getAppTitle())
const logoUrl = computed(() => getLogoUrl())
const currentRouteTitle = computed(() => getRouteTitle(route.path, route.meta?.name || '工作台'))

const visibleRoutePathsByGroup = {
  user: ['/manager/admin'],
  data: ['/manager/dataview', '/manager/history', '/manager/config'],
  diagnosis: ['/manager/detect', '/manager/imagetovideo', '/manager/video']
}

const isCachedPage = (path) => cachedPagePaths.includes(path)

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const isAdmin = computed(() => {
  const roles = Array.isArray(currentUser.value?.roles) ? currentUser.value.roles : []
  const permissions = Array.isArray(currentUser.value?.permissions) ? currentUser.value.permissions : []
  return String(currentUser.value?.role || '').toLowerCase() === 'admin'
    || roles.map((role) => String(role).toUpperCase()).includes('ADMIN')
    || permissions.includes('admin:read')
})

const shouldRenderRoute = (path) => {
  if (path === '/manager/config') return true
  if (path === '/manager/admin' && !isAdmin.value) return false
  return isRouteVisible(path)
}

const hasGroupVisibleRoutes = (groupKey) => {
  const paths = visibleRoutePathsByGroup[groupKey] || []
  return paths.some((path) => shouldRenderRoute(path))
}

const defaultOpeneds = computed(() => {
  const groups = ['user', 'data', 'diagnosis']
  return groups.filter((groupKey) => {
    if (groupKey === 'user' && !isAdmin.value) return false
    return hasGroupVisibleRoutes(groupKey)
  })
})

const redirectUnauthorizedAdminPage = async () => {
  await ensureUiConfigLoaded()
  if (!isAdmin.value && ['/manager/admin', '/manager/config'].includes(router.currentRoute.value.path)) {
    router.replace(getDefaultManagerRoute(false))
    return
  }
  if (!shouldRenderRoute(router.currentRoute.value.path) && router.currentRoute.value.path.startsWith('/manager/')) {
    router.replace(getDefaultManagerRoute(isAdmin.value))
  }
}

const goHome = () => {
  router.push(getDefaultManagerRoute(isAdmin.value))
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate?.()
}

const openPasswordDialog = () => {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

const submitPasswordChange = () => {
  if (!currentUser.value?.id) {
    ElMessage.error('当前用户信息不存在，请重新登录')
    return
  }

  passwordFormRef.value?.validate(async (valid) => {
    if (!valid) return
    passwordSubmitting.value = true
    try {
      const res = await request.put('/admin/changePassword', {
        id: currentUser.value.id,
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      if (res.code === '200') {
        const savedUser = { ...currentUser.value }
        if ('password' in savedUser) {
          savedUser.password = passwordForm.newPassword
        }
        currentUser.value = savedUser
        localStorage.setItem('code_user', JSON.stringify(savedUser))
        ElMessage.success('密码修改成功')
        passwordDialogVisible.value = false
      } else {
        ElMessage.error(res.msg || '密码修改失败')
      }
    } catch (error) {
      console.error('修改密码失败:', error)
      ElMessage.error('修改密码失败，请稍后重试')
    } finally {
      passwordSubmitting.value = false
    }
  })
}

const logout = async () => {
  try {
    await request.post('/auth/logout')
  } catch (error) {
    console.error('退出登录失败:', error)
  } finally {
    clearAuth()
    router.push('/login')
  }
}

const user = getLoginUser()
if (user.id) {
  currentUser.value = user
  redirectUnauthorizedAdminPage()
} else {
  router.push('/login')
}

watch(
  () => router.currentRoute.value.path,
  () => redirectUnauthorizedAdminPage()
)

watch(
  () => uiConfigState.config,
  () => redirectUnauthorizedAdminPage(),
  { deep: true }
)
</script>

<style scoped>
.manager-layout {
  min-height: 100vh;
  background: var(--app-bg);
}

.manager-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  height: 64px;
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
  box-shadow: 0 1px 8px rgba(43, 125, 233, 0.04);
}

.brand-panel {
  display: flex;
  align-items: center;
  width: 280px;
  min-height: 64px;
  padding: 12px 20px;
  gap: 12px;
  background: linear-gradient(135deg, #1E3A5F 0%, #2B7DE9 50%, #4B8FE8 100%);
  color: #ffffff;
  position: relative;
  overflow: hidden;
}

.brand-panel::before {
  content: '';
  position: absolute;
  top: -60%;
  right: -10%;
  width: 140px;
  height: 140px;
  background: radial-gradient(circle, rgba(255,255,255,0.20) 0%, transparent 70%);
  border-radius: 50%;
}

.brand-panel::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -10%;
  width: 80px;
  height: 80px;
  background: radial-gradient(circle, rgba(255,255,255,0.10) 0%, transparent 60%);
  border-radius: 50%;
}

.brand-logo {
  position: relative;
  z-index: 1;
  width: 36px;
  height: 36px;
  object-fit: contain;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  padding: 4px;
  flex-shrink: 0;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.brand-title {
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.5px;
  word-wrap: break-word;
  white-space: normal;
}

.topbar {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 0 24px;
  background: #ffffff;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--app-text-muted);
  font-size: 14px;
}

.breadcrumb-home {
  padding: 5px 12px;
  color: var(--app-primary);
  font: inherit;
  font-size: 13px;
  background: var(--app-primary-soft);
  border: 1px solid var(--app-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.breadcrumb-home:hover {
  color: #ffffff;
  background: var(--app-primary);
  border-color: var(--app-primary);
}

.breadcrumb-separator {
  color: var(--app-border-strong);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 14px 4px 4px;
  color: var(--app-text);
  cursor: pointer;
  border-radius: 20px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  transition: all 0.3s ease;
}

.user-entry:hover {
  background: var(--app-surface-hover);
  border-color: var(--app-border-strong);
  box-shadow: var(--app-shadow);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid #ffffff;
  box-shadow: 0 2px 6px rgba(43, 125, 233, 0.1);
  object-fit: cover;
}

.manager-body {
  display: flex;
  min-height: calc(100vh - 64px);
}

.manager-sidebar {
  width: 280px;
  flex: 0 0 280px;
  background: #ffffff;
  border-right: 1px solid var(--app-border);
}

.manager-menu {
  min-height: calc(100vh - 64px);
  border-right: 0;
  background: transparent;
  padding: 8px 0;
}

.manager-content {
  flex: 1;
  min-width: 0;
  background: var(--app-bg);
  padding: 4px;
}

:deep(.manager-menu .el-sub-menu__title) {
  min-height: 48px;
  height: auto;
  padding: 10px 16px;
  color: var(--app-text);
  font-weight: 600;
  font-size: 14px;
  border-radius: 0 10px 10px 0;
  margin-right: 12px;
  transition: all 0.3s ease;
  white-space: normal;
  line-height: 1.4;
  word-wrap: break-word;
}

:deep(.manager-menu .el-sub-menu__title:hover) {
  background: linear-gradient(90deg, var(--app-primary-soft) 0%, transparent 100%);
  color: var(--app-primary);
}

:deep(.manager-menu .el-sub-menu__title .el-icon) {
  font-size: 16px;
  margin-right: 8px;
  color: var(--app-primary);
  flex-shrink: 0;
}

:deep(.manager-menu .el-menu-item) {
  min-height: 40px;
  height: auto;
  padding: 8px 16px 8px 20px;
  color: var(--app-text-muted);
  font-size: 13px;
  border-radius: 0 8px 8px 0;
  margin-right: 12px;
  margin-bottom: 2px;
  transition: all 0.3s ease;
  white-space: normal;
  line-height: 1.4;
  word-wrap: break-word;
}

:deep(.manager-menu .el-menu-item:hover) {
  background: linear-gradient(90deg, var(--app-surface-hover) 0%, transparent 100%);
  color: var(--app-text-secondary);
}

:deep(.manager-menu .el-menu-item.is-active) {
  color: var(--app-primary);
  background: linear-gradient(90deg, var(--app-primary-soft) 0%, transparent 100%);
  font-weight: 600;
  border-right: 3px solid var(--app-primary);
}

:deep(.el-dropdown) {
  cursor: pointer;
}

:deep(.el-tooltip__trigger) {
  outline: none;
}

.password-form {
  padding: 16px 8px 0 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}

@media (max-width: 900px) {
  .manager-header {
    height: auto;
    flex-direction: column;
  }

  .brand-panel,
  .manager-sidebar {
    width: 100%;
    flex-basis: auto;
  }

  .topbar {
    min-height: 52px;
    padding: 0 16px;
  }

  .manager-body {
    flex-direction: column;
  }

  .manager-menu {
    min-height: auto;
  }

  .user-entry span {
    display: none;
  }
}
</style>
