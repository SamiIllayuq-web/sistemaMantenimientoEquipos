import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSesion } from '../contexto/Sesion'
import './Login.css'

export default function Login() {
  const { iniciarSesion } = useSesion()
  const navegar = useNavigate()

  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function manejarEnvio(evento) {
    evento.preventDefault()
    setError('')
    setEnviando(true)

    try {
      await iniciarSesion(correo.trim(), clave)
      navegar('/')
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        const campos = Object.entries(respuesta.erroresCampos)
          .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
          .join('. ')
        setError(campos)
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo iniciar sesion')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="login-pagina">
      <div className="login-tarjeta">
        <div className="login-encabezado">
          <h1>Sistema de Mantenimiento</h1>
          <p>Residencia Universitaria &mdash; UNH</p>
        </div>

        <form onSubmit={manejarEnvio}>
          <label htmlFor="correo">Correo</label>
          <input
            id="correo"
            type="email"
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
            placeholder="admin@unh.edu.pe"
            autoComplete="username"
            required
          />

          <label htmlFor="clave">Contrasena</label>
          <input
            id="clave"
            type="password"
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            placeholder="********"
            autoComplete="current-password"
            required
          />

          {error && <div className="login-error">{error}</div>}

          <button type="submit" disabled={enviando}>
            {enviando ? 'Verificando...' : 'Iniciar sesion'}
          </button>
        </form>

        <div className="login-pie">
          <p>Usuario inicial: admin@unh.edu.pe / Admin1234</p>
        </div>
      </div>
    </div>
  )
}
