package com.unh.mantenimiento.mantenimiento.repository;

import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MantenimientoUnhRepository extends JpaRepository<MantenimientoUnh, Integer> {

    /**
     * Listado paginado con filtros opcionales.
     *
     * @param estado    EN_PROCESO, FINALIZADO o null (todos)
     * @param idActivo  filtra por activo (null = todos)
     * @param tipo      CORRECTIVO, PREVENTIVO o null (todos)
     *
     * @EntityGraph carga activo, responsable e incidencia en la misma
     * consulta: sin N+1.
     */
    @EntityGraph(attributePaths = {"activo", "responsable", "incidencia"})
    @Query(value = """
            SELECT m FROM MantenimientoUnh m
            WHERE (:estado IS NULL OR m.estadoMantenimiento = :estado)
              AND (:idActivo IS NULL OR m.activo.idActivo = :idActivo)
              AND (:tipo IS NULL OR m.tipoMantenimiento = :tipo)
            ORDER BY m.fechaInicio DESC, m.idMantenimiento DESC
            """,
            countQuery = """
            SELECT COUNT(m) FROM MantenimientoUnh m
            WHERE (:estado IS NULL OR m.estadoMantenimiento = :estado)
              AND (:idActivo IS NULL OR m.activo.idActivo = :idActivo)
              AND (:tipo IS NULL OR m.tipoMantenimiento = :tipo)
            """)
    Page<MantenimientoUnh> buscarPaginado(
            @Param("estado") String estado,
            @Param("idActivo") Integer idActivo,
            @Param("tipo") String tipo,
            Pageable pageable);

    /** Detalle con activo, responsable e incidencia anidados. */
    @EntityGraph(attributePaths = {"activo", "responsable", "incidencia"})
    @Query("SELECT m FROM MantenimientoUnh m WHERE m.idMantenimiento = :id")
    java.util.Optional<MantenimientoUnh> buscarDetalle(@Param("id") Integer id);

    /**
     * Historial de un activo: sus mantenimientos, del mas reciente al mas
     * antiguo. Se usa en el boton "Historial" de la pantalla de Activos.
     */
    @EntityGraph(attributePaths = {"activo", "responsable", "incidencia"})
    @Query("""
            SELECT m FROM MantenimientoUnh m
            WHERE m.activo.idActivo = :idActivo
            ORDER BY m.fechaInicio DESC, m.idMantenimiento DESC
            """)
    List<MantenimientoUnh> historialPorActivo(@Param("idActivo") Integer idActivo);

    /** Cuenta los mantenimientos que referencian una incidencia. */
    @Query("SELECT COUNT(m) FROM MantenimientoUnh m WHERE m.incidencia.idIncidencia = :id")
    long contarPorIncidencia(@Param("id") Integer idIncidencia);
}
