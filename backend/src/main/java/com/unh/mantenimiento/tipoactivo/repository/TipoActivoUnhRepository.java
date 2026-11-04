package com.unh.mantenimiento.tipoactivo.repository;

import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoActivoUnhRepository extends JpaRepository<TipoActivoUnh, Integer> {

    Optional<TipoActivoUnh> findByNombre(String nombre);

    boolean existsByNombre(String nombre);

    List<TipoActivoUnh> findAllByOrderByNombreAsc();

    List<TipoActivoUnh> findAllByCategoriaOrderByNombreAsc(String categoria);
}
