import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 响应拦截 — 统一错误处理
api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const message = err.response?.data?.error || err.message || '网络异常'
    console.error('[API Error]', message)
    return Promise.reject(new Error(message))
  }
)

export default api
