package com.unh.mantenimiento.comun.dto;

import java.util.List;

/**
 * Respuesta paginada: el contenido de la pagina actual mas los datos que
 * necesita el frontend para navegar (cuantas hay en total, cuantas paginas).
 *
 * @param contenido    los registros de la pagina pedida
 * @param pagina       numero de pagina (BASE 1: la primera es la 1)
 * @param tamano       registros por pagina
 * @param total        total de registros que cumplen el filtro
 * @param totalPaginas total de paginas segun el tamano pedido
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long total,
        int totalPaginas
) {

    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long total) {
        int totalPaginas = (tamano <= 0) ? 0 : (int) Math.ceil((double) total / (double) tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, total, totalPaginas);
    }
}
