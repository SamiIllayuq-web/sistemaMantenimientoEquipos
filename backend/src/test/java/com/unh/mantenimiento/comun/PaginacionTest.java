package com.unh.mantenimiento.comun;

import com.unh.mantenimiento.comun.dto.PaginaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la paginacion: normalizacion de pagina base 1 y limites.
 */
class PaginacionTest {

    @Test
    @DisplayName("La pagina de la API es base 1")
    void paginaBase1() {
        assertThat(Paginacion.normalizarPagina(1)).isEqualTo(1);
        assertThat(Paginacion.normalizarPagina(5)).isEqualTo(5);
        // Una pagina 0 o negativa se normaliza a 1.
        assertThat(Paginacion.normalizarPagina(0)).isEqualTo(1);
        assertThat(Paginacion.normalizarPagina(-3)).isEqualTo(1);
    }

    @Test
    @DisplayName("El tamano de pagina tiene un tope maximo")
    void tamanoConTope() {
        assertThat(Paginacion.normalizarTamano(0)).isEqualTo(Paginacion.TAMANO_POR_DEFECTO);
        assertThat(Paginacion.normalizarTamano(-1)).isEqualTo(Paginacion.TAMANO_POR_DEFECTO);
        assertThat(Paginacion.normalizarTamano(50)).isEqualTo(50);
        assertThat(Paginacion.normalizarTamano(9999)).isEqualTo(Paginacion.TAMANO_MAXIMO);
    }

    @Test
    @DisplayName("La respuesta pagina calcula el total de paginas")
    void totalPaginas() {
        PaginaResponse<String> pagina = PaginaResponse.de(List.of("a", "b"), 1, 20, 45);

        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.total()).isEqualTo(45);
        assertThat(pagina.contenido()).hasSize(2);
    }

    @Test
    @DisplayName("Un total menor que el tamano es una sola pagina")
    void unaSolaPagina() {
        PaginaResponse<String> pagina = PaginaResponse.de(List.of("a"), 1, 20, 3);

        assertThat(pagina.totalPaginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin registros hay cero paginas")
    void sinRegistros() {
        PaginaResponse<String> pagina = PaginaResponse.de(List.of(), 1, 20, 0);

        assertThat(pagina.totalPaginas()).isZero();
    }
}
