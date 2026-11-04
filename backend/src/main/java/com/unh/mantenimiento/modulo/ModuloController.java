package com.unh.mantenimiento.modulo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de modulos. Publico: el frontend lo necesita para construir el
 * menu antes de iniciar sesion (no expone datos sensibles).
 */
@RestController
@RequestMapping("/api/modulos")
public class ModuloController {

    private final ModuloRepository moduloRepository;

    public ModuloController(ModuloRepository moduloRepository) {
        this.moduloRepository = moduloRepository;
    }

    @GetMapping
    public List<ModuloDTO> listar() {
        return moduloRepository.findAllByOrderByIdModuloAsc()
                .stream()
                .map(ModuloDTO::de)
                .toList();
    }

    /** DTO del modulo: nunca expone la tabla puente. */
    public record ModuloDTO(Integer id, String codigo, String nombre, String descripcion) {
        static ModuloDTO de(Modulo modulo) {
            return new ModuloDTO(
                    modulo.getIdModulo(),
                    modulo.getCodigo(),
                    modulo.getNombre(),
                    modulo.getDescripcion()
            );
        }
    }
}
