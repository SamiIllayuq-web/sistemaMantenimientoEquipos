import axios from 'axios'

/**
 * Cliente HTTP. El token JWT se envia en el header Authorization.
 * En desarrollo, Vite hace proxy de /api hacia http://localhost:8081.
 */
const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Adjunta el token si existe.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si el token expiro o es invalido, limpia la sesion y vuelve al login.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const estado = error.response?.status
    if (estado === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('token')
      localStorage.removeItem('usuario')
      localStorage.removeItem('modulos')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default api
