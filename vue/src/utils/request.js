import { ElMessage } from 'element-plus'
import router from '../router'
import axios from 'axios'

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_INFO_KEY = 'code_user'

const request = axios.create({
    // baseURL: import.meta.env.VITE_BASE_URL,
    baseURL: 'http://localhost:1234',
    timeout: 30000
})

export const getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_KEY) || ''

export const getRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY) || ''

export const getLoginUser = () => {
    try {
        return JSON.parse(localStorage.getItem(USER_INFO_KEY) || '{}')
    } catch {
        return {}
    }
}

export const saveAuth = (authData = {}) => {
    const userInfo = authData.userInfo || authData
    if (authData.accessToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, authData.accessToken)
    }
    if (authData.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, authData.refreshToken)
    }
    if (userInfo && Object.keys(userInfo).length > 0) {
        localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo))
    }
}

export const clearAuth = () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_INFO_KEY)
}

const isAuthRequest = (url = '') => url.includes('/auth/login') || url.includes('/auth/refresh') || url.endsWith('/login')

const shouldSilenceForbidden = (config = {}) =>
    config.silentForbidden === true || config.silentError === true

const refreshAccessToken = async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return false

    try {
        const response = await axios.post('http://localhost:1234/auth/refresh', { refreshToken })
        const res = response.data
        if (res?.code === '200' && res.data?.accessToken) {
            localStorage.setItem(ACCESS_TOKEN_KEY, res.data.accessToken)
            return true
        }
    } catch (error) {
        console.error('刷新Token失败:', error)
    }
    return false
}

const redirectToLogin = (message = '登录状态已过期，请重新登录') => {
    clearAuth()
    ElMessage.error(message)
    if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
    }
}

request.interceptors.request.use(config => {
    if (!(config.data instanceof FormData)) {
        config.headers['Content-Type'] = 'application/json;charset=utf-8'
    }
    const token = getAccessToken()
    if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
}, error => {
    return Promise.reject(error)
})

request.interceptors.response.use(
    async response => {
        let res = response.data
        if (response.config.responseType === 'blob') {
            return res
        }
        if (typeof res === 'string') {
            res = res ? JSON.parse(res) : res
        }
        if (['401', '4010', '4011', '4012'].includes(String(res.code))) {
            if (!response.config._retry && !isAuthRequest(response.config.url)) {
                response.config._retry = true
                const refreshed = await refreshAccessToken()
                if (refreshed) {
                    response.config.headers.Authorization = `Bearer ${getAccessToken()}`
                    return request(response.config)
                }
            }
            redirectToLogin(res.msg)
        }
        return res
    },
    async error => {
        const originalConfig = error.config || {}
        const status = error.response?.status
        const res = error.response?.data
        if (status === 401 && !originalConfig._retry && !isAuthRequest(originalConfig.url || '')) {
            originalConfig._retry = true
            const refreshed = await refreshAccessToken()
            if (refreshed) {
                originalConfig.headers = originalConfig.headers || {}
                originalConfig.headers.Authorization = `Bearer ${getAccessToken()}`
                return request(originalConfig)
            }
        }
        if (status === 401) {
            redirectToLogin(res?.msg)
            return Promise.reject(error)
        }
        if (status === 403) {
            if (!shouldSilenceForbidden(originalConfig)) {
                ElMessage.error(res?.msg || '权限不足')
            }
            return Promise.reject(error)
        }
        console.log('err' + error)
        return Promise.reject(error)
    }
)

export default request
