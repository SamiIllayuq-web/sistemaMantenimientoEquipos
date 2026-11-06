package com.unh.mantenimiento.evidencia.service;

import com.unh.mantenimiento.comun.Paginacion;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.evidencia.dto.EvidenciaDtos;
import com.unh.mantenimiento.evidencia.model.EvidenciaMantenimientoUnh;
import com.unh.mantenimiento.evidencia.repository.EvidenciaMantenimientoUnhRepository;
import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import com.unh.mantenimiento.mantenimiento.repository.MantenimientoUnhRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de evidencias. Deep module: interfaz pequena, logica densa adentro.
 *
 * Reglas que vive aqui:
 * - Solo se aceptan JPG, PNG y PDF, de hasta 10 MB.
 * - El archivo se guarda con un nombre GENERADO en una carpeta que NO es
 *   publica. El nombre que envio el usuario nunca se usa en disco.
 * - La evidencia pertenece SIEMPRE a un mantenimiento existente.
 * - La descarga exige sesion y el modulo EVIDENCIAS (eso lo aplica el
 *   controlador con @PreAuthorize).
 */
@Service
public class EvidenciaUnhService {

    /** Tamano maximo permitido: 10 MB. */
    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

    /** Extensiones permitidas. */
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "pdf");

    /** Content types aceptados, para no confiar solo en la extension. */
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf");

    private final EvidenciaMantenimientoUnhRepository evidenciaRepository;
    private final MantenimientoUnhRepository mantenimientoRepository;
    private final Path carpeta;

    public EvidenciaUnhService(
            EvidenciaMantenimientoUnhRepository evidenciaRepository,
            MantenimientoUnhRepository mantenimientoRepository,
            @Value("${EVIDENCIAS_DIR:./evidencias}") String carpetaBase) {
        this.evidenciaRepository = evidenciaRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.carpeta = Paths.get(carpetaBase).toAbsolutePath().normalize();
    }

    /**
     * Registra una evidencia: valida el archivo, lo guarda con nombre generado
     * y guarda la referencia en la base.
     *
     * @param idMantenimiento el mantenimiento al que pertenece
     * @param archivo         el archivo subido
     * @param request         los metadatos (descripcion y tipo)
     */
    @Transactional
    public EvidenciaDtos.EvidenciaResponse registrar(
            Integer idMantenimiento,
            MultipartFile archivo,
            EvidenciaDtos.RegistrarEvidenciaRequest request) {

        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("No se recibio ningun archivo");
        }

        MantenimientoUnh mantenimiento = mantenimientoRepository.buscarDetalle(idMantenimiento)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un mantenimiento con ID " + idMantenimiento));

        validarArchivo(archivo);

        String nombreOriginal = sanearNombreOriginal(archivo.getOriginalFilename());
        String nombreGenerado = generarNombre(nombreOriginal);
        Path destino = carpeta.resolve(nombreGenerado);

        try {
            Files.createDirectories(carpeta);
            try (InputStream entrada = archivo.getInputStream()) {
                Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new OperacionInvalidaException(
                    "No se pudo guardar el archivo en el servidor: " + error.getMessage());
        }

        EvidenciaMantenimientoUnh evidencia = new EvidenciaMantenimientoUnh();
        evidencia.setMantenimiento(mantenimiento);
        evidencia.setTipoArchivo(resolverTipo(request.tipoArchivo(), nombreOriginal));
        evidencia.setNombreArchivo(nombreGenerado);
        evidencia.setNombreOriginal(nombreOriginal);
        evidencia.setDescripcionEvidencia(request.descripcion());
        evidencia.setFechaRegistroEvidencia(LocalDateTime.now());

        evidenciaRepository.save(evidencia);

        return aResponse(evidencia, archivo.getSize());
    }

    /** Lista paginada de las evidencias de un mantenimiento. */
    @Transactional(readOnly = true)
    public PaginaResponse<EvidenciaDtos.EvidenciaResponse> listarPorMantenimiento(
            Integer idMantenimiento, int pagina, int tamano) {

        if (!mantenimientoRepository.existsById(idMantenimiento)) {
            throw new RecursoNoEncontradoException(
                    "No existe un mantenimiento con ID " + idMantenimiento);
        }

        int paginaSegura = Paginacion.normalizarPagina(pagina);
        int tamanoSeguro = Paginacion.normalizarTamano(tamano);
        Pageable pageable = Paginacion.aPageable(
                paginaSegura,
                tamanoSeguro,
                Sort.by(Sort.Order.desc("fechaRegistroEvidencia"), Sort.Order.desc("idEvidencia")));

        Page<EvidenciaMantenimientoUnh> resultado =
                evidenciaRepository.buscarPorMantenimiento(idMantenimiento, pageable);

        return PaginaResponse.de(
                resultado.getContent().stream().map(e -> aResponse(e, null)).toList(),
                paginaSegura,
                tamanoSeguro,
                resultado.getTotalElements());
    }

    /**
     * Devuelve el archivo para descargar.
     *
     * @return la evidencia y su ruta en disco
     */
    @Transactional(readOnly = true)
    public ArchivoEvidencia obtenerParaDescarga(Integer idEvidencia) {
        EvidenciaMantenimientoUnh evidencia = evidenciaRepository.findById(idEvidencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una evidencia con ID " + idEvidencia));

        Path ruta = carpeta.resolve(evidencia.getNombreArchivo()).normalize();

        // Defensa extra: el nombre generado nunca debe salir de la carpeta.
        if (!ruta.startsWith(carpeta)) {
            throw new OperacionInvalidaException("Ruta de archivo no valida");
        }

        if (!Files.exists(ruta) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException(
                    "El archivo de la evidencia ya no esta en el servidor");
        }

        try {
            return new ArchivoEvidencia(
                    ruta,
                    evidencia.getNombreOriginal(),
                    Files.size(ruta));
        } catch (IOException error) {
            throw new OperacionInvalidaException(
                    "No se pudo leer el archivo de la evidencia: " + error.getMessage());
        }
    }

    /**
     * Elimina una evidencia: borra el archivo de disco y el registro.
     * El archivo se borra primero: si falla, el registro sigue y se puede
     * reintentar.
     */
    @Transactional
    public void eliminar(Integer idEvidencia) {
        EvidenciaMantenimientoUnh evidencia = evidenciaRepository.findById(idEvidencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una evidencia con ID " + idEvidencia));

        Path ruta = carpeta.resolve(evidencia.getNombreArchivo()).normalize();
        if (ruta.startsWith(carpeta) && Files.exists(ruta)) {
            try {
                Files.delete(ruta);
            } catch (IOException error) {
                throw new OperacionInvalidaException(
                        "No se pudo borrar el archivo del servidor: " + error.getMessage());
            }
        }

        evidenciaRepository.delete(evidencia);
    }

    /** Valida tamano, extension y content type del archivo. */
    private void validarArchivo(MultipartFile archivo) {
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new OperacionInvalidaException(
                    "El archivo supera el tamano maximo de 10 MB");
        }

        String nombre = archivo.getOriginalFilename();
        String extension = extensionDe(nombre);

        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new OperacionInvalidaException(
                    "Tipo de archivo no permitido. Solo se aceptan JPG, PNG y PDF");
        }

        String tipo = archivo.getContentType();
        if (tipo != null && !TIPOS_PERMITIDOS.contains(tipo.toLowerCase(Locale.ROOT))) {
            throw new OperacionInvalidaException(
                    "El contenido del archivo no coincide con un JPG, PNG o PDF");
        }
    }

    /** El tipo se deduce del archivo si el usuario no lo indico. */
    private String resolverTipo(String tipoIndicado, String nombreOriginal) {
        if (tipoIndicado != null && !tipoIndicado.isBlank()) {
            return tipoIndicado.toUpperCase(Locale.ROOT);
        }
        return "pdf".equals(extensionDe(nombreOriginal))
                ? EvidenciaMantenimientoUnh.INFORME
                : EvidenciaMantenimientoUnh.FOTO;
    }

    /** Nombre generado: nunca el del usuario. */
    private String generarNombre(String nombreOriginal) {
        return UUID.randomUUID() + extensionConPunto(extensionDe(nombreOriginal));
    }

    /**
     * Limpia el nombre original: solo se queda del nombre lo que sea seguro
     * mostrar. No se usa para construir la ruta del disco.
     */
    private String sanearNombreOriginal(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "archivo";
        }
        // Se quita la ruta que algunos navegadores agregan y los separadores.
        String limpio = nombreOriginal.replace('\\', '/');
        int ultimaBarra = limpio.lastIndexOf('/');
        if (ultimaBarra >= 0) {
            limpio = limpio.substring(ultimaBarra + 1);
        }
        limpio = limpio.replaceAll("[^A-Za-z0-9._-]", "_");
        if (limpio.length() > 255) {
            limpio = limpio.substring(0, 255);
        }
        return limpio.isBlank() ? "archivo" : limpio;
    }

    /** Extension en minusculas, sin el punto. */
    private String extensionDe(String nombre) {
        if (nombre == null) {
            return "";
        }
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            return "";
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private String extensionConPunto(String extension) {
        return extension.isBlank() ? "" : "." + extension;
    }

    private EvidenciaDtos.EvidenciaResponse aResponse(
            EvidenciaMantenimientoUnh evidencia, Long tamanoBytes) {
        return new EvidenciaDtos.EvidenciaResponse(
                evidencia.getIdEvidencia(),
                evidencia.getMantenimiento().getIdMantenimiento(),
                evidencia.getTipoArchivo(),
                evidencia.getNombreArchivo(),
                evidencia.getNombreOriginal(),
                evidencia.getDescripcionEvidencia(),
                evidencia.getFechaRegistroEvidencia(),
                tamanoBytes);
    }

    /**
     * Archivo listo para descargar: su ruta, el nombre que se le muestra al
     * usuario y el tamano.
     */
    public record ArchivoEvidencia(Path ruta, String nombreOriginal, long tamanoBytes) {
    }
}
