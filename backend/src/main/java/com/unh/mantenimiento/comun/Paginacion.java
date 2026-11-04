package com.unh.mantenimiento.comun;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Utilidad de paginacion. La API usa paginas BASE 1 (la primera es la 1),
 * que es lo que espera ver el usuario; Spring Data usa base 0, asi que la
 * conversion vive aqui y no se dispersa por los servicios.
 */
public final class Paginacion {

    /** Registros por pagina si el cliente no pide otro tamano. */
    public static final int TAMANO_POR_DEFECTO = 20;

    /** Tope maximo: evita que alguien pida una pagina de un millon de filas. */
    public static final int TAMANO_MAXIMO = 100;

    private Paginacion() {
    }

    /**
     * Convierte los parametros de la API en un Pageable de Spring Data.
     *
     * @param pagina pagina base 1 (se normaliza a 1 si es menor)
     * @param tamano registros por pagina (se acota a TAMANO_MAXIMO)
     */
    public static Pageable aPageable(int pagina, int tamano, Sort orden) {
        int paginaSegura = Math.max(pagina, 1);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);
        return PageRequest.of(paginaSegura - 1, tamanoSeguro, orden);
    }

    /** Normaliza el numero de pagina(base 1) que se devuelve en la respuesta. */
    public static int normalizarPagina(int pagina) {
        return Math.max(pagina, 1);
    }

    /** Normaliza el tamano de pagina que se devuelve en la respuesta. */
    public static int normalizarTamano(int tamano) {
        if (tamano <= 0) {
            return TAMANO_POR_DEFECTO;
        }
        return Math.min(tamano, TAMANO_MAXIMO);
    }
}
