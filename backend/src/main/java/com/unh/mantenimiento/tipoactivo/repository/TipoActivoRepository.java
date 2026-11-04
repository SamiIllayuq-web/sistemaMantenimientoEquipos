package com.unh.mantenimiento.tipoactivo.repository;

import com.unh.mantenimiento.tipoactivo.model.TipoActivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoActivoRepository extends JpaRepository<TipoActivo, Integer> {

    Optional<TipoActivo> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

    List<TipoActivo> findAllByOrderByNombreAsc();

    List<TipoActivo> findAllByCategoriaOrderByNombreAsc(String categoria);
}
