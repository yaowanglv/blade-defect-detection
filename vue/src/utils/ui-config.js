import { computed, reactive } from 'vue'
import request, { getAccessToken } from './request.js'

const DEFAULT_APP_TITLE = '基于大模型和多模态数据融合的风机缺陷智能检测系统'
const DEFAULT_LOGO_URL = '/lyw.png'
const FAVICON_ID = 'dynamic-favicon'
const EMPTY_FAVICON_DATA_URL = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'

export const DEFAULT_UI_CONFIG = {
  appTitle: DEFAULT_APP_TITLE,
  loginTitle: DEFAULT_APP_TITLE,
  logoUrl: DEFAULT_LOGO_URL,
  showLogo: true,
  faviconUrl: DEFAULT_LOGO_URL,
  showFavicon: true,
  loginDefaultRoute: '/manager/dataview',
  menuGroups: [
    { key: 'user', title: '用户管理' },
    { key: 'data', title: '数据管理' },
    { key: 'diagnosis', title: '智能风机辅助检测与分析' }
  ],
  routes: [
    { path: '/manager/admin', title: '管理员信息', visible: true },
    { path: '/manager/config', title: '系统配置', visible: true },
    { path: '/manager/detect', title: '风机缺陷检测', visible: true },
    { path: '/manager/imagetovideo', title: '风机图像智能剪辑', visible: true },
    { path: '/manager/video', title: '风机视频检测', visible: true },
    { path: '/manager/history', title: '检测历史', visible: true },
    { path: '/manager/dataview', title: '数据可视化', visible: true }
  ]
}

const state = reactive({
  loaded: false,
  loading: false,
  config: structuredClone(DEFAULT_UI_CONFIG)
})

const routeMap = computed(() => {
  const map = {}
  for (const item of state.config.routes || []) {
    if (!item?.path) continue
    map[item.path] = item
  }
  return map
})

const menuGroupMap = computed(() => {
  const map = {}
  for (const item of state.config.menuGroups || []) {
    if (!item?.key) continue
    map[item.key] = item
  }
  return map
})

const cloneConfig = (config) => JSON.parse(JSON.stringify(config))

const normalizeUiConfig = (config = {}) => {
  const next = cloneConfig(DEFAULT_UI_CONFIG)
  if (typeof config.appTitle === 'string' && config.appTitle.trim()) {
    next.appTitle = config.appTitle.trim()
  }
  if (typeof config.loginTitle === 'string' && config.loginTitle.trim()) {
    next.loginTitle = config.loginTitle.trim()
  } else {
    next.loginTitle = next.appTitle
  }
  if (typeof config.logoUrl === 'string' && config.logoUrl.trim()) {
    next.logoUrl = config.logoUrl.trim()
  }
  next.showLogo = config.showLogo !== false
  if (typeof config.faviconUrl === 'string' && config.faviconUrl.trim()) {
    next.faviconUrl = config.faviconUrl.trim()
  }
  next.showFavicon = config.showFavicon !== false
  if (typeof config.loginDefaultRoute === 'string' && config.loginDefaultRoute.trim()) {
    next.loginDefaultRoute = config.loginDefaultRoute.trim()
  }

  const inputGroups = Array.isArray(config.menuGroups) ? config.menuGroups : []
  next.menuGroups = next.menuGroups.map((group) => {
    const matched = inputGroups.find((item) => item?.key === group.key)
    return matched?.title?.trim()
      ? { ...group, title: matched.title.trim() }
      : group
  })

  const inputRoutes = Array.isArray(config.routes) ? config.routes : []
  next.routes = next.routes.map((route) => {
    const matched = inputRoutes.find((item) => item?.path === route.path)
    if (!matched) return route
    return {
      ...route,
      title: matched.title?.trim() || route.title,
      visible: route.path === '/manager/config' ? true : matched.visible !== false
    }
  })

  return next
}

