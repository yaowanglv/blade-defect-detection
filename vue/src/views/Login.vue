<template>
  <div class="login-container">
    <div class="station-banner">
      <p>欢迎使用蓝鸢服务中转站：www.api.iris9527.com</p>
      <p>站长wx：blueiris9527</p>
      <p>站长qq：61678985</p>
    </div>
    <div class="login-center">
      <div class="login-card">
        <div class="login-card-header">
          <img v-if="logoUrl" class="login-logo" :src="logoUrl" alt="logo">
          <h1 class="system-title">{{ loginTitle }}</h1>
          <p class="system-subtitle"></p>
        </div>
        <form @submit.prevent="login">
          <div class="input-group">
            <label for="username">用户名</label>
            <div class="input-field">
              <span class="input-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="8" r="4.5"/>
                  <path d="M4 21c0-4.5 3.6-8 8-8s8 3.5 8 8"/>
                </svg>
              </span>
              <input type="text" id="username" v-model="form.username" placeholder="请输入用户名" autocomplete="username">
            </div>
          </div>

          <div class="input-group">
            <label for="password">密码</label>
            <div class="input-field">
              <span class="input-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="5" y="11" width="14" height="10" rx="2"/>
                  <circle cx="12" cy="16" r="1.2"/>
                  <path d="M8 11V7a4 4 0 0 1 8 0v4"/>
                </svg>
              </span>
              <input :type="showPassword ? 'text' : 'password'" id="password" v-model="form.password" placeholder="请输入密码" autocomplete="current-password">
              <button type="button" class="password-toggle" @click="showPassword = !showPassword" aria-label="切换密码可见状态">
                <svg v-if="!showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="form-options">
            <label class="remember-me">
              <input type="checkbox" v-model="form.remember">
              <span class="checkbox-custom"></span>
              记住密码
            </label>
            <a href="#" class="forgot-link"></a>
          </div>

          <button type="submit" class="btn-login">登录</button>
        </form>
      </div>

      <p class="copyright"></p>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import request, { saveAuth } from '@/utils/request'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getDefaultManagerRoute, getLoginTitle, getLogoUrl } from '@/utils/ui-config.js'

const form = reactive({
  username: '',
  password: '',
  remember: true
})

const errors = reactive({
  username: '',
  password: ''
})

const showPassword = ref(false)
const loginTitle = computed(() => getLoginTitle())
const logoUrl = computed(() => getLogoUrl())

const validateForm = () => {
  let valid = true
  if (!form.username) {
    errors.username = '请输入账号'
    valid = false
  } else if (form.username.length < 4) {
    errors.username = '最少4位'
    valid = false
  } else {
    errors.username = ''
  }
  if (!form.password) {
    errors.password = '请输入密码'
    valid = false
  } else {
    errors.password = ''
  }
  return valid
}

const login = () => {
  if (!validateForm()) return
  request.post('/auth/login', { username: form.username, password: form.password })
    .then((res) => {
      if (res.code === '200') {
        saveAuth(res.data || {})
        ElMessage.success('登录成功')
        const userInfo = res.data?.userInfo || res.data || {}
        const roles = Array.isArray(userInfo.roles) ? userInfo.roles : []
        const permissions = Array.isArray(userInfo.permissions) ? userInfo.permissions : []
        const isAdmin = String(userInfo.role || '').toLowerCase() === 'admin'
          || roles.map((role) => String(role).toUpperCase()).includes('ADMIN')
          || permissions.includes('admin:read')
        router.push(getDefaultManagerRoute(isAdmin))
      } else {
        ElMessage.error(res.msg || '账号或密码错误')
      }
    }).catch((error) => {
      console.error('登录请求失败:', error)
      ElMessage.error('账号或者密码错误')
    })
}
</script>

<style>
:root {
  --primary: #0891b2;
  --primary-light: #22d3ee;
  --bg-dark: #0f172a;
  --bg-card: rgba(30, 41, 59, 0.7);
  --text-primary: #f1f5f9;
  --text-secondary: #94a3b8;
  --border-color: rgba(148, 163, 184, 0.2);
  --glow-color: rgba(8, 145, 178, 0.3);
}
</style>

<style scoped>
.login-container {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: url('@/assets/imgs/66.png') center / cover no-repeat;
  overflow: hidden;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
}

.station-banner {
  position: absolute;
  top: 20px;
  left: 24px;
  z-index: 2;
  color: #ff0000;
  font-weight: 700;
  font-size: 15px;
  line-height: 1.7;
  text-align: left;
}

.station-banner p {
  margin: 0;
}

.login-center {
  width: min(92vw, 460px);
}

.login-card {
  padding: 34px 30px 28px;
  border-radius: 24px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.18);
}

.login-card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 26px;
}

.login-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
  border-radius: 16px;
  margin-bottom: 16px;
  background: rgba(255, 255, 255, 0.12);
  padding: 8px;
}

.system-title {
  margin: 0;
  color: #111827;
  font-size: 26px;
  line-height: 1.4;
  font-weight: 700;
}

.system-subtitle {
  margin: 10px 0 0;
  color: #374151;
}

.input-group {
  margin-bottom: 18px;
}

.input-group label {
  display: inline-block;
  margin-bottom: 8px;
  color: #111827;
  font-size: 14px;
}

.input-field {
  display: flex;
  align-items: center;
  border: 1px solid #d1d5db;
  border-radius: 14px;
  background: #f9fafb;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
}

.input-field:focus-within {
  border-color: rgba(34, 211, 238, 0.7);
  box-shadow: 0 0 0 4px rgba(8, 145, 178, 0.18);
}

.input-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  color: #4b5563;
}

.input-field input {
  flex: 1;
  height: 48px;
  color: #111827;
  background: transparent;
  border: 0;
  outline: none;
  font-size: 15px;
}

.input-field input::placeholder {
  color: #9ca3af;
}

.password-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  background: transparent;
  color: #4b5563;
  border: 0;
  cursor: pointer;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}

.remember-me {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 13px;
  cursor: pointer;
}

.remember-me input {
  display: none;
}

.checkbox-custom {
  width: 16px;
  height: 16px;
  border-radius: 5px;
  border: 1px solid #d1d5db;
  background: #ffffff;
}

.btn-login {
  width: 100%;
  height: 50px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(8, 145, 178, 0.28);
}

.btn-login:hover {
  filter: brightness(1.04);
}

@media (max-width: 640px) {
  .login-card {
    padding: 26px 20px 22px;
    border-radius: 20px;
  }

  .system-title {
    font-size: 22px;
  }
}
</style>
