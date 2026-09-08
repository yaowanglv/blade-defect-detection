import { getConfigByGroup } from './config'

export const WORKSPACE_AUTO_REFRESH_CONFIG = {
  key: 'workspace_auto_refresh',
  group: 'global',
  label: '返回工作页时自动刷新',
  defaultValue: 'false',
  sortOrder: 30
}

const TRUE_VALUES = ['true', '1', 'yes', 'on']

export const isWorkspaceAutoRefreshEnabled = async () => {
  try {
    const res = await getConfigByGroup(WORKSPACE_AUTO_REFRESH_CONFIG.group, {
      silentForbidden: true
    })
    if (res.code !== '200') return false

    const item = (res.data || []).find(
      config => config.configKey === WORKSPACE_AUTO_REFRESH_CONFIG.key
    )
    return TRUE_VALUES.includes(String(item?.configValue || '').trim().toLowerCase())
  } catch (error) {
    console.error('Load page refresh config failed:', error)
    return false
  }
}
