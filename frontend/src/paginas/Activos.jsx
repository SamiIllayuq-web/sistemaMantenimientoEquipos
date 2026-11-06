import { useCallback, useEffect, useState } from 'react'
import api from '../api/cliente'
import Paginacion from '../componentes/Paginacion'
import '../componentes/Paginacion.css'
import { useSesion } from '../contexto/Sesion'
import { formatearFecha } from '../util/fechas'
import './Activos.css'

const FORMULARIO_VACIO = {
  codigoPatrimonial: '',
  nombre: '',
  idTipoActivo: '',
  idUbicacion: '',
  marca: '',
  modelo: '',
  numeroSerie: '',
  fechaAdquisicion: '',
  fechaPuestaServicio: '',
  estadoOperativo: 'OPERATIVO',
  valorAdquisicion: '',
  observaciones: '',
}

const ESTADOS_OPERATIVOS = [
  { valor: 'OPERATIVO', texto: 'Operativo' },
  { valor: 'EN_MANTENIMIENTO', texto: 'En mantenimiento' },
  { valor: 'FUERA_DE_SERVICIO', texto: 'Fuera de servicio' },
]

export default function Activos() {
  const { tieneModulo } = useSesion()
  const puedeEscribir = tieneModulo('ACTIVOS')
  // El historial de mantenimientos es lectura del modulo MANTENIMIENTOS:
  // el boton solo aparece si el usuario lo tiene.
  const puedeVerHistorial = tieneModulo('MANTENIMIENTOS')

  const [activos, setActivos] = useState([])
  const [tipos, setTipos] = useState([])
  const [ubicaciones, setUbicaciones] = useState([])
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [editando, setEditando] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [textoBusqueda, setTextoBusqueda] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState('ACTIVO')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [pagina, setPagina] = useState(1)
  const [tamano, setTamano] = useState(20)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)

  // Historial de mantenimientos del activo abierto en el panel deslizante.
  const [historialActivo, setHistorialActivo] = useState(null)
  const [historial, setHistorial] = useState([])
  const [cargandoHistorial, setCargandoHistorial] = useState(false)

  const cargarActivos = useCallback(async () => {
    const params = { pagina, tamano }
    if (textoBusqueda.trim()) params.texto = textoBusqueda.trim()
    if (estadoFiltro) params.estado = estadoFiltro

    const respuesta = await api.get('/activos', { params })
    setActivos(respuesta.data.contenido)
    setTotal(respuesta.data.total)
    setTotalPaginas(respuesta.data.totalPaginas)
  }, [textoBusqueda, estadoFiltro, pagina, tamano])

  useEffect(() => {
    async function cargar() {
      try {
        const [respTipos, respUbicaciones] = await Promise.all([
          api.get('/tipos-activo'),
          api.get('/ubicaciones'),
        ])
        setTipos(respTipos.data)
        setUbicaciones(respUbicaciones.data)
        await cargarActivos()
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los datos')
      } finally {
        setCargando(false)
      }
    }
    cargar()
  }, [cargarActivos])

  function cambiar(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function buscar(e) {
    e.preventDefault()
    setPagina(1)
    setTextoBusqueda(busqueda)
  }

  function limpiarBusqueda() {
    setBusqueda('')
    setTextoBusqueda('')
    setPagina(1)
  }

  function cambiarEstadoFiltro(nuevoEstado) {
    setEstadoFiltro(nuevoEstado)
    setPagina(1)
  }

  function cambiarPagina(nuevaPagina) {
    setPagina(nuevaPagina)
  }

  function cambiarTamano(nuevoTamano) {
    setTamano(nuevoTamano)
    setPagina(1)
  }

  /** Abre el panel con el historial de mantenimientos del activo. */
  async function abrirHistorial(activo) {
    setHistorialActivo(activo)
    setHistorial([])
    setCargandoHistorial(true)
    try {
      const respuesta = await api.get(`/mantenimientos/activo/${activo.id}`)
      setHistorial(respuesta.data)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo cargar el historial')
    } finally {
      setCargandoHistorial(false)
    }
  }

  function cerrarHistorial() {
    setHistorialActivo(null)
    setHistorial([])
  }

  function editar(activo) {
    setEditando(activo.id)
    setFormulario({
      codigoPatrimonial: activo.codigoPatrimonial,
      nombre: activo.nombre,
      idTipoActivo: activo.tipoActivo.id,
      idUbicacion: activo.ubicacion.id,
      marca: activo.marca ?? '',
      modelo: activo.modelo ?? '',
      numeroSerie: activo.numeroSerie ?? '',
      fechaAdquisicion: activo.fechaAdquisicion ?? '',
      fechaPuestaServicio: activo.fechaPuestaServicio ?? '',
      estadoOperativo: activo.estadoOperativo,
      valorAdquisicion: activo.valorAdquisicion ?? '',
      observaciones: activo.observaciones ?? '',
    })
    setError('')
    setExito('')
  }

  function cancelar() {
    setEditando(null)
    setFormulario(FORMULARIO_VACIO)
  }

  async function guardar(e) {
    e.preventDefault()
    setError('')
    setExito('')

    const cuerpo = {
      ...formulario,
      idTipoActivo: Number(formulario.idTipoActivo),
      idUbicacion: Number(formulario.idUbicacion),
      fechaAdquisicion: formulario.fechaAdquisicion || null,
      fechaPuestaServicio: formulario.fechaPuestaServicio || null,
      valorAdquisicion: formulario.valorAdquisicion === ''
        ? null
        : Number(formulario.valorAdquisicion),
    }

    try {
      if (editando) {
        delete cuerpo.codigoPatrimonial
        await api.put(`/activos/${editando}`, cuerpo)
        setExito('Activo actualizado')
      } else {
        await api.post('/activos', cuerpo)
        setExito('Activo creado')
      }
      cancelar()
      await cargarActivos()
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo guardar el activo')
      }
    }
  }

  async function desactivar(id) {
    if (!window.confirm('Desactivar este activo? Seguirá en el inventario, pero no aparecerá en la lista por defecto.')) return
    try {
      await api.put(`/activos/${id}/desactivar`)
      setExito('Activo desactivado')
      await cargarActivos()
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo desactivar')
    }
  }

  async function reactivar(id) {
    try {
      await api.put(`/activos/${id}/reactivar`)
      setExito('Activo reactivado')
      await cargarActivos()
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo reactivar')
    }
  }

  if (cargando) {
    return <p className="activos-cargando">Cargando...</p>
  }

  return (
    <div className="activos">
      <h1>Activos</h1>
      <p className="activos-subtitulo">
        Inventario de equipos e infraestructura de la residencia.
      </p>

      {error && <div className="activos-error">{error}</div>}
      {exito && <div className="activos-exito">{exito}</div>}

      <div className="activos-barra">
        <form onSubmit={buscar} className="activos-busqueda">
          <input
            type="search"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por nombre o codigo patrimonial..."
          />
          <button type="submit">Buscar</button>
          {busqueda && (
            <button type="button" className="secundario" onClick={limpiarBusqueda}>
              Limpiar
            </button>
          )}
        </form>

        <div className="activos-filtros">
          <label htmlFor="estado">Estado</label>
          <select
            id="estado"
            value={estadoFiltro}
            onChange={(e) => cambiarEstadoFiltro(e.target.value)}
          >
            <option value="ACTIVO">Activos</option>
            <option value="INACTIVO">Inactivos (desactivados)</option>
          </select>
        </div>
      </div>

      <div className="activos-grid">
        {puedeEscribir && (
          <section className="activos-panel">
            <h2>{editando ? `Editar activo ${editando}` : 'Registrar activo'}</h2>

            <form onSubmit={guardar}>
              <div className="activos-campo">
                <label htmlFor="codigoPatrimonial">Codigo patrimonial</label>
                <input
                  id="codigoPatrimonial"
                  value={formulario.codigoPatrimonial}
                  onChange={(e) => cambiar('codigoPatrimonial', e.target.value)}
                  disabled={Boolean(editando)}
                  required
                />
              </div>

              <div className="activos-campo">
                <label htmlFor="nombre">Nombre</label>
                <input
                  id="nombre"
                  value={formulario.nombre}
                  onChange={(e) => cambiar('nombre', e.target.value)}
                  required
                />
              </div>

              <div className="activos-campo">
                <label htmlFor="idTipoActivo">Tipo de activo</label>
                <select
                  id="idTipoActivo"
                  value={formulario.idTipoActivo}
                  onChange={(e) => cambiar('idTipoActivo', e.target.value)}
                  required
                >
                  <option value="">Seleccione...</option>
                  {tipos.map((tipo) => (
                    <option key={tipo.id} value={tipo.id}>
                      {tipo.nombre}
                    </option>
                  ))}
                </select>
              </div>

              <div className="activos-campo">
                <label htmlFor="idUbicacion">Ubicacion</label>
                <select
                  id="idUbicacion"
                  value={formulario.idUbicacion}
                  onChange={(e) => cambiar('idUbicacion', e.target.value)}
                  required
                >
                  <option value="">Seleccione...</option>
                  {ubicaciones.map((ubicacion) => (
                    <option key={ubicacion.id} value={ubicacion.id}>
                      {ubicacion.nombre}
                    </option>
                  ))}
                </select>
              </div>

              <div className="activos-campo-doble">
                <div>
                  <label htmlFor="marca">Marca</label>
                  <input
                    id="marca"
                    value={formulario.marca}
                    onChange={(e) => cambiar('marca', e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="modelo">Modelo</label>
                  <input
                    id="modelo"
                    value={formulario.modelo}
                    onChange={(e) => cambiar('modelo', e.target.value)}
                  />
                </div>
              </div>

              <div className="activos-campo">
                <label htmlFor="numeroSerie">Numero de serie</label>
                <input
                  id="numeroSerie"
                  value={formulario.numeroSerie}
                  onChange={(e) => cambiar('numeroSerie', e.target.value)}
                />
              </div>

              <div className="activos-campo-doble">
                <div>
                  <label htmlFor="fechaAdquisicion">Fecha de adquisicion</label>
                  <input
                    id="fechaAdquisicion"
                    type="date"
                    value={formulario.fechaAdquisicion}
                    onChange={(e) => cambiar('fechaAdquisicion', e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="fechaPuestaServicio">Puesta en servicio</label>
                  <input
                    id="fechaPuestaServicio"
                    type="date"
                    value={formulario.fechaPuestaServicio}
                    onChange={(e) => cambiar('fechaPuestaServicio', e.target.value)}
                  />
                </div>
              </div>

              <div className="activos-campo-doble">
                <div>
                  <label htmlFor="estadoOperativo">Estado operativo</label>
                  <select
                    id="estadoOperativo"
                    value={formulario.estadoOperativo}
                    onChange={(e) => cambiar('estadoOperativo', e.target.value)}
                  >
                    {ESTADOS_OPERATIVOS.map((estado) => (
                      <option key={estado.valor} value={estado.valor}>
                        {estado.texto}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="valorAdquisicion">Valor de adquisicion (S/)</label>
                  <input
                    id="valorAdquisicion"
                    type="number"
                    step="0.01"
                    min="0"
                    value={formulario.valorAdquisicion}
                    onChange={(e) => cambiar('valorAdquisicion', e.target.value)}
                  />
                </div>
              </div>

              <div className="activos-campo">
                <label htmlFor="observaciones">Observaciones</label>
                <textarea
                  id="observaciones"
                  rows="2"
                  value={formulario.observaciones}
                  onChange={(e) => cambiar('observaciones', e.target.value)}
                />
              </div>

              <div className="activos-acciones">
                <button type="submit">
                  {editando ? 'Guardar cambios' : 'Registrar activo'}
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

        <section className="activos-panel">
          <h2>Inventario ({total})</h2>

          {activos.length === 0 ? (
            <p className="activos-vacio">No hay activos que coincidan.</p>
          ) : (
            <table className="activos-tabla">
              <thead>
                <tr>
                  <th>Codigo</th>
                  <th>Activo</th>
                  <th>Ubicacion</th>
                  <th>Tipo</th>
                  <th>Estado op.</th>
                  {(puedeEscribir || puedeVerHistorial) && <th></th>}
                </tr>
              </thead>
              <tbody>
                {activos.map((activo) => (
                  <tr key={activo.id}>
                    <td className="activos-codigo">{activo.codigoPatrimonial}</td>
                    <td>
                      <strong>{activo.nombre}</strong>
                      {activo.marca && (
                        <small className="activos-sub">
                          {activo.marca}
                          {activo.modelo ? ` ${activo.modelo}` : ''}
                        </small>
                      )}
                    </td>
                    <td>{activo.ubicacion.nombre}</td>
                    <td>{activo.tipoActivo.nombre}</td>
                    <td>
                      <span className={`activos-estado ${activo.estadoOperativo.toLowerCase()}`}>
                        {activo.estadoOperativo}
                      </span>
                    </td>
                    {(puedeEscribir || puedeVerHistorial) && (
                      <td className="activos-fila-acciones">
                        {puedeVerHistorial && (
                          <button
                            type="button"
                            className="secundario"
                            onClick={() => abrirHistorial(activo)}
                          >
                            Historial
                          </button>
                        )}
                        {puedeEscribir && (
                          <>
                            <button type="button" onClick={() => editar(activo)}>
                              Editar
                            </button>
                            {activo.estado === 'ACTIVO' ? (
                              <button
                                type="button"
                                className="peligro"
                                onClick={() => desactivar(activo.id)}
                              >
                                Desactivar
                              </button>
                            ) : (
                              <button type="button" onClick={() => reactivar(activo.id)}>
                                Reactivar
                              </button>
                            )}
                          </>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <Paginacion
            pagina={pagina}
            totalPaginas={totalPaginas}
            total={total}
            tamano={tamano}
            onCambiarPagina={cambiarPagina}
            onCambiarTamano={cambiarTamano}
          />
        </section>
      </div>

      {/* Panel deslizante: historial de mantenimientos del activo. */}
      {historialActivo && (
        <div
          className="historial-panel"
          onClick={cerrarHistorial}
          role="presentation"
        >
          <div
            className="historial-cajon"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-label={`Historial de ${historialActivo.nombre}`}
          >
            <div className="historial-cabecera">
              <div>
                <h2>{historialActivo.nombre}</h2>
                <p>{historialActivo.codigoPatrimonial}</p>
              </div>
              <button
                type="button"
                className="historial-cerrar"
                onClick={cerrarHistorial}
              >
                ×
              </button>
            </div>

            {cargandoHistorial ? (
              <p className="historial-vacio">Cargando historial...</p>
            ) : historial.length === 0 ? (
              <p className="historial-vacio">
                Este activo no tiene mantenimientos registrados.
              </p>
            ) : (
              <table className="historial-tabla">
                <thead>
                  <tr>
                    <th>Inicio</th>
                    <th>Tipo</th>
                    <th>Estado</th>
                    <th>Detalle</th>
                  </tr>
                </thead>
                <tbody>
                  {historial.map((mantenimiento) => (
                    <tr key={mantenimiento.id}>
                      <td>{formatearFecha(mantenimiento.fechaInicio)}</td>
                      <td>{mantenimiento.tipoMantenimiento}</td>
                      <td>{mantenimiento.estadoMantenimiento}</td>
                      <td>
                        {mantenimiento.diagnostico && (
                          <span className="historial-detalle">
                            {mantenimiento.diagnostico}
                          </span>
                        )}
                        <span className="historial-detalle">
                          {mantenimiento.responsable.nombreCompleto}
                        </span>
                        {mantenimiento.incidencia && (
                          <span className="historial-detalle">
                            Incidencia #{mantenimiento.incidencia.id}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
