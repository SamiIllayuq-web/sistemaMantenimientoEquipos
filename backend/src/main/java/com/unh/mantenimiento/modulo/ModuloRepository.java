package com.unh.mantenimiento.modulo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ModuloRepository extends JpaRepository<Modulo, Integer> {

    Optional<Modulo> findByCodigo(String codigo);

    List<Modulo> findAllByOrderByIdModuloAsc();

    @Query("""
            SELECT m FROM Modulo m
            JOIN ResponsableModulo rm ON rm.idModulo = m.idModulo
            WHERE rm.idResponsable = :dni
            ORDER BY m.idModulo
            """)
    List<Modulo> findModulosDe(@Param("dni") String dni);
}