const applyFavicon = () => {
  const faviconUrl = state.config.showFavicon !== false && state.config.faviconUrl ? state.config.faviconUrl : ''
  let link = document.getElementById(FAVICON_ID)
  if (!link) {
    link = document.createElement('link')
    link.id = FAVICON_ID
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  if (faviconUrl) {
    link.href = faviconUrl
    const lowerUrl = faviconUrl.toLowerCase()
    link.type = lowerUrl.endsWith('.ico')
      ? 'image/x-icon'
      : lowerUrl.endsWith('.svg')
        ? 'image/svg+xml'
        : 'image/png'
  } else {
    // Prevent browsers from falling back to the default static favicon.
    link.href = EMPTY_FAVICON_DATA_URL
    link.type = 'image/svg+xml'
  }
}

export const applyUiConfig = (config) => {
  state.config = normalizeUiConfig(config)
  state.loaded = true
  applyFavicon()
  return state.config
}

export const ensureUiConfigLoaded = async (force = false) => {
  if (state.loading) {
    return state.config
  }
  if (state.loaded && !force) {
    return state.config
  }

  state.loading = true
  try {
    const res = await request.get('/public/ui/branding', { silentForbidden: true })
    if (res.code === '200' && res.data) {
      applyUiConfig(res.data)
    } else {
      applyUiConfig(DEFAULT_UI_CONFIG)
    }
  } catch (error) {
    applyUiConfig(DEFAULT_UI_CONFIG)
  } finally {
    state.loading = false
  }
  return state.config
}

export const saveUiConfig = async (config) => {
  const normalized = normalizeUiConfig(config)
  const res = await request.put('/config/uiBranding', normalized)
  if (res.code === '200') {
    applyUiConfig(normalized)
  }
  return res
}

export const uploadUiLogo = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post('/config/uploadLogo', formData, {
    headers: {
      Authorization: `Bearer ${getAccessToken()}`
    }
  })
  return res
}

export const getUiConfig = () => state.config
export const getAppTitle = () => state.config.appTitle || DEFAULT_APP_TITLE
export const getLoginTitle = () => state.config.loginTitle || getAppTitle()
export const getRouteTitle = (path, fallback = '') => routeMap.value[path]?.title || fallback
export const isRouteVisible = (path) => {
  if (path === '/manager/config') return true
  const route = routeMap.value[path]
  return route ? route.visible !== false : true
}
export const getMenuGroupTitle = (key, fallback = '') => menuGroupMap.value[key]?.title || fallback
export const getVisibleRoutes = () => (state.config.routes || []).filter((item) => isRouteVisible(item.path))
export const getAvailableManagerRoutes = (isAdmin = true) => (state.config.routes || []).filter((item) => {
  if (!item?.path?.startsWith('/manager/')) return false
  if (!isRouteVisible(item.path)) return false
  if (!isAdmin && item.path === '/manager/admin') return false
  return true
})
export const getDefaultManagerRoute = (isAdmin = false) => {
  const preferredOrder = ['/manager/dataview', '/manager/detect', '/manager/history', '/manager/video', '/manager/imagetovideo', '/manager/admin', '/manager/config']
  const configuredPath = state.config.loginDefaultRoute
  const configuredRoute = state.config.routes?.find((item) => item?.path === configuredPath)
  if (configuredRoute && isRouteVisible(configuredPath) && (isAdmin || configuredPath !== '/manager/admin')) {
    return configuredPath
  }
  const available = preferredOrder.filter((path) => {
    if (!isRouteVisible(path)) return false
    if (!isAdmin && ['/manager/admin'].includes(path)) return false
    return true
  })
  return available[0] || '/manager/config'
}
export const shouldShowLogo = () => state.config.showLogo !== false && !!state.config.logoUrl
export const getLogoUrl = () => (shouldShowLogo() ? state.config.logoUrl : '')
export const shouldShowFavicon = () => state.config.showFavicon !== false && !!state.config.faviconUrl
export const getFaviconUrl = () => (shouldShowFavicon() ? state.config.faviconUrl : '')
export const useUiConfigState = () => state
