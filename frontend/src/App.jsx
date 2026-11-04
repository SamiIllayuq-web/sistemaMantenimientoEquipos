import { Navigate, Route, Routes } from 'react-router-dom'
import { ProveedorSesion, useSesion } from './contexto/Sesion'
import Login from './paginas/Login'
import Layout from './paginas/Layout'
import Usuarios from './paginas/Usuarios'
import './App.css'

/** Protege una ruta: exige sesion activa. */
function RutaProtegida({ children }) {
  const { estaAutenticado, cargando } = useSesion()

  if (cargando) {
    return <p className="app-cargando">Cargando...</p>
  }
  if (!estaAutenticado) {
    return <Navigate to="/login" replace />
  }
  return children
}

/** Pantalla de inicio: resumen del usuario y sus modulos. */
function Inicio() {
  const { usuario, modulos } = useSesion()

  return (
    <div className="inicio">
      <h1>Bienvenido, {usuario?.nombreCompleto ?? usuario?.nombres}</h1>
      <p className="inicio-subtitulo">
        Sistema de Mantenimiento de la Residencia universitaria UNH
      </p>

      <div className="inicio-tarjetas">
        <div className="inicio-tarjeta">
          <span className="inicio-etiqueta">Correo</span>
          <strong>{usuario?.correo}</strong>
        </div>
        <div className="inicio-tarjeta">
          <span className="inicio-etiqueta">Estado</span>
          <strong>{usuario?.estado}</strong>
        </div>
        <div className="inicio-tarjeta">
          <span className="inicio-etiqueta">Modulos asignados</span>
          <strong>{modulos.length}</strong>
        </div>
      </div>

      <h2>Tus modulos</h2>
      <div className="inicio-modulos">
        {modulos.map((modulo) => (
          <span key={modulo}>{modulo}</span>
        ))}
      </div>
    </div>
  )
}

/** Pantallas de modulos que todavia no se han construido. */
function EnConstruccion({ modulo }) {
  return (
    <div className="en-construccion">
      <h1>{modulo}</h1>
      <p>Este modulo se construye en los siguientes incrementos.</p>
    </div>
  )
}

export default function App() {
  return (
    <ProveedorSesion>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/"
          element={
            <RutaProtegida>
              <Layout />
            </RutaProtegida>
          }
        >
          <Route index element={<Inicio />} />
          <Route path="usuarios" element={<Usuarios />} />
          <Route path="ubicaciones" element={<EnConstruccion modulo="Ubicaciones" />} />
          <Route path="tipos-activo" element={<EnConstruccion modulo="Tipos de activo" />} />
          <Route path="activos" element={<EnConstruccion modulo="Activos" />} />
          <Route path="incidencias" element={<EnConstruccion modulo="Incidencias" />} />
          <Route path="mantenimientos" element={<EnConstruccion modulo="Mantenimientos" />} />
          <Route path="evidencias" element={<EnConstruccion modulo="Evidencias" />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ProveedorSesion>
  )
}
