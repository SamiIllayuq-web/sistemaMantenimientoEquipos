package com.unh.mantenimiento.evidencia.service;

import com.unh.mantenimiento.activo.dto.ActivoDtos;
import com.unh.mantenimiento.activo.model.ActivoUnh;
import com.unh.mantenimiento.activo.service.ActivoUnhService;
import com.unh.mantenimiento.comun.dto.PaginaResponse;
import com.unh.mantenimiento.comun.excepcion.OperacionInvalidaException;
import com.unh.mantenimiento.comun.excepcion.RecursoNoEncontradoException;
import com.unh.mantenimiento.evidencia.dto.EvidenciaDtos;
import com.unh.mantenimiento.evidencia.model.EvidenciaMantenimientoUnh;
import com.unh.mantenimiento.evidencia.repository.EvidenciaMantenimientoUnhRepository;
import com.unh.mantenimiento.mantenimiento.dto.MantenimientoDtos;
import com.unh.mantenimiento.mantenimiento.model.MantenimientoUnh;
import com.unh.mantenimiento.mantenimiento.repository.MantenimientoUnhRepository;
import com.unh.mantenimiento.mantenimiento.service.MantenimientoUnhService;
import com.unh.mantenimiento.seguridad.model.UsuarioAutenticado;
import com.unh.mantenimiento.tipoactivo.model.TipoActivoUnh;
import com.unh.mantenimiento.tipoactivo.repository.TipoActivoUnhRepository;
import com.unh.mantenimiento.ubicacion.model.UbicacionUnh;
import com.unh.mantenimiento.ubicacion.repository.UbicacionUnhRepository;
import com.unh.mantenimiento.usuario.model.ResponsableUnh;
import com.unh.mantenimiento.usuario.repository.ResponsableUnhRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del modulo de evidencias. Corre contra PostgreSQL real.
 *
 * Los archivos se guardan en una carpeta temporal: los tests no deben dejar
 * basura en la carpeta real de evidencias.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "EVIDENCIAS_DIR=${java.io.tmpdir}/evidencias-test-i6")
class EvidenciaUnhServiceTest {

    @Autowired
    private EvidenciaUnhService evidenciaService;

    @Autowired
    private EvidenciaMantenimientoUnhRepository evidenciaRepository;

    @Autowired
    private MantenimientoUnhRepository mantenimientoRepository;

    @Autowired
    private MantenimientoUnhService mantenimientoService;

    @Autowired
    private ActivoUnhService activoService;

    @Autowired
    private ResponsableUnhRepository responsableRepository;

    @Autowired
    private TipoActivoUnhRepository tipoActivoRepository;

    @Autowired
    private UbicacionUnhRepository ubicacionRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String DNI = "77777777";

    private Integer idTipo;
    private Integer idUbicacion;
    private Integer idActivo;
    private Integer idMantenimiento;

    @BeforeEach
    void preparar() {
        ResponsableUnh responsable = new ResponsableUnh();
        responsable.setDni(DNI);
        responsable.setNombres("Tecnico");
        responsable.setApellidos("De Evidencia");
        responsable.setCorreo("evidencia." + System.nanoTime() + "@unh.edu.pe");
        responsable.setClaveHash("x");
        responsable.setEstado("ACTIVO");
        responsable.setFechaRegistro(LocalDateTime.now());
        responsable.setIntentosFallidos(0);
        responsableRepository.save(responsable);
        entityManager.flush();

        UsuarioAutenticado usuario = new UsuarioAutenticado(
                DNI, responsable.getCorreo(), "Tecnico De Evidencia", "ACTIVO",
                List.of("MOD_MANTENIMIENTOS", "MOD_EVIDENCIAS"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));

        TipoActivoUnh tipo = new TipoActivoUnh();
        tipo.setNombre("Tipo evidencia " + System.nanoTime());
        tipo.setCategoria(TipoActivoUnh.CATEGORIA_EQUIPO);
        tipo.setRequiereMantenimiento(true);
        idTipo = tipoActivoRepository.save(tipo).getIdTipoActivo();

        UbicacionUnh ubicacion = new UbicacionUnh();
        ubicacion.setNombre("Sala evidencia " + System.nanoTime());
        ubicacion.setTipoUbicacion("SALA");
        idUbicacion = ubicacionRepository.save(ubicacion).getIdUbicacion();

        idActivo = activoService.crear(new ActivoDtos.CrearActivoRequest(
                "PAT-EVI-" + (System.nanoTime() % 100000), "Activo con evidencia",
                idTipo, idUbicacion, null, null, null, null, null,
                ActivoUnh.OPERATIVO, null, null)).id();

        MantenimientoDtos.MantenimientoResponse mantenimiento =
                mantenimientoService.crearPreventivo(
                        new MantenimientoDtos.CrearPreventivoRequest(idActivo, null, "Trabajo con evidencia"));
        idMantenimiento = mantenimiento.id();
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Un archivo PNG minimo y valido. */
    private MockMultipartFile archivoPng(String nombre) {
        byte[] contenido = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };
        return new MockMultipartFile("archivo", nombre, "image/png", contenido);
    }

