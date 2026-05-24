import api from './index'

export function fetchPositions() {
  return api.get('/positions')
}

export function fetchPositionDetail(code) {
  return api.get(`/positions/${code}`)
}

export function buyPosition(code, data) {
  return api.post(`/positions/${code}/buy`, data)
}

export function sellPosition(code, data) {
  return api.post(`/positions/${code}/sell`, data)
}

export function deletePosition(code) {
  return api.delete(`/positions/${code}`)
}

export function resetPosition(code, data) {
  return api.post(`/positions/${code}/reset`, data)
}
