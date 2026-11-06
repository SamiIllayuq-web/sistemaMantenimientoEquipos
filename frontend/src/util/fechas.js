/**
 * Manejo de fechas y horas entre el navegador y el servidor.
 *
 * REGLA IMPORTANTE: las fechas se envian como HORA LOCAL, sin conversion a
 * UTC. El servidor (Java/PostgreSQL) trabaja con hora local y sus validaciones
 * (`@PastOrPresent`) comparan contra su propio reloj.
 *
 * Si se usara `toISOString()`, un navegador en UTC-5 (Peru) enviaria una hora
 * 5 horas por delante: el servidor la rechazaria con "La fecha de fin no
 * puede ser futura" aunque el usuario haya puesto la hora actual.
 */

/** Devuelve la fecha y hora actual en formato local `AAAA-MM-DDTHH:MM`. */
export function ahoraLocal(segundos = false) {
  const fecha = new Date()
  const dosDigitos = (n) => String(n).padStart(2, '0')
  const parte = `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}` +
    `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}`
  return segundos ? `${parte}:${dosDigitos(fecha.getSeconds())}` : parte
}

/**
 * Convierte un valor de `<input type="datetime-local">` (hora local, sin zona)
 * al formato que espera el servidor: `AAAA-MM-DDTHH:MM:SS` en hora local.
 *
 * Devuelve null si el valor esta vacio. No usa `new Date(...)` a proposito:
 * ese constructor interpretaria el texto como UTC y volveria a la trampa del
 * desfase.
 */
export function aFechaLocalServidor(valor) {
  if (!valor) return null
  return valor.length === 16 ? `${valor}:00` : valor
}

/** Formatea una fecha ISO a texto legible. */
export function formatearFecha(fecha) {
  if (!fecha) return '-'
  const d = new Date(fecha)
  return d.toLocaleString('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Formatea una fecha simple (AAAA-MM-DD) a texto legible. */
export function formatearDia(fecha) {
  if (!fecha) return '-'
  const d = new Date(`${fecha}T00:00:00`)
  return d.toLocaleDateString('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}
