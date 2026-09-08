import { createRouter, createWebHistory } from 'vue-router'
import {
  ensureUiConfigLoaded,
  getAppTitle,
  getDefaultManagerRoute,
  getLoginTitle,
  getRouteTitle,
  isRouteVisible
} from '../utils/ui-config.js'

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/manager',
    component: () => import('../views/Manager.vue'),
    children: [
      {
        path: '',
        redirect: () => {
          const user = getLoginUser()
          const isAdmin = hasRole(user, 'ADMIN') || hasPermission(user, 'admin:read')
          return getDefaultManagerRoute(isAdmin)
        }
      },
      { path: 'admin', meta: { name: '管理员信息', requiresAdmin: true }, component: () => import('../views/Admin.vue') },
      { path: 'config', meta: { name: '系统配置', requiresAdmin: true }, component: () => import('../views/Config.vue') },
      { path: 'detect', meta: { name: '风机缺陷检测' }, component: () => import('../views/Detect.vue') },
      { path: 'imagetovideo', meta: { name: '风机图像智能剪辑' }, component: () => import('../views/ImageToVideo.vue') },
      { path: 'video', meta: { name: '风机视频检测' }, component: () => import('../views/Video.vue') },
      { path: 'history', meta: { name: '检测历史' }, component: () => import('../views/History.vue') },
      { path: 'dataview', meta: { name: '数据可视化' }, component: () => import('../views/Dataview.vue') }
    ]
  },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/notFound', name: '404', component: () => import('../views/404.vue') }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

const getLoginUser = () => {
  try {
    return JSON.parse(localStorage.getItem('code_user') || '{}')
  } catch {
    return {}
  }
}

const hasRole = (user, role) => {
  const roles = Array.isArray(user.roles) ? user.roles : []
  return roles.map((item) => String(item).toUpperCase()).includes(role)
}

const hasPermission = (user, permission) => {
  const permissions = Array.isArray(user.permissions) ? user.permissions : []
  return permissions.includes(permission)
}

router.beforeEach(async (to) => {
  await ensureUiConfigLoaded()

  if (to.path === '/login') {
    document.title = getLoginTitle()
  } else {
    const routeTitle = getRouteTitle(to.path, to.meta?.name || '')
    document.title = routeTitle ? `${routeTitle} - ${getAppTitle()}` : getAppTitle()
  }

  if (to.path === '/login') {
    return true
  }

  const user = getLoginUser()
  const token = localStorage.getItem('accessToken')
  const isAdmin = hasRole(user, 'ADMIN') || hasPermission(user, 'admin:read')

  if (!token || !user.id) {
    return '/login'
  }

  if (to.path.startsWith('/manager/') && !isRouteVisible(to.path)) {
    return getDefaultManagerRoute(isAdmin)
  }

  if (!to.meta?.requiresAdmin) {
    return true
  }

  if (isAdmin) {
    return true
  }

  return getDefaultManagerRoute(false)
})

export default router
