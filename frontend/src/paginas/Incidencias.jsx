import { useCallback, useEffect, useState } from 'react'
import api from '../api/cliente'
import Paginacion from '../componentes/Paginacion'
import '../componentes/Paginacion.css'
import { useSesion } from '../contexto/Sesion'
import { aFechaLocalServidor, formatearFecha } from '../util/fechas'
import './Incidencias.css'

const FORMULARIO_VACIO = {
  idActivo: '',
  descripcion: '',
  prioridad: 'MEDIA',
  fechaDeteccion: '',
  observaciones: '',
}

const PRIORIDADES = [
  { valor: 'ALTA', texto: 'Alta' },
  { valor: 'MEDIA', texto: 'Media' },
  { valor: 'BAJA', texto: 'Baja' },
]

const ESTADOS = [
  { valor: '', texto: 'Todas' },
  { valor: 'ABIERTA', texto: 'Abiertas' },
  { valor: 'EN_ATENCION', texto: 'En atencion' },
  { valor: 'CERRADA', texto: 'Cerradas' },
]

export default function Incidencias() {
  const { tieneModulo } = useSesion()
  const puedeEscribir = tieneModulo('INCIDENCIAS')

  const [incidencias, setIncidencias] = useState([])
  const [activos, setActivos] = useState([])
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [editando, setEditando] = useState(null)
  const [estadoFiltro, setEstadoFiltro] = useState('')
  const [activoFiltro, setActivoFiltro] = useState('')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [pagina, setPagina] = useState(1)
  const [tamano, setTamano] = useState(20)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)

  const cargarIncidencias = useCallback(async () => {
    const params = { pagina, tamano }
    if (estadoFiltro) params.estado = estadoFiltro
    if (activoFiltro) params.idActivo = activoFiltro

    const respuesta = await api.get('/incidencias', { params })
    setIncidencias(respuesta.data.contenido)
    setTotal(respuesta.data.total)
    setTotalPaginas(respuesta.data.totalPaginas)
  }, [estadoFiltro, activoFiltro, pagina, tamano])

  // Los activos del desplegable se cargan una sola vez: son el catalogo
  // completo, no dependen de la pagina ni de los filtros.
  useEffect(() => {
    async function cargarActivos() {
      try {
        const respActivos = await api.get('/activos/opciones')
        setActivos(respActivos.data)
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los activos')
      }
    }
    cargarActivos()
  }, [])

  useEffect(() => {
    async function cargar() {
      try {
        await cargarIncidencias()
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los datos')
      } finally {
        setCargando(false)
      }
    }
    cargar()
  }, [cargarIncidencias])

  function cambiar(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function cambiarEstadoFiltro(nuevoEstado) {
    setEstadoFiltro(nuevoEstado)
    setPagina(1)
  }

  function cambiarActivoFiltro(nuevoActivo) {
    setActivoFiltro(nuevoActivo)
    setPagina(1)
  }

  function cambiarPagina(nuevaPagina) {
    setPagina(nuevaPagina)
  }

  function cambiarTamano(nuevoTamano) {
    setTamano(nuevoTamano)
    setPagina(1)
  }

  function editar(incidencia) {
    setEditando(incidencia.id)
    setFormulario({
      idActivo: incidencia.activo.id,
      descripcion: incidencia.descripcion,
      prioridad: incidencia.prioridad,
      fechaDeteccion: '',
      observaciones: incidencia.observaciones ?? '',
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
      idActivo: Number(formulario.idActivo),
      fechaDeteccion: aFechaLocalServidor(formulario.fechaDeteccion),
    }

    try {
      if (editando) {
        delete cuerpo.idActivo
        await api.put(`/incidencias/${editando}`, cuerpo)
        setExito('Incidencia actualizada')
      } else {
        await api.post('/incidencias', cuerpo)
        setExito('Incidencia reportada')
      }
      cancelar()
      await cargarIncidencias()
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo guardar la incidencia')
      }
    }
  }

  if (cargando) {
    return <p className="incidencias-cargando">Cargando...</p>
  }

  return (
    <div className="incidencias">
      <h1>Incidencias</h1>
      <p className="incidencias-subtitulo">
        Reporte y seguimiento de las fallas de los activos.
      </p>

      {error && <div className="incidencias-error">{error}</div>}
      {exito && <div className="incidencias-exito">{exito}</div>}

      <div className="incidencias-filtros">
        <div className="incidencias-filtro">
          <label htmlFor="estado">Estado</label>
          <select
            id="estado"
            value={estadoFiltro}
            onChange={(e) => cambiarEstadoFiltro(e.target.value)}
          >
            {ESTADOS.map((estado) => (
              <option key={estado.valor} value={estado.valor}>
                {estado.texto}
              </option>
            ))}
          </select>
        </div>

        <div className="incidencias-filtro">
          <label htmlFor="activo">Activo</label>
          <select
            id="activo"
            value={activoFiltro}
            onChange={(e) => cambiarActivoFiltro(e.target.value)}
          >
            <option value="">Todos los activos</option>
            {activos.map((activo) => (
              <option key={activo.id} value={activo.id}>
                {activo.codigoPatrimonial} - {activo.nombre}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="incidencias-grid">
        {puedeEscribir && (
          <section className="incidencias-panel">
            <h2>{editando ? `Corregir incidencia ${editando}` : 'Reportar incidencia'}</h2>

            <form onSubmit={guardar}>
              <div className="incidencias-campo">
                <label htmlFor="idActivo">Activo</label>
                <select
                  id="idActivo"
                  value={formulario.idActivo}
                  onChange={(e) => cambiar('idActivo', e.target.value)}
                  disabled={Boolean(editando)}
                  required
                >
                  <option value="">Seleccione...</option>
                  {activos.map((activo) => (
                    <option key={activo.id} value={activo.id}>
                      {activo.codigoPatrimonial} - {activo.nombre}
                    </option>
                  ))}
                </select>
              </div>

              <div className="incidencias-campo">
                <label htmlFor="descripcion">Descripcion de la falla</label>
                <textarea
                  id="descripcion"
                  rows="3"
                  value={formulario.descripcion}
                  onChange={(e) => cambiar('descripcion', e.target.value)}
                  required
                />
              </div>

              <div className="incidencias-campo-doble">
                <div>
                  <label htmlFor="prioridad">Prioridad</label>
                  <select
                    id="prioridad"
                    value={formulario.prioridad}
                    onChange={(e) => cambiar('prioridad', e.target.value)}
                  >
                    {PRIORIDADES.map((prioridad) => (
                      <option key={prioridad.valor} value={prioridad.valor}>
                        {prioridad.texto}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="fechaDeteccion">Fecha de deteccion</label>
                  <input
                    id="fechaDeteccion"
                    type="datetime-local"
                    value={formulario.fechaDeteccion}
                    onChange={(e) => cambiar('fechaDeteccion', e.target.value)}
                  />
                </div>
              </div>

              <div className="incidencias-campo">
                <label htmlFor="observaciones">Observaciones</label>
                <textarea
                  id="observaciones"
                  rows="2"
                  value={formulario.observaciones}
                  onChange={(e) => cambiar('observaciones', e.target.value)}
                />
              </div>

              <div className="incidencias-acciones">
                <button type="submit">
                  {editando ? 'Guardar cambios' : 'Reportar incidencia'}
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

        <section className="incidencias-panel">
          <h2>Incidencias ({total})</h2>

          {incidencias.length === 0 ? (
            <p className="incidencias-vacio">No hay incidencias que coincidan.</p>
          ) : (
            <table className="incidencias-tabla">
              <thead>
                <tr>
                  <th>Activo</th>
                  <th>Falla</th>
                  <th>Detectada</th>
                  <th>Prioridad</th>
                  <th>Estado</th>
                  {puedeEscribir && <th></th>}
                </tr>
              </thead>
              <tbody>
                {incidencias.map((incidencia) => (
                  <tr key={incidencia.id}>
                    <td>
                      <strong>{incidencia.activo.nombre}</strong>
                      <small className="incidencias-sub">
                        {incidencia.activo.codigoPatrimonial}
                      </small>
                    </td>
                    <td>
                      {incidencia.descripcion}
                      {incidencia.observaciones && (
                        <small className="incidencias-sub">
                          {incidencia.observaciones}
                        </small>
                      )}
                    </td>
                    <td className="incidencias-fecha">
                      {formatearFecha(incidencia.fechaDeteccion)}
                    </td>
                    <td>
                      <span
                        className={`incidencias-prioridad ${incidencia.prioridad.toLowerCase()}`}
                      >
                        {incidencia.prioridad}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`incidencias-estado ${incidencia.estado.toLowerCase()}`}
                      >
                        {incidencia.estado}
                      </span>
                    </td>
                    {puedeEscribir && (
                      <td className="incidencias-fila-acciones">
                        {incidencia.estado === 'ABIERTA' ? (
                          <button type="button" onClick={() => editar(incidencia)}>
                            Corregir
                          </button>
                        ) : (
                          <span className="incidencias-bloqueada">No editable</span>
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
    </div>
  )
}
