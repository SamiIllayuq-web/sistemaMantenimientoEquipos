/**
 * Controles de paginacion. Reutilizable por todos los listados paginados.
 *
 * Espera la forma de respuesta de la API:
 *   { contenido: [...], pagina, tamano, total, totalPaginas }
 *
 * La pagina es base 1 (la primera pagina es la 1).
 */
export default function Paginacion({
  pagina,
  totalPaginas,
  total,
  onCambiarPagina,
  onCambiarTamano,
  tamano,
}) {
  const desde = (pagina - 1) * tamano + 1
  const hasta = Math.min(pagina * tamano, total)
  const unaSolaPagina = !totalPaginas || totalPaginas <= 1

  return (
    <div className="paginacion">
      {unaSolaPagina ? (
        <span className="paginacion-info">
          {total} registro{total === 1 ? '' : 's'}
        </span>
      ) : (
        <>
          <span className="paginacion-info">
            Mostrando {desde}-{hasta} de {total}
          </span>

          <div className="paginacion-controles">
            <button
              type="button"
              onClick={() => onCambiarPagina(1)}
              disabled={pagina <= 1}
              title="Primera pagina"
            >
              {'<<'}
            </button>
            <button
              type="button"
              onClick={() => onCambiarPagina(pagina - 1)}
              disabled={pagina <= 1}
            >
              Anterior
            </button>

            <span className="paginacion-actual">
              Pagina {pagina} de {totalPaginas}
            </span>

            <button
              type="button"
              onClick={() => onCambiarPagina(pagina + 1)}
              disabled={pagina >= totalPaginas}
            >
              Siguiente
            </button>
            <button
              type="button"
              onClick={() => onCambiarPagina(totalPaginas)}
              disabled={pagina >= totalPaginas}
              title="Ultima pagina"
            >
              {'>>'}
            </button>
          </div>
        </>
      )}

      {onCambiarTamano && (
        <label className="paginacion-tamano">
          Por pagina
          <select
            value={tamano}
            onChange={(e) => onCambiarTamano(Number(e.target.value))}
          >
            {[10, 20, 50].map((opcion) => (
              <option key={opcion} value={opcion}>
                {opcion}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  )
}
