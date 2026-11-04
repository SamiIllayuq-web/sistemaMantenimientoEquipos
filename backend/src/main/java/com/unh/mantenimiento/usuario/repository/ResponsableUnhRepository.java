package com.unh.mantenimiento.usuario.repository;

import com.unh.mantenimiento.modulo.ModuloUnh;
import com.unh.mantenimiento.modulo.ResponsableModuloUnh;
import com.unh.mantenimiento.usuario.model.ResponsableUnh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResponsableUnhRepository extends JpaRepository<ResponsableUnh, String> {

    Optional<ResponsableUnh> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByDni(String dni);

    /**
     * Modulos (codigos) asignados a un responsable.
     * Consulta explicita para evitar N+1 al construir el menu.
     */
    @Query("""
            SELECT m.codigo FROM ModuloUnh m
            JOIN ResponsableModuloUnh rm ON rm.idModulo = m.idModulo
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
            SELECT COUNT(DISTINCT r.dni) FROM ResponsableUnh r
            JOIN ResponsableModuloUnh rm ON rm.idResponsable = r.dni
            WHERE rm.idModulo = :idModulo AND r.estado = 'ACTIVO'
            """)
    long contarActivosConModulo(@Param("idModulo") Integer idModulo);
}
