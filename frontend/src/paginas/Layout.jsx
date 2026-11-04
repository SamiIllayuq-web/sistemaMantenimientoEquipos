import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useSesion } from '../contexto/Sesion'
import './Layout.css'

/**
 * Pantallas disponibles segun los modulos del usuario.
 * El servidor valida igual: esto es solo la interfaz.
 */
const PANTALLAS = [
  { modulo: 'USUARIOS', ruta: '/usuarios', texto: 'Usuarios' },
  { modulo: 'UBICACIONES', ruta: '/ubicaciones', texto: 'Ubicaciones' },
  { modulo: 'TIPOS_ACTIVO', ruta: '/tipos-activo', texto: 'Tipos de activo' },
  { modulo: 'ACTIVOS', ruta: '/activos', texto: 'Activos' },
  { modulo: 'INCIDENCIAS', ruta: '/incidencias', texto: 'Incidencias' },
  { modulo: 'MANTENIMIENTOS', ruta: '/mantenimientos', texto: 'Mantenimientos' },
  { modulo: 'EVIDENCIAS', ruta: '/evidencias', texto: 'Evidencias' },
]

export default function Layout() {
  const { usuario, modulos, cerrarSesion, tieneModulo } = useSesion()
  const navegar = useNavigate()

  const visibles = PANTALLAS.filter((p) => tieneModulo(p.modulo))

  function manejarSalida() {
    cerrarSesion()
    navegar('/login')
  }

  return (
    <div className="layout">
      <aside className="layout-lateral">
        <div className="layout-marca">
          <strong>Mantenimiento</strong>
          <span>Residencia UNH</span>
        </div>

        <nav className="layout-menu">
          {visibles.length === 0 && (
            <p className="layout-sin-modulos">No tienes modulos asignados.</p>
          )}
          {visibles.map((pantalla) => (
            <NavLink
              key={pantalla.ruta}
              to={pantalla.ruta}
              className={({ isActive }) =>
                isActive ? 'layout-enlace activo' : 'layout-enlace'
              }
            >
              {pantalla.texto}
            </NavLink>
          ))}
        </nav>

        <div className="layout-usuario">
          <div className="layout-usuario-datos">
            <strong>{usuario?.nombreCompleto ?? usuario?.nombres}</strong>
            <span>{usuario?.correo}</span>
            <span className="layout-modulos">
              {modulos.length} modulo{modulos.length === 1 ? '' : 's'}
            </span>
          </div>
          <button type="button" onClick={manejarSalida}>
            Cerrar sesion
          </button>
        </div>
      </aside>

      <main className="layout-contenido">
        <Outlet />
      </main>
    </div>
  )
}
