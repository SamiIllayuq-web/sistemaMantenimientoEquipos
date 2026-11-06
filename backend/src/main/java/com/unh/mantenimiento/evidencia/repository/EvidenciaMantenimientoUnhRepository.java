package com.unh.mantenimiento.evidencia.repository;

import com.unh.mantenimiento.evidencia.model.EvidenciaMantenimientoUnh;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvidenciaMantenimientoUnhRepository
        extends JpaRepository<EvidenciaMantenimientoUnh, Integer> {

    /**
     * Listado paginado de las evidencias de un mantenimiento.
     *
     * @param idMantenimiento el mantenimiento cuyas evidencias se piden
     */
    @Query(value = """
            SELECT e FROM EvidenciaMantenimientoUnh e
            WHERE e.mantenimiento.idMantenimiento = :idMantenimiento
            ORDER BY e.fechaRegistroEvidencia DESC, e.idEvidencia DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM EvidenciaMantenimientoUnh e
            WHERE e.mantenimiento.idMantenimiento = :idMantenimiento
            """)
    Page<EvidenciaMantenimientoUnh> buscarPorMantenimiento(
            @Param("idMantenimiento") Integer idMantenimiento,
            Pageable pageable);

    /** Evidencias de un mantenimiento, sin paginar. */
    @Query("""
            SELECT e FROM EvidenciaMantenimientoUnh e
            WHERE e.mantenimiento.idMantenimiento = :idMantenimiento
            ORDER BY e.fechaRegistroEvidencia DESC, e.idEvidencia DESC
            """)
    List<EvidenciaMantenimientoUnh> listarPorMantenimiento(
            @Param("idMantenimiento") Integer idMantenimiento);

    /** Busca por el nombre generado en disco (para la descarga). */
    @Query("""
            SELECT e FROM EvidenciaMantenimientoUnh e
            WHERE e.nombreArchivo = :nombreArchivo
            """)
    java.util.Optional<EvidenciaMantenimientoUnh> buscarPorNombreArchivo(
            @Param("nombreArchivo") String nombreArchivo);
}
