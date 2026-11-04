import { useEffect, useState } from 'react'
import api from '../api/cliente'
import { useSesion } from '../contexto/Sesion'
import './Usuarios.css'

const FORMULARIO_VACIO = {
  dni: '',
  nombres: '',
  apellidos: '',
  correo: '',
  clave: '',
  telefono: '',
  modulos: [],
}

export default function Usuarios() {
  const { tieneModulo } = useSesion()
  const [usuarios, setUsuarios] = useState([])
  const [modulosDisponibles, setModulosDisponibles] = useState([])
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [editando, setEditando] = useState(null)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)

  async function cargarDatos() {
    try {
      const [respUsuarios, respModulos] = await Promise.all([
        api.get('/usuarios'),
        api.get('/modulos'),
      ])
      setUsuarios(respUsuarios.data)
      setModulosDisponibles(respModulos.data)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los datos')
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    cargarDatos()
  }, [])

  function cambiarCampo(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function alternarModulo(codigo) {
    setFormulario((anterior) => ({
      ...anterior,
      modulos: anterior.modulos.includes(codigo)
        ? anterior.modulos.filter((m) => m !== codigo)
        : [...anterior.modulos, codigo],
    }))
  }

  function editar(usuario) {
    setEditando(usuario.dni)
    setFormulario({
      dni: usuario.dni,
      nombres: usuario.nombres,
      apellidos: usuario.apellidos,
      correo: usuario.correo,
      clave: '',
      telefono: usuario.telefono ?? '',
      modulos: [...usuario.modulos],
    })
    setError('')
    setExito('')
  }

  function cancelarEdicion() {
    setEditando(null)
    setFormulario(FORMULARIO_VACIO)
  }

  async function guardar(evento) {
    evento.preventDefault()
    setError('')
    setExito('')

    try {
      if (editando) {
        await api.put(`/usuarios/${editando}`, {
          nombres: formulario.nombres,
          apellidos: formulario.apellidos,
          correo: formulario.correo,
          telefono: formulario.telefono || null,
          modulos: formulario.modulos,
        })
        setExito(`Usuario ${formulario.dni} actualizado`)
      } else {
        await api.post('/usuarios', formulario)
        setExito(`Usuario ${formulario.dni} creado`)
      }
      cancelarEdicion()
      await cargarDatos()
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo guardar el usuario')
      }
    }
  }

  async function desactivar(dni) {
    if (!window.confirm(`Desactivar al usuario ${dni}?`)) return
    setError('')
    try {
      await api.put(`/usuarios/${dni}/desactivar`)
      setExito(`Usuario ${dni} desactivado`)
      await cargarDatos()
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo desactivar')
    }
  }

  if (cargando) {
    return <p className="usuarios-cargando">Cargando...</p>
  }

  return (
    <div className="usuarios">
      <h1>Usuarios y permisos</h1>
      <p className="usuarios-subtitulo">
        Cada modulo es una pantalla. El servidor valida el acceso a cada una.
      </p>

      {error && <div className="usuarios-error">{error}</div>}
      {exito && <div className="usuarios-exito">{exito}</div>}

      <div className="usuarios-grid">
        <section className="usuarios-panel">
          <h2>{editando ? `Editar usuario ${editando}` : 'Nuevo usuario'}</h2>

          <form onSubmit={guardar}>
            <div className="usuarios-campo">
              <label htmlFor="dni">DNI</label>
              <input
                id="dni"
                value={formulario.dni}
                onChange={(e) => cambiarCampo('dni', e.target.value)}
                disabled={Boolean(editando)}
                maxLength={8}
                required
              />
            </div>

            <div className="usuarios-campo">
              <label htmlFor="nombres">Nombres</label>
              <input
                id="nombres"
                value={formulario.nombres}
                onChange={(e) => cambiarCampo('nombres', e.target.value)}
                required
              />
            </div>

            <div className="usuarios-campo">
              <label htmlFor="apellidos">Apellidos</label>
              <input
                id="apellidos"
                value={formulario.apellidos}
                onChange={(e) => cambiarCampo('apellidos', e.target.value)}
                required
              />
            </div>

            <div className="usuarios-campo">
              <label htmlFor="correo">Correo</label>
              <input
                id="correo"
                type="email"
                value={formulario.correo}
                onChange={(e) => cambiarCampo('correo', e.target.value)}
                required
              />
            </div>

            {!editando && (
              <div className="usuarios-campo">
                <label htmlFor="clave">Contrasena (min. 8)</label>
                <input
                  id="clave"
                  type="password"
                  value={formulario.clave}
                  onChange={(e) => cambiarCampo('clave', e.target.value)}
                  minLength={8}
                  required
                />
              </div>
            )}

            <div className="usuarios-campo">
              <label htmlFor="telefono">Telefono</label>
              <input
                id="telefono"
                value={formulario.telefono}
                onChange={(e) => cambiarCampo('telefono', e.target.value)}
              />
            </div>

            <fieldset className="usuarios-modulos">
              <legend>Modulos asignados</legend>
              {modulosDisponibles.map((modulo) => (
                <label key={modulo.id} className="usuarios-modulo">
                  <input
                    type="checkbox"
                    checked={formulario.modulos.includes(modulo.codigo)}
                    onChange={() => alternarModulo(modulo.codigo)}
                  />
                  <span>{modulo.nombre}</span>
                  <small>{modulo.codigo}</small>
                </label>
              ))}
            </fieldset>

            <div className="usuarios-acciones">
              <button type="submit">
                {editando ? 'Guardar cambios' : 'Crear usuario'}
              </button>
              {editando && (
                <button type="button" className="secundario" onClick={cancelarEdicion}>
                  Cancelar
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="usuarios-panel">
          <h2>Usuarios registrados ({usuarios.length})</h2>

          <table className="usuarios-tabla">
            <thead>
              <tr>
                <th>DNI</th>
                <th>Nombre</th>
                <th>Correo</th>
                <th>Modulos</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((usuario) => (
                <tr key={usuario.dni} className={usuario.estado !== 'ACTIVO' ? 'inactivo' : ''}>
                  <td>{usuario.dni}</td>
                  <td>{usuario.nombres} {usuario.apellidos}</td>
                  <td>{usuario.correo}</td>
                  <td>
                    <div className="usuarios-chips">
                      {usuario.modulos.map((modulo) => (
                        <span key={modulo}>{modulo}</span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <span className={`usuarios-estado ${usuario.estado.toLowerCase()}`}>
                      {usuario.estado}
                    </span>
                  </td>
                  <td className="usuarios-fila-acciones">
                    <button type="button" onClick={() => editar(usuario)}>
                      Editar
                    </button>
                    {usuario.estado === 'ACTIVO' && tieneModulo('USUARIOS') && (
                      <button
                        type="button"
                        className="peligro"
                        onClick={() => desactivar(usuario.dni)}
                      >
                        Desactivar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </div>
  )
}
