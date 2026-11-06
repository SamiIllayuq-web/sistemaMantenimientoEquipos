import { useCallback, useEffect, useState } from 'react'
import api from '../api/cliente'
import Paginacion from '../componentes/Paginacion'
import '../componentes/Paginacion.css'
import { useSesion } from '../contexto/Sesion'
import { aFechaLocalServidor, ahoraLocal, formatearDia, formatearFecha } from '../util/fechas'
import './Mantenimientos.css'

const FORMULARIO_VACIO = {
  tipoMantenimiento: 'CORRECTIVO',
  idActivo: '',
  idIncidencia: '',
  fechaInicio: '',
  diagnostico: '',
  actividadRealizada: '',
  resultado: '',
  horasInactividad: '',
  costoManoObra: '',
  proximaFechaMantenimiento: '',
  observaciones: '',
}

const ESTADOS = [
  { valor: '', texto: 'Todos' },
  { valor: 'EN_PROCESO', texto: 'En proceso' },
  { valor: 'FINALIZADO', texto: 'Finalizados' },
]

const TIPOS = [
  { valor: '', texto: 'Todos' },
  { valor: 'CORRECTIVO', texto: 'Correctivo' },
  { valor: 'PREVENTIVO', texto: 'Preventivo' },
]

export default function Mantenimientos() {
  const { tieneModulo } = useSesion()
  const puedeEscribir = tieneModulo('MANTENIMIENTOS')

  const [mantenimientos, setMantenimientos] = useState([])
  const [activos, setActivos] = useState([])
  const [incidencias, setIncidencias] = useState([])
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [editando, setEditando] = useState(null)
  const [estadoFiltro, setEstadoFiltro] = useState('')
  const [activoFiltro, setActivoFiltro] = useState('')
  const [tipoFiltro, setTipoFiltro] = useState('')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [pagina, setPagina] = useState(1)
  const [tamano, setTamano] = useState(20)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)

  const cargarMantenimientos = useCallback(async () => {
    const params = { pagina, tamano }
    if (estadoFiltro) params.estado = estadoFiltro
    if (activoFiltro) params.idActivo = activoFiltro
    if (tipoFiltro) params.tipo = tipoFiltro

    const respuesta = await api.get('/mantenimientos', { params })
    setMantenimientos(respuesta.data.contenido)
    setTotal(respuesta.data.total)
    setTotalPaginas(respuesta.data.totalPaginas)
  }, [estadoFiltro, activoFiltro, tipoFiltro, pagina, tamano])

  // Los desplegables se cargan una sola vez: son catalogos completos.
  // Las incidencias del selector vienen de /opciones (sin paginar): un
  // selector paginado se rompe cuando hay mas registros que paginas.
  useEffect(() => {
    async function cargarCatalogos() {
      try {
        const [respActivos, respIncidencias] = await Promise.all([
          api.get('/activos/opciones'),
          api.get('/incidencias/opciones'),
        ])
        setActivos(respActivos.data)
        setIncidencias(respIncidencias.data)
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los catalogos')
      }
    }
    cargarCatalogos()
  }, [])

  useEffect(() => {
    async function cargar() {
      try {
        await cargarMantenimientos()
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los datos')
      } finally {
        setCargando(false)
      }
    }
    cargar()
  }, [cargarMantenimientos])

  function cambiar(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  function cambiarEstadoFiltro(valor) {
    setEstadoFiltro(valor)
    setPagina(1)
  }

  function cambiarActivoFiltro(valor) {
    setActivoFiltro(valor)
    setPagina(1)
  }

  function cambiarTipoFiltro(valor) {
    setTipoFiltro(valor)
    setPagina(1)
  }

  function cambiarPagina(valor) {
    setPagina(valor)
  }

  function cambiarTamano(valor) {
    setTamano(valor)
    setPagina(1)
  }

  function editar(mantenimiento) {
    setEditando(mantenimiento.id)
    setFormulario({
      ...FORMULARIO_VACIO,
      tipoMantenimiento: mantenimiento.tipoMantenimiento,
      idActivo: mantenimiento.activo.id,
      idIncidencia: mantenimiento.incidencia?.id ?? '',
      fechaInicio: '',
      diagnostico: mantenimiento.diagnostico ?? '',
      actividadRealizada: mantenimiento.actividadRealizada ?? '',
      resultado: mantenimiento.resultado ?? '',
      horasInactividad: mantenimiento.horasInactividad ?? '',
      costoManoObra: mantenimiento.costoManoObra ?? '',
      proximaFechaMantenimiento: mantenimiento.proximaFechaMantenimiento ?? '',
      observaciones: mantenimiento.observaciones ?? '',
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

    try {
      if (editando) {
        // Editar un trabajo en curso: se actualizan los datos del trabajo.
        await api.put(`/mantenimientos/${editando}`, {
          diagnostico: formulario.diagnostico || null,
          actividadRealizada: formulario.actividadRealizada || null,
          resultado: formulario.resultado || null,
          horasInactividad:
            formulario.horasInactividad === '' ? null : Number(formulario.horasInactividad),
          costoManoObra:
            formulario.costoManoObra === '' ? null : Number(formulario.costoManoObra),
          observaciones: formulario.observaciones || null,
        })
        setExito('Mantenimiento actualizado')
      } else if (formulario.tipoMantenimiento === 'CORRECTIVO') {
        await api.post('/mantenimientos/correctivo', {
          idIncidencia: Number(formulario.idIncidencia),
          fechaInicio: aFechaLocalServidor(formulario.fechaInicio),
          observaciones: formulario.observaciones || null,
        })
        setExito('Mantenimiento correctivo creado')
      } else {
        await api.post('/mantenimientos/preventivo', {
          idActivo: Number(formulario.idActivo),
          fechaInicio: aFechaLocalServidor(formulario.fechaInicio),
          observaciones: formulario.observaciones || null,
        })
        setExito('Mantenimiento preventivo creado')
      }
      cancelar()
      await cargarMantenimientos()
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo guardar el mantenimiento')
      }
    }
  }

  async function finalizar(mantenimiento) {
    // La fecha se pide en HORA LOCAL y se envia sin convertir a UTC: el
    // servidor valida contra su propio reloj y una conversion a UTC lo
    // rechazaria como "fecha futura".
    const fechaFin = window.prompt(
      'Fecha y hora de fin del trabajo (AAAA-MM-DDTHH:MM):',
      ahoraLocal(),
    )
    if (!fechaFin) return

    const resultado = window.prompt('Resultado del trabajo (opcional):', '') ?? ''

    try {
      await api.put(`/mantenimientos/${mantenimiento.id}/finalizar`, {
        fechaFin: aFechaLocalServidor(fechaFin),
        diagnostico: mantenimiento.diagnostico ?? null,
        actividadRealizada: mantenimiento.actividadRealizada ?? null,
        resultado: resultado || null,
        horasInactividad: mantenimiento.horasInactividad ?? null,
        costoManoObra: mantenimiento.costoManoObra ?? null,
        proximaFechaMantenimiento: mantenimiento.proximaFechaMantenimiento ?? null,
        observaciones: mantenimiento.observaciones ?? null,
      })
      setExito('Mantenimiento finalizado')
      await cargarMantenimientos()
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo finalizar el mantenimiento')
    }
  }

  if (cargando) {
    return <p className="mantenimientos-cargando">Cargando...</p>
  }

  return (
    <div className="mantenimientos">
      <h1>Mantenimientos</h1>
      <p className="mantenimientos-subtitulo">
        Correctivos y preventivos de los activos de la residencia.
      </p>

      {error && <div className="mantenimientos-error">{error}</div>}
      {exito && <div className="mantenimientos-exito">{exito}</div>}

      <div className="mantenimientos-filtros">
        <div className="mantenimientos-filtro">
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

        <div className="mantenimientos-filtro">
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

        <div className="mantenimientos-filtro">
          <label htmlFor="tipo">Tipo</label>
          <select
            id="tipo"
            value={tipoFiltro}
            onChange={(e) => cambiarTipoFiltro(e.target.value)}
          >
            {TIPOS.map((tipo) => (
              <option key={tipo.valor} value={tipo.valor}>
                {tipo.texto}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mantenimientos-grid">
        {puedeEscribir && (
          <section className="mantenimientos-panel">
            <h2>
              {editando
                ? `Editar mantenimiento ${editando}`
                : 'Registrar mantenimiento'}
            </h2>

            <form onSubmit={guardar}>
              {!editando && (
                <>
                  <div className="mantenimientos-campo">
                    <label htmlFor="tipoMantenimiento">Tipo</label>
                    <select
                      id="tipoMantenimiento"
                      value={formulario.tipoMantenimiento}
                      onChange={(e) => cambiar('tipoMantenimiento', e.target.value)}
                    >
                      <option value="CORRECTIVO">Correctivo (desde una incidencia)</option>
                      <option value="PREVENTIVO">Preventivo</option>
                    </select>
                  </div>

                  {formulario.tipoMantenimiento === 'CORRECTIVO' ? (
                    <div className="mantenimientos-campo">
                      <label htmlFor="idIncidencia">Incidencia</label>
                      <select
                        id="idIncidencia"
                        value={formulario.idIncidencia}
                        onChange={(e) => cambiar('idIncidencia', e.target.value)}
                        required
                      >
                        <option value="">Seleccione...</option>
                        {incidencias.map((incidencia) => (
                          <option key={incidencia.id} value={incidencia.id}>
                            #{incidencia.id} - {incidencia.activo.codigoPatrimonial} -{' '}
                            {incidencia.descripcion}
                          </option>
                        ))}
                      </select>
                    </div>
                  ) : (
                    <div className="mantenimientos-campo">
                      <label htmlFor="idActivo">Activo</label>
                      <select
                        id="idActivo"
                        value={formulario.idActivo}
                        onChange={(e) => cambiar('idActivo', e.target.value)}
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
                  )}

                  <div className="mantenimientos-campo">
                    <label htmlFor="fechaInicio">Fecha de inicio</label>
                    <input
                      id="fechaInicio"
                      type="datetime-local"
                      value={formulario.fechaInicio}
                      onChange={(e) => cambiar('fechaInicio', e.target.value)}
                    />
                  </div>
                </>
              )}

              <div className="mantenimientos-campo">
                <label htmlFor="diagnostico">Diagnostico</label>
                <textarea
                  id="diagnostico"
                  rows="2"
                  value={formulario.diagnostico}
                  onChange={(e) => cambiar('diagnostico', e.target.value)}
                />
              </div>

              <div className="mantenimientos-campo">
                <label htmlFor="actividadRealizada">Actividad realizada</label>
                <textarea
                  id="actividadRealizada"
                  rows="2"
                  value={formulario.actividadRealizada}
                  onChange={(e) => cambiar('actividadRealizada', e.target.value)}
                />
              </div>

              <div className="mantenimientos-campo">
                <label htmlFor="resultado">Resultado</label>
                <textarea
                  id="resultado"
                  rows="2"
                  value={formulario.resultado}
                  onChange={(e) => cambiar('resultado', e.target.value)}
                />
              </div>

              <div className="mantenimientos-campo-doble">
                <div>
                  <label htmlFor="horasInactividad">Horas de inactividad</label>
                  <input
                    id="horasInactividad"
                    type="number"
                    step="0.01"
                    min="0"
                    value={formulario.horasInactividad}
                    onChange={(e) => cambiar('horasInactividad', e.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="costoManoObra">Costo mano de obra (S/)</label>
                  <input
                    id="costoManoObra"
                    type="number"
                    step="0.01"
                    min="0"
                    value={formulario.costoManoObra}
                    onChange={(e) => cambiar('costoManoObra', e.target.value)}
                  />
                </div>
              </div>

              <div className="mantenimientos-campo">
                <label htmlFor="proximaFechaMantenimiento">Proxima fecha de mantenimiento</label>
                <input
                  id="proximaFechaMantenimiento"
                  type="date"
                  value={formulario.proximaFechaMantenimiento}
                  onChange={(e) => cambiar('proximaFechaMantenimiento', e.target.value)}
                />
              </div>

              <div className="mantenimientos-campo">
                <label htmlFor="observaciones">Observaciones</label>
                <textarea
                  id="observaciones"
                  rows="2"
                  value={formulario.observaciones}
                  onChange={(e) => cambiar('observaciones', e.target.value)}
                />
              </div>

              <div className="mantenimientos-acciones">
                <button type="submit">
                  {editando ? 'Guardar cambios' : 'Registrar mantenimiento'}
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

        <section className="mantenimientos-panel">
          <h2>Mantenimientos ({total})</h2>

          {mantenimientos.length === 0 ? (
            <p className="mantenimientos-vacio">No hay mantenimientos que coincidan.</p>
          ) : (
            <table className="mantenimientos-tabla">
              <thead>
                <tr>
                  <th>Activo</th>
                  <th>Tipo</th>
                  <th>Inicio</th>
                  <th>Estado</th>
                  <th>Detalle</th>
                  {puedeEscribir && <th></th>}
                </tr>
              </thead>
              <tbody>
                {mantenimientos.map((mantenimiento) => (
                  <tr key={mantenimiento.id}>
                    <td>
                      <strong>{mantenimiento.activo.nombre}</strong>
                      <small className="mantenimientos-sub">
                        {mantenimiento.activo.codigoPatrimonial}
                      </small>
                    </td>
                    <td>
                      <span
                        className={`mantenimientos-tipo ${mantenimiento.tipoMantenimiento.toLowerCase()}`}
                      >
                        {mantenimiento.tipoMantenimiento}
                      </span>
                    </td>
                    <td className="mantenimientos-fecha">
                      {formatearFecha(mantenimiento.fechaInicio)}
                    </td>
                    <td>
                      <span
                        className={`mantenimientos-estado ${mantenimiento.estadoMantenimiento.toLowerCase()}`}
                      >
                        {mantenimiento.estadoMantenimiento}
                      </span>
                    </td>
                    <td>
                      {mantenimiento.diagnostico && (
                        <small className="mantenimientos-sub">
                          {mantenimiento.diagnostico}
                        </small>
                      )}
                      {mantenimiento.incidencia && (
                        <small className="mantenimientos-sub">
                          Incidencia #{mantenimiento.incidencia.id}
                        </small>
                      )}
                      <small className="mantenimientos-sub">
                        {mantenimiento.responsable.nombreCompleto}
                      </small>
                      {mantenimiento.proximaFechaMantenimiento && (
                        <small className="mantenimientos-sub">
                          Proxima: {formatearDia(mantenimiento.proximaFechaMantenimiento)}
                        </small>
                      )}
                    </td>
                    {puedeEscribir && (
                      <td className="mantenimientos-fila-acciones">
                        {mantenimiento.estadoMantenimiento === 'EN_PROCESO' ? (
                          <>
                            <button type="button" onClick={() => editar(mantenimiento)}>
                              Editar
                            </button>
                            <button
                              type="button"
                              className="exito"
                              onClick={() => finalizar(mantenimiento)}
                            >
                              Finalizar
                            </button>
                          </>
                        ) : (
                          <span className="mantenimientos-bloqueada">Finalizado</span>
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
