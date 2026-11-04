package com.unh.mantenimiento.ubicacion.repository;

import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UbicacionUnhRepository extends JpaRepository<UbicacionUnh, Integer> {

    Optional<UbicacionUnh> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

    List<UbicacionUnh> findAllByOrderByNombreAsc();
}
