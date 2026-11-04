package com.unh.mantenimiento.modulo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ModuloUnhRepository extends JpaRepository<ModuloUnh, Integer> {

    Optional<ModuloUnh> findByCodigo(String codigo);

    List<ModuloUnh> findAllByOrderByIdModuloAsc();

    @Query("""
            SELECT m FROM ModuloUnh m
            JOIN ResponsableModuloUnh rm ON rm.idModulo = m.idModulo
            WHERE rm.idResponsable = :dni
            ORDER BY m.idModulo
            """)
    List<ModuloUnh> findModulosDe(@Param("dni") String dni);
}
