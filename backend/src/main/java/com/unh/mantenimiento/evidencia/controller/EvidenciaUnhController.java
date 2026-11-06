package com.unh.mantenimiento.evidencia.controller;

import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.evidencia.dto.EvidenciaDtos;
import com.unh.mantenimiento.evidencia.service.EvidenciaUnhService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Controlador de evidencias.
 *
 * LECTURA y ESCRITURA: exigen la autoridad MOD_EVIDENCIAS. A diferencia de
 * los catalogos, una evidencia es un archivo que se descarga: el servidor
 * exige el modulo en TODAS las operaciones, incluida la descarga.
 */
@RestController
@RequestMapping("/api/evidencias")
public class EvidenciaUnhController {

    private final EvidenciaUnhService evidenciaService;

    public EvidenciaUnhController(EvidenciaUnhService evidenciaService) {
        this.evidenciaService = evidenciaService;
    }

    /**
     * Lista paginada de las evidencias de un mantenimiento.
     *
     * @param idMantenimiento el mantenimiento cuyas evidencias se piden
     * @param pagina    numero de pagina, base 1 (por defecto 1)
     * @param tamano    registros por pagina (por defecto 20, maximo 100)
     */
    @GetMapping("/mantenimiento/{idMantenimiento}")
    @PreAuthorize("hasAuthority('MOD_EVIDENCIAS')")
    public PaginaResponse<EvidenciaDtos.EvidenciaResponse> listar(
            @PathVariable Integer idMantenimiento,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return evidenciaService.listarPorMantenimiento(idMantenimiento, pagina, tamano);
    }

    /**
     * Sube un archivo como evidencia de un mantenimiento.
     *
     * El archivo viaja en el campo `archivo` (multipart) y los metadatos en
     * `datos` (JSON), que es la forma que acepta el formulario del frontend.
     */
    @PostMapping("/mantenimiento/{idMantenimiento}")
    @PreAuthorize("hasAuthority('MOD_EVIDENCIAS')")
    public ResponseEntity<EvidenciaDtos.EvidenciaResponse> registrar(
            @PathVariable Integer idMantenimiento,
            @RequestPart("archivo") MultipartFile archivo,
            @Valid @RequestPart(value = "datos", required = false)
            EvidenciaDtos.RegistrarEvidenciaRequest request) {

        EvidenciaDtos.RegistrarEvidenciaRequest datos = (request == null)
                ? new EvidenciaDtos.RegistrarEvidenciaRequest(null, null)
                : request;

        EvidenciaDtos.EvidenciaResponse creada =
                evidenciaService.registrar(idMantenimiento, archivo, datos);

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /** Descarga el archivo de una evidencia. */
    @GetMapping("/{id}/descargar")
    @PreAuthorize("hasAuthority('MOD_EVIDENCIAS')")
    public ResponseEntity<Resource> descargar(@PathVariable Integer id) {
        EvidenciaUnhService.ArchivoEvidencia archivo = evidenciaService.obtenerParaDescarga(id);

        String nombre = archivo.nombreOriginal();
        String nombreCodificado = URLEncoder.encode(nombre, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreCodificado + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(archivo.tamanoBytes())
                .body(new FileSystemResource(archivo.ruta()));
    }

    /** Elimina una evidencia: borra el archivo y su registro. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MOD_EVIDENCIAS')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        evidenciaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
