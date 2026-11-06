import { useCallback, useEffect, useRef, useState } from 'react'
import api from '../api/cliente'
import Paginacion from '../componentes/Paginacion'
import '../componentes/Paginacion.css'
import { useSesion } from '../contexto/Sesion'
import { formatearFecha } from '../util/fechas'
import './Evidencias.css'

/** Tipos de archivo admitidos por el servidor (coinciden con el backend). */
const TIPOS_ADMITIDOS = ['image/jpeg', 'image/jpg', 'image/png', 'application/pdf']

/** Tamano maximo: 10 MB. El servidor lo vuelve a validar. */
const TAMANO_MAXIMO = 10 * 1024 * 1024

const TIPOS_EVIDENCIA = [
  { valor: 'FOTO', texto: 'Foto' },
  { valor: 'INFORME', texto: 'Informe' },
  { valor: 'OTRO', texto: 'Otro' },
]

/** Convierte bytes a un texto legible de tamano. */
function formatearTamano(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function Evidencias() {
  const { tieneModulo } = useSesion()
  const puedeEscribir = tieneModulo('EVIDENCIAS')

  const [mantenimientos, setMantenimientos] = useState([])
  const [mantenimientoId, setMantenimientoId] = useState('')
  const [evidencias, setEvidencias] = useState([])
  const [descripcion, setDescripcion] = useState('')
  const [tipoArchivo, setTipoArchivo] = useState('FOTO')
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [cargando, setCargando] = useState(true)
  const [subiendo, setSubiendo] = useState(false)
  const [pagina, setPagina] = useState(1)
  const [tamano, setTamano] = useState(20)
  const [total, setTotal] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)

  const inputArchivo = useRef(null)

  const cargarMantenimientos = useCallback(async () => {
    const respuesta = await api.get('/mantenimientos', { params: { pagina: 1, tamano: 100 } })
    setMantenimientos(respuesta.data.contenido)
    return respuesta.data.contenido
  }, [])

  const cargarEvidencias = useCallback(async (idMantenimiento, paginaActual, tamanoActual) => {
    if (!idMantenimiento) {
      setEvidencias([])
      setTotal(0)
      setTotalPaginas(0)
      return
    }
    const respuesta = await api.get(`/evidencias/mantenimiento/${idMantenimiento}`, {
      params: { pagina: paginaActual, tamano: tamanoActual },
    })
    setEvidencias(respuesta.data.contenido)
    setTotal(respuesta.data.total)
    setTotalPaginas(respuesta.data.totalPaginas)
  }, [])

  // Al cargar: traer los mantenimientos y, si hay uno, sus evidencias.
  useEffect(() => {
    async function cargar() {
      try {
        const lista = await cargarMantenimientos()
        if (lista.length > 0) {
          setMantenimientoId(String(lista[0].id))
          await cargarEvidencias(lista[0].id, 1, 20)
        }
      } catch (fallo) {
        setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar los datos')
      } finally {
        setCargando(false)
      }
    }
    cargar()
  }, [cargarMantenimientos, cargarEvidencias])

  async function cambiarMantenimiento(nuevoId) {
    setMantenimientoId(nuevoId)
    setPagina(1)
    setError('')
    setExito('')
    try {
      await cargarEvidencias(nuevoId, 1, tamano)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar las evidencias')
    }
  }

  async function cambiarPagina(valor) {
    setPagina(valor)
    try {
      await cargarEvidencias(mantenimientoId, valor, tamano)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar las evidencias')
    }
  }

  async function cambiarTamano(valor) {
    setTamano(valor)
    setPagina(1)
    try {
      await cargarEvidencias(mantenimientoId, 1, valor)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudieron cargar las evidencias')
    }
  }

  /** Valida el archivo en el navegador antes de subirlo. */
  function validarArchivo(archivo) {
    if (!archivo) return 'Seleccione un archivo'
    if (!TIPOS_ADMITIDOS.includes(archivo.type)) {
      return 'Solo se admiten archivos JPG, PNG o PDF'
    }
    if (archivo.size > TAMANO_MAXIMO) {
      return 'El archivo supera el tamano maximo de 10 MB'
    }
    return null
  }

  async function subir(e) {
    e.preventDefault()
    setError('')
    setExito('')

    if (!mantenimientoId) {
      setError('Seleccione el mantenimiento al que pertenece la evidencia')
      return
    }

    const archivo = inputArchivo.current?.files?.[0]
    const problema = validarArchivo(archivo)
    if (problema) {
      setError(problema)
      return
    }

    setSubiendo(true)
    try {
      const datos = new FormData()
      datos.append('archivo', archivo)
      datos.append(
        'datos',
        new Blob(
          [JSON.stringify({ descripcion: descripcion || null, tipoArchivo })],
          { type: 'application/json' },
        ),
      )

      await api.post(`/evidencias/mantenimiento/${mantenimientoId}`, datos, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      setExito('Evidencia registrada')
      setDescripcion('')
      if (inputArchivo.current) inputArchivo.current.value = ''
      await cargarEvidencias(mantenimientoId, pagina, tamano)
    } catch (fallo) {
      const respuesta = fallo.response?.data
      if (respuesta?.erroresCampos) {
        setError(
          Object.entries(respuesta.erroresCampos)
            .map(([campo, mensaje]) => `${campo}: ${mensaje}`)
            .join('. '),
        )
      } else {
        setError(respuesta?.mensaje ?? 'No se pudo registrar la evidencia')
      }
    } finally {
      setSubiendo(false)
    }
  }

  /** Descarga el archivo. El token viaja en el header: no alcanza con un <a>. */
  async function descargar(evidencia) {
    setError('')
    try {
      const respuesta = await api.get(`/evidencias/${evidencia.id}/descargar`, {
        responseType: 'blob',
      })
      const url = URL.createObjectURL(respuesta.data)
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = evidencia.nombreOriginal
      document.body.appendChild(enlace)
      enlace.click()
      document.body.removeChild(enlace)
      URL.revokeObjectURL(url)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo descargar el archivo')
    }
  }

  async function eliminar(evidencia) {
    const confirmado = window.confirm(
      `Eliminar la evidencia "${evidencia.nombreOriginal}"?`,
    )
    if (!confirmado) return

    setError('')
    setExito('')
    try {
      await api.delete(`/evidencias/${evidencia.id}`)
      setExito('Evidencia eliminada')
      await cargarEvidencias(mantenimientoId, pagina, tamano)
    } catch (fallo) {
      setError(fallo.response?.data?.mensaje ?? 'No se pudo eliminar la evidencia')
    }
  }

  if (cargando) {
    return <p className="evidencias-cargando">Cargando...</p>
  }

  return (
    <div className="evidencias">
      <h1>Evidencias</h1>
      <p className="evidencias-subtitulo">
        Fotos e informes que respaldan los trabajos de mantenimiento.
      </p>

      {error && <div className="evidencias-error">{error}</div>}
      {exito && <div className="evidencias-exito">{exito}</div>}

      <div className="evidencias-filtros">
        <div className="evidencias-filtro">
          <label htmlFor="mantenimiento">Mantenimiento</label>
          <select
            id="mantenimiento"
            value={mantenimientoId}
            onChange={(e) => cambiarMantenimiento(e.target.value)}
          >
            <option value="">Seleccione un mantenimiento...</option>
            {mantenimientos.map((mantenimiento) => (
              <option key={mantenimiento.id} value={mantenimiento.id}>
                #{mantenimiento.id} - {mantenimiento.activo.nombre} -{' '}
                {mantenimiento.tipoMantenimiento}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="evidencias-grid">
        {puedeEscribir && (
          <section className="evidencias-panel">
            <h2>Subir evidencia</h2>

            <form onSubmit={subir}>
              <div className="evidencias-campo">
                <label htmlFor="archivo">Archivo</label>
                <input
                  id="archivo"
                  type="file"
                  ref={inputArchivo}
                  accept=".jpg,.jpeg,.png,.pdf"
                />
                <small className="evidencias-ayuda">
                  JPG, PNG o PDF. Maximo 10 MB.
                </small>
              </div>

              <div className="evidencias-campo">
                <label htmlFor="tipoArchivo">Tipo</label>
                <select
                  id="tipoArchivo"
                  value={tipoArchivo}
                  onChange={(e) => setTipoArchivo(e.target.value)}
                >
                  {TIPOS_EVIDENCIA.map((tipo) => (
                    <option key={tipo.valor} value={tipo.valor}>
                      {tipo.texto}
                    </option>
                  ))}
                </select>
              </div>

              <div className="evidencias-campo">
                <label htmlFor="descripcionEvidencia">Descripcion</label>
                <textarea
                  id="descripcionEvidencia"
                  rows="3"
                  value={descripcion}
                  onChange={(e) => setDescripcion(e.target.value)}
                  maxLength={500}
                />
              </div>

              <div className="evidencias-acciones">
                <button type="submit" disabled={subiendo}>
                  {subiendo ? 'Subiendo...' : 'Subir evidencia'}
                </button>
              </div>
            </form>
          </section>
        )}

        <section className="evidencias-panel">
          <h2>Evidencias ({total})</h2>

          {!mantenimientoId ? (
            <p className="evidencias-vacio">
              Seleccione un mantenimiento para ver sus evidencias.
            </p>
          ) : evidencias.length === 0 ? (
            <p className="evidencias-vacio">
              Este mantenimiento no tiene evidencias registradas.
            </p>
          ) : (
            <table className="evidencias-tabla">
              <thead>
                <tr>
                  <th>Archivo</th>
                  <th>Tipo</th>
                  <th>Fecha</th>
                  {puedeEscribir && <th></th>}
                </tr>
              </thead>
              <tbody>
                {evidencias.map((evidencia) => (
                  <tr key={evidencia.id}>
                    <td>
                      <strong>{evidencia.nombreOriginal}</strong>
                      <small className="evidencias-sub">
                        {formatearTamano(evidencia.tamanoBytes)}
                      </small>
                      {evidencia.descripcion && (
                        <small className="evidencias-sub">
                          {evidencia.descripcion}
                        </small>
                      )}
                    </td>
                    <td>
                      <span
                        className={`evidencias-tipo ${evidencia.tipoArchivo.toLowerCase()}`}
                      >
                        {evidencia.tipoArchivo}
                      </span>
                    </td>
                    <td className="evidencias-fecha">
                      {formatearFecha(evidencia.fechaRegistro)}
                    </td>
                    {puedeEscribir && (
                      <td className="evidencias-fila-acciones">
                        <button
                          type="button"
                          className="secundario"
                          onClick={() => descargar(evidencia)}
                        >
                          Descargar
                        </button>
                        <button
                          type="button"
                          className="peligro"
                          onClick={() => eliminar(evidencia)}
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

          {mantenimientoId && (
            <Paginacion
              pagina={pagina}
              totalPaginas={totalPaginas}
              total={total}
              tamano={tamano}
              onCambiarPagina={cambiarPagina}
              onCambiarTamano={cambiarTamano}
            />
          )}
        </section>
      </div>
    </div>
  )
}
