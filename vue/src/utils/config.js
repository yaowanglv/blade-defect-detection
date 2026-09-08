import request from './request.js'

export const getConfigByGroup = (group, options = {}) =>
  request.get('/config/getByGroup', {
    ...options,
    params: { group }
  })

export const getAllConfigs = () =>
  request.get('/config/list')

export const getUiBrandingConfig = () =>
  request.get('/config/uiBranding')

export const updateUiBrandingConfig = (data) =>
  request.put('/config/uiBranding', data)

export const uploadUiBrandingLogo = (formData) =>
  request.post('/config/uploadLogo', formData)

export const updateConfig = (data) =>
  request.put('/config/update', data)

export const batchUpdateConfigs = (configs) =>
  request.put('/config/batchUpdate', configs)

export const resetConfig = (key, group) =>
  request.post('/config/reset', null, { params: { key, group } })

export const listConfigModels = (group, rootPath, includeHidden = false) =>
  request.get('/config/listModels', { params: { group, rootPath, includeHidden } })

export const updateModelAliases = (group, aliases) =>
  request.put('/config/modelAliases', aliases, { params: { group } })

export const updateHiddenModels = (group, hiddenModels) =>
  request.put('/config/hiddenModels', hiddenModels, { params: { group } })

export const getAiConfigStatus = () =>
  request.get('/detect/aiConfigStatus')

export const testAiConnection = (model) =>
  request.post('/detect/testAiConnection', null, { params: { model }, timeout: 70000 })