    @Test
    @DisplayName("Registrar una evidencia la guarda y devuelve sus datos")
    void registrarEvidencia() {
        EvidenciaDtos.EvidenciaResponse creada = evidenciaService.registrar(
                idMantenimiento,
                archivoPng("foto-terma.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest("Foto de la terma", "FOTO"));

        assertThat(creada.id()).isNotNull();
        assertThat(creada.idMantenimiento()).isEqualTo(idMantenimiento);
        assertThat(creada.tipoArchivo()).isEqualTo(EvidenciaMantenimientoUnh.FOTO);
        assertThat(creada.nombreOriginal()).isEqualTo("foto-terma.png");
        assertThat(creada.descripcion()).isEqualTo("Foto de la terma");
        assertThat(creada.fechaRegistro()).isNotNull();
    }

    @Test
    @DisplayName("El archivo se guarda con nombre generado, no con el del usuario")
    void elNombreEnDiscoEsGenerado() {
        EvidenciaDtos.EvidenciaResponse creada = evidenciaService.registrar(
                idMantenimiento,
                archivoPng("mi foto secreta.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        // El nombre original se conserva para mostrarlo...
        assertThat(creada.nombreOriginal()).isEqualTo("mi_foto_secreta.png");
        // ...pero en disco el nombre es generado (UUID).
        assertThat(creada.nombreArchivo()).doesNotContain("mi_foto_secreta");
        assertThat(creada.nombreArchivo()).endsWith(".png");
    }

    @Test
    @DisplayName("El archivo se escribe en la carpeta del servidor")
    void elArchivoSeGuardaEnDisco() {
        EvidenciaDtos.EvidenciaResponse creada = evidenciaService.registrar(
                idMantenimiento,
                archivoPng("evidencia.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        EvidenciaUnhService.ArchivoEvidencia archivo =
                evidenciaService.obtenerParaDescarga(creada.id());

        assertThat(archivo.ruta()).exists();
        assertThat(archivo.tamanoBytes()).isPositive();
        assertThat(archivo.nombreOriginal()).isEqualTo("evidencia.png");
    }

    @Test
    @DisplayName("Se rechaza un archivo que no es JPG, PNG ni PDF")
    void seRechazaTipoNoPermitido() {
        MockMultipartFile ejecutable =
                new MockMultipartFile("archivo", "virus.exe", "application/x-msdownload", "contenido".getBytes());

        assertThatThrownBy(() -> evidenciaService.registrar(
                idMantenimiento, ejecutable,
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    @DisplayName("Se rechaza un archivo que supera los 10 MB")
    void seRechazaArchivoGrande() {
        byte[] grande = new byte[(int) (10L * 1024 * 1024 + 1)];
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "grande.png", "image/png", grande);

        assertThatThrownBy(() -> evidenciaService.registrar(
                idMantenimiento, archivo,
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("tamano maximo");
    }

    @Test
    @DisplayName("Se rechaza un archivo vacio")
    void seRechazaArchivoVacio() {
        MockMultipartFile vacio =
                new MockMultipartFile("archivo", "vacio.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> evidenciaService.registrar(
                idMantenimiento, vacio,
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null)))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("ningun archivo");
    }

    @Test
    @DisplayName("Una evidencia sobre un mantenimiento inexistente da 404")
    void mantenimientoInexistente() {
        assertThatThrownBy(() -> evidenciaService.registrar(
                999999, archivoPng("x.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar trae solo las evidencias del mantenimiento pedido")
    void listarPorMantenimiento() {
        Integer otroActivo = activoService.crear(new ActivoDtos.CrearActivoRequest(
                "PAT-EVI-OTRO-" + (System.nanoTime() % 100000), "Otro activo",
                idTipo, idUbicacion, null, null, null, null, null,
                ActivoUnh.OPERATIVO, null, null)).id();
        Integer otroMantenimiento = mantenimientoService.crearPreventivo(
                new MantenimientoDtos.CrearPreventivoRequest(otroActivo, null, null)).id();

        evidenciaService.registrar(idMantenimiento, archivoPng("a.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));
        evidenciaService.registrar(idMantenimiento, archivoPng("b.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));
        evidenciaService.registrar(otroMantenimiento, archivoPng("c.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        PaginaResponse<EvidenciaDtos.EvidenciaResponse> pagina =
                evidenciaService.listarPorMantenimiento(idMantenimiento, 1, 20);

        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.contenido()).hasSize(2);
    }

    @Test
    @DisplayName("Listar las evidencias de un mantenimiento inexistente da 404")
    void listarMantenimientoInexistente() {
        assertThatThrownBy(() -> evidenciaService.listarPorMantenimiento(999999, 1, 20))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Eliminar una evidencia borra el archivo y el registro")
    void eliminarEvidencia() throws Exception {
        EvidenciaDtos.EvidenciaResponse creada = evidenciaService.registrar(
                idMantenimiento, archivoPng("borrar.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        EvidenciaUnhService.ArchivoEvidencia archivo =
                evidenciaService.obtenerParaDescarga(creada.id());
        Path ruta = archivo.ruta();
        assertThat(ruta).exists();

        evidenciaService.eliminar(creada.id());

        assertThat(evidenciaRepository.findById(creada.id())).isEmpty();
        assertThat(Files.exists(ruta)).isFalse();
    }

    @Test
    @DisplayName("Descargar una evidencia inexistente da 404")
    void descargarInexistente() {
        assertThatThrownBy(() -> evidenciaService.obtenerParaDescarga(999999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("El tipo se deduce del archivo cuando el usuario no lo indica")
    void tipoSeDeduceDelArchivo() {
        EvidenciaDtos.EvidenciaResponse foto = evidenciaService.registrar(
                idMantenimiento, archivoPng("sin-tipo.png"),
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        assertThat(foto.tipoArchivo()).isEqualTo(EvidenciaMantenimientoUnh.FOTO);

        MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "informe.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes());
        EvidenciaDtos.EvidenciaResponse informe = evidenciaService.registrar(
                idMantenimiento, pdf,
                new EvidenciaDtos.RegistrarEvidenciaRequest(null, null));

        assertThat(informe.tipoArchivo()).isEqualTo(EvidenciaMantenimientoUnh.INFORME);
    }
}
