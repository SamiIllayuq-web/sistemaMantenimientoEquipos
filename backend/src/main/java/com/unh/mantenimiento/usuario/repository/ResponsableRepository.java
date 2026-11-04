package com.unh.mantenimiento.usuario.repository;

import com.unh.mantenimiento.modulo.Modulo;
import com.unh.mantenimiento.modulo.ResponsableModulo;
import com.unh.mantenimiento.usuario.model.Responsable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResponsableRepository extends JpaRepository<Responsable, String> {

    Optional<Responsable> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByDni(String dni);

    /**
     * Modulos (codigos) asignados a un responsable.
     * Consulta explicita para evitar N+1 al construir el menu.
     */
    @Query("""
            SELECT m.codigo FROM Modulo m
            JOIN ResponsableModulo rm ON rm.idModulo = m.idModulo
            WHERE rm.idResponsable = :dni
            ORDER BY m.idModulo
            """)
    List<String> findCodigosModulos(@Param("dni") String dni);

    /**
     * Cuenta los responsables ACTIVO que tienen asignado el modulo indicado.
     * Se usa para impedir quitar el modulo USUARIOS o desactivar al ultimo
     * administrador.
     */
    @Query("""
            SELECT COUNT(DISTINCT r.dni) FROM Responsable r
            JOIN ResponsableModulo rm ON rm.idResponsable = r.dni
            WHERE rm.idModulo = :idModulo AND r.estado = 'ACTIVO'
            """)
    long contarActivosConModulo(@Param("idModulo") Integer idModulo);
}
