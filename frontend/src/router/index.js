import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      {
        path: '',
        name: 'Positions',
        component: () => import('../views/Positions.vue'),
        meta: { title: '持仓' }
      },
      {
        path: 'watchlist',
        name: 'Watchlist',
        component: () => import('../views/Watchlist.vue'),
        meta: { title: '自选' }
      }
    ]
  },
  {
    path: '/fund/:code',
    name: 'FundDetail',
    component: () => import('../views/FundDetail.vue'),
    meta: { title: '基金明细' }
  },
  {
    path: '/portfolio-analysis',
    name: 'PortfolioAnalysis',
    component: () => import('../views/PortfolioAnalysis.vue'),
    meta: { title: '持仓分析' }
  },
  {
    path: '/mock-dashboard',
    name: 'MockDashboard',
    component: () => import('../views/mock/MockDashboard.vue')
  },
  {
    path: '/mock-watchlist',
    name: 'MockWatchlist',
    component: () => import('../views/mock/MockWatchlist.vue')
  },
  {
    path: '/mock-fund-detail',
    name: 'MockFundDetail',
    component: () => import('../views/mock/MockFundDetail.vue')
  },
  {
    path: '/mock-portfolio-analysis',
    name: 'MockPortfolioAnalysis',
    component: () => import('../views/mock/MockPortfolioAnalysis.vue')
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue'),
    meta: { title: '设置' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

export default router
