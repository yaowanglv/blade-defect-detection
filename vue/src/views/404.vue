

<template>
  <div class="not-found-page">
    <div class="not-found-card">
      <img class="not-found-image" src="@/assets/imgs/404.jpg" alt="">
      <h1>页面不存在</h1>
      <p>当前访问的地址无法匹配到系统页面。</p>
      <RouterLink class="not-found-link" to="/">返回主页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--app-bg);
}

.not-found-card {
  width: min(480px, 100%);
  padding: 40px 32px;
  text-align: center;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-strong);
}

.not-found-image {
  width: min(280px, 60%);
  display: block;
  margin: 0 auto 24px;
  opacity: 0.9;
}

.not-found-card h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.not-found-card p {
  margin: 8px 0 24px;
  color: var(--app-text-muted);
  font-size: 14px;
}

.not-found-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  padding: 0 22px;
  color: #ffffff;
  font-weight: 500;
  background: var(--app-primary);
  border-radius: 8px;
  transition: all 0.3s ease;
  text-decoration: none;
  box-shadow: 0 4px 12px rgba(43, 125, 233, 0.25);
}

.not-found-link:hover {
  color: #ffffff;
  background: var(--app-primary-hover);
  box-shadow: 0 6px 16px rgba(43, 125, 233, 0.35);
  transform: translateY(-1px);
}
</style>
