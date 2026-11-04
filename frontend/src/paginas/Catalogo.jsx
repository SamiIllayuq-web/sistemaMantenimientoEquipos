import { useEffect, useState } from 'react'
import api from '../api/cliente'
import './Catalogo.css'

/**
 * Pantalla generica de catalogo (ubicaciones / tipos de activo).
 *
 * LECTURA: cualquier usuario con sesion.
 * ESCRITURA: solo si tiene el modulo correspondiente (el servidor valida).
 */
export default function Catalogo({
  titulo,
  subtitulo,
  endpoint,
  modulo,
  campos,
  formVacio,
  etiquetaSingular,
}) {
  const [registros, setRegistros] = useState([])
  const [formulario, setFormulario] = useState(formVacio)
  const [editando, setEditando] = useState(null)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    async function cargar() {
      try {
        const respuesta = await api.get(endpoint)
        setRegistros(respuesta.data)
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudo cargar la lista')
      } finally {
        setCargando(false)
      }
    }
    cargar()
  }, [endpoint])

  function cambiar(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function editar(registro) {
    setEditando(registro.id)
    setFormulario({ ...registro })
    setError('')
    setExito('')
  }

  function cancelar() {
    setEditando(null)
    setFormulario(formVacio)
  }

  async function guardar(evento) {
    evento.preventDefault()
    setError('')
    setExito('')

    try {
      if (editando) {
        await api.put(`${endpoint}/${editando}`, formulario)
        setExito(`${etiquetaSingular} actualizado`)
      } else {
        await api.post(endpoint, formulario)
        setExito(`${etiquetaSingular} creado`)
      }
      cancelar()
      const respuesta = await api.get(endpoint)
      setRegistros(respuesta.data)
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo guardar')
      }
    }
  }

  async function eliminar(id) {
    if (!window.confirm('Eliminar este registro?')) return
    setError('')
    try {
      await api.delete(`${endpoint}/${id}`)
      setExito(`${etiquetaSingular} eliminado`)
      const respuesta = await api.get(endpoint)
      setRegistros(respuesta.data)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo eliminar')
    }
  }

  if (cargando) {
    return <p className="catalogo-cargando">Cargando...</p>
  }

  return (
    <div className="catalogo">
      <h1>{titulo}</h1>
      <p className="catalogo-subtitulo">{subtitulo}</p>

      {error && <div className="catalogo-error">{error}</div>}
      {exito && <div className="catalogo-exito">{exito}</div>}

      <div className="catalogo-grid">
        {modulo && (
          <section className="catalogo-panel">
            <h2>{editando ? 'Editar' : 'Registrar'}</h2>
            <form onSubmit={guardar}>
              {campos.map((campo) => (
                <div className="catalogo-campo" key={campo.nombre}>
                  <label htmlFor={campo.nombre}>{campo.etiqueta}</label>
                  {campo.tipo === 'select' ? (
                    <select
                      id={campo.nombre}
                      value={formulario[campo.nombre] ?? ''}
                      onChange={(e) => cambiar(campo.nombre, e.target.value)}
                      required={campo.requerido}
                    >
                      <option value="">Seleccione...</option>
                      {campo.opciones.map((opcion) => (
                        <option key={opcion.valor} value={opcion.valor}>
                          {opcion.texto}
                        </option>
                      ))}
                    </select>
                  ) : campo.tipo === 'checkbox' ? (
                    <label className="catalogo-check">
                      <input
                        id={campo.nombre}
                        type="checkbox"
                        checked={Boolean(formulario[campo.nombre])}
                        onChange={(e) => cambiar(campo.nombre, e.target.checked)}
                      />
                      <span>{campo.etiquetaCheck ?? 'Si'}</span>
                    </label>
                  ) : (
                    <input
                      id={campo.nombre}
                      type={campo.tipo ?? 'text'}
                      value={formulario[campo.nombre] ?? ''}
                      onChange={(e) => cambiar(campo.nombre, e.target.value)}
                      required={campo.requerido}
                      min={campo.min}
                      max={campo.max}
                    />
                  )}
                </div>
              ))}

              <div className="catalogo-acciones">
                <button type="submit">
                  {editando ? 'Guardar cambios' : 'Registrar'}
                </button>
                {editando && (
                  <button type="button" className="secundario" onClick={cancelar}>
                    Cancelar
                  </button>
                )}
              </div>
            </form>
          </section>
        )}

        <section className="catalogo-panel">
          <h2>Registros ({registros.length})</h2>

          {registros.length === 0 ? (
            <p className="catalogo-vacio">Todavia no hay registros.</p>
          ) : (
            <table className="catalogo-tabla">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Detalle</th>
                  {modulo && <th></th>}
                </tr>
              </thead>
              <tbody>
                {registros.map((registro) => (
                  <tr key={registro.id}>
                    <td>{registro.nombre}</td>
                    <td className="catalogo-detalle">
                      {campos
                        .filter((c) => c.mostrarEnLista && c.nombre !== 'nombre')
                        .map((c) => (
                          <span key={c.nombre}>
                            <strong>{c.etiqueta}:</strong> {String(registro[c.nombre] ?? '-')}
                          </span>
                        ))}
                    </td>
                    {modulo && (
                      <td className="catalogo-fila-acciones">
                        <button type="button" onClick={() => editar(registro)}>
                          Editar
                        </button>
                        <button
                          type="button"
                          className="peligro"
                          onClick={() => eliminar(registro.id)}
                        >
                          Eliminar
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  )
}
