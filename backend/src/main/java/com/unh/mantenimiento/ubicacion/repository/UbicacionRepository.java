package com.unh.mantenimiento.ubicacion.repository;

import com.unh.mantenimiento.ubicacion.model.Ubicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Integer> {

    Optional<Ubicacion> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

    List<Ubicacion> findAllByOrderByNombreAsc();
}
