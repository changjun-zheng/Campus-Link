import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
    { path: '/board/:code', name: 'board', component: () => import('../views/BoardView.vue'), meta: { title: '版块' } },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录 / 注册', bare: true } },
    { path: '/post/:id', name: 'post', component: () => import('../views/PostDetailView.vue'), meta: { title: '帖子详情' } },
    { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { title: '发布', requiresAuth: true } },
    { path: '/search', name: 'search', component: () => import('../views/SearchView.vue'), meta: { title: '搜索' } },
    // 他人主页（F-ACC-002）：**不设 requiresAuth**——两个后端端点都是公开的，
    // 未登录也该能打开从帖子详情点过来的作者名；关注按钮由页内自己引到登录回跳
    { path: '/u/:id', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { title: '个人主页' } },
    { path: '/notifications', name: 'notifications', component: () => import('../views/NotificationsView.vue'), meta: { title: '通知', requiresAuth: true } },
    { path: '/favorites', name: 'favorites', component: () => import('../views/MyFavoritesView.vue'), meta: { title: '我的收藏', requiresAuth: true } },
    { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '设置', requiresAuth: true } },
    { path: '/me/posts', name: 'my-posts', component: () => import('../views/MyPostsView.vue'), meta: { title: '我的帖子', requiresAuth: true } },
    { path: '/me/replies', name: 'my-replies', component: () => import('../views/MyRepliesView.vue'), meta: { title: '我的回帖', requiresAuth: true } },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { title: '管理后台', requiresAuth: true } },
  ],
})

// 登录守卫：requiresAuth 路由未登录时跳转登录页并携带回跳地址
router.beforeEach((to) => {
  if (!to.meta.requiresAuth) {
    return true
  }
  const auth = useAuthStore()
  // BUG-004：Date.now() 非响应式，过期判定做成命令式，进受保护路由前先清掉过期会话
  if (auth.hasExpired()) {
    auth.logout()
  }
  if (auth.isLoggedIn) {
    return true
  }
  return { path: '/login', query: { redirect: String(to.fullPath) } }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${String(to.meta.title)} · Campus-Link` : 'Campus-Link'
})

export default router
