package com.unh.mantenimiento.activo.repository;

import com.unh.mantenimiento.activo.model.ActivoUnh;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivoUnhRepository extends JpaRepository<ActivoUnh, Integer> {

    Optional<ActivoUnh> findByCodigoPatrimonial(String codigoPatrimonial);

    boolean existsByCodigoPatrimonial(String codigoPatrimonial);

    /**
     * Listado con filtros. Solo devuelve activos ACTIVOS (estado de gestion)
     * salvo que se pida explicitamente un estado.
     *
     * La busqueda textual usa un patron ya construido ('%texto%') en vez de
     * CONCAT('%', :texto, '%'): el CONCAT de Hibernate 7 con un parametro
     * nulo hace que LOWER() se resuelva sobre un tipo no textual y la BD
     * responde 'function lower(bytea) does not exist'.
     *
     * @EntityGraph carga tipo y ubicacion en la misma consulta: sin N+1.
     */
    @EntityGraph(attributePaths = {"tipoActivo", "ubicacion"})
    @Query("""
            SELECT a FROM ActivoUnh a
            WHERE (:estado IS NULL OR a.estado = :estado)
              AND (:patron IS NULL
                   OR LOWER(a.nombre) LIKE :patron
                   OR LOWER(a.codigoPatrimonial) LIKE :patron)
            ORDER BY a.nombre ASC
            """)
    List<ActivoUnh> buscar(
            @Param("patron") String patron, @Param("estado") String estado);

    /** Detalle con tipo y ubicacion anidados. */
    @EntityGraph(attributePaths = {"tipoActivo", "ubicacion"})
    @Query("SELECT a FROM ActivoUnh a WHERE a.idActivo = :id")
    Optional<ActivoUnh> buscarDetalle(@Param("id") Integer id);

    /** Cuenta los activos que referencian un tipo de activo (guard de borrado). */
    @Query("SELECT COUNT(a) FROM ActivoUnh a WHERE a.tipoActivo.idTipoActivo = :id")
    long contarPorTipoActivo(@Param("id") Integer idTipoActivo);

    /** Cuenta los activos que referencian una ubicacion (guard de borrado). */
    @Query("SELECT COUNT(a) FROM ActivoUnh a WHERE a.ubicacion.idUbicacion = :id")
    long contarPorUbicacion(@Param("id") Integer idUbicacion);
}
