package com.unh.mantenimiento.incidencia.repository;

import com.unh.mantenimiento.incidencia.model.IncidenciaUnh;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidenciaUnhRepository extends JpaRepository<IncidenciaUnh, Integer> {

    /**
     * Listado con filtros opcionales.
     *
     * Los parametros nulos se resuelven con ':param IS NULL OR ...': no se
     * usa CONCAT con parametros nulos, que en Hibernate 7 termina aplicando
     * LOWER() sobre un tipo no textual.
     *
     * @EntityGraph carga el activo en la misma consulta: sin N+1.
     */
    @EntityGraph(attributePaths = {"activo"})
    @Query("""
            SELECT i FROM IncidenciaUnh i
            WHERE (:estado IS NULL OR i.estado = :estado)
              AND (:idActivo IS NULL OR i.activo.idActivo = :idActivo)
            ORDER BY i.fechaDeteccion DESC, i.idIncidencia DESC
            """)
    List<IncidenciaUnh> buscar(
            @Param("estado") String estado,
            @Param("idActivo") Integer idActivo);

    /** Listado paginado con los mismos filtros que {@link #buscar}. */
    @EntityGraph(attributePaths = {"activo"})
    @Query(value = """
            SELECT i FROM IncidenciaUnh i
            WHERE (:estado IS NULL OR i.estado = :estado)
              AND (:idActivo IS NULL OR i.activo.idActivo = :idActivo)
            ORDER BY i.fechaDeteccion DESC, i.idIncidencia DESC
            """,
            countQuery = """
            SELECT COUNT(i) FROM IncidenciaUnh i
            WHERE (:estado IS NULL OR i.estado = :estado)
              AND (:idActivo IS NULL OR i.activo.idActivo = :idActivo)
            """)
    Page<IncidenciaUnh> buscarPaginado(
            @Param("estado") String estado,
            @Param("idActivo") Integer idActivo,
            Pageable pageable);

    /** Detalle con el activo anidado. */
    @EntityGraph(attributePaths = {"activo"})
    @Query("SELECT i FROM IncidenciaUnh i WHERE i.idIncidencia = :id")
    Optional<IncidenciaUnh> buscarDetalle(@Param("id") Integer id);

    /** Incidencias abiertas de un activo (regla: un activo puede tener varias). */
    @Query("SELECT COUNT(i) FROM IncidenciaUnh i "
            + "WHERE i.activo.idActivo = :idActivo AND i.estado <> 'CERRADA'")
    long contarAbiertasPorActivo(@Param("idActivo") Integer idActivo);
}
