/**
 * Contexto de sesion. Guarda el token, el usuario y la lista de modulos.
 * El menu se construye a partir de los modulos: cada modulo es una
 * pantalla. Ocultar el menu NO es seguridad (el servidor tambien valida).
 */
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import api from '../api/cliente'

const SesionContext = createContext(null)

export function ProveedorSesion({ children }) {
  const [usuario, setUsuario] = useState(null)
  const [modulos, setModulos] = useState([])
  const [cargando, setCargando] = useState(true)

  // Al cargar la app, verifica si hay una sesion guardada.
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) {
      setCargando(false)
      return
    }

    api
      .get('/auth/me')
      .then((respuesta) => {
        const datos = respuesta.data
        setUsuario(datos.usuario)
        setModulos(datos.modulos)
        localStorage.setItem('usuario', JSON.stringify(datos.usuario))
        localStorage.setItem('modulos', JSON.stringify(datos.modulos))
      })
      .catch(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('usuario')
        localStorage.removeItem('modulos')
      })
      .finally(() => setCargando(false))
  }, [])

  const valor = useMemo(
    () => ({
      usuario,
      modulos,
      cargando,
      estaAutenticado: Boolean(usuario),

      tieneModulo: (codigo) => modulos.includes(codigo),

      async iniciarSesion(correo, clave) {
        const respuesta = await api.post('/auth/login', { correo, clave })
        const datos = respuesta.data
        localStorage.setItem('token', datos.token)
        localStorage.setItem('usuario', JSON.stringify(datos.usuario))
        localStorage.setItem('modulos', JSON.stringify(datos.modulos))
        setUsuario(datos.usuario)
        setModulos(datos.modulos)
        return datos
      },

      cerrarSesion() {
        localStorage.removeItem('token')
        localStorage.removeItem('usuario')
        localStorage.removeItem('modulos')
        setUsuario(null)
        setModulos([])
      },
    }),
    [usuario, modulos, cargando],
  )

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}

export function useSesion() {
  const contexto = useContext(SesionContext)
  if (!contexto) {
    throw new Error('useSesion debe usarse dentro de ProveedorSesion')
  }
  return contexto
}
