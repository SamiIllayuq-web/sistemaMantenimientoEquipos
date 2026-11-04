-- =====================================================================
-- V1: Esquema inicial del Sistema de Mantenimiento (Residencia UNH)
-- Todas las tablas de la V1 + datos iniciales (modulos y admin).
-- =====================================================================

-- ---------------------------------------------------------------------
-- responsable (usuarios)
-- ---------------------------------------------------------------------
CREATE TABLE responsable (
    dni                CHAR(8)      PRIMARY KEY,
    nombres            VARCHAR(100) NOT NULL,
    apellidos          VARCHAR(100) NOT NULL,
    correo             VARCHAR(150) NOT NULL UNIQUE,
    clave_hash         VARCHAR(100) NOT NULL,
    telefono           VARCHAR(20)  NULL,
    estado             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO'
                       CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    fecha_registro     TIMESTAMP    NOT NULL DEFAULT NOW(),
    intentos_fallidos  SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta    TIMESTAMP    NULL,
    ultimo_acceso      TIMESTAMP    NULL
);

-- ---------------------------------------------------------------------
-- modulo (catalogo de modulos / permisos)
-- ---------------------------------------------------------------------
CREATE TABLE modulo (
    id_modulo    SERIAL       PRIMARY KEY,
    codigo       VARCHAR(50)  NOT NULL UNIQUE,
    nombre       VARCHAR(100) NOT NULL,
    descripcion  VARCHAR(255) NULL
);

-- ---------------------------------------------------------------------
-- responsable_modulo (permisos: un usuario puede tener varios modulos)
-- ---------------------------------------------------------------------
CREATE TABLE responsable_modulo (
    id_responsable CHAR(8)    NOT NULL,
    id_modulo      INTEGER   NOT NULL,
    CONSTRAINT pk_responsable_modulo PRIMARY KEY (id_responsable, id_modulo),
    CONSTRAINT fk_rm_responsable FOREIGN KEY (id_responsable)
        REFERENCES responsable (dni) ON DELETE CASCADE,
    CONSTRAINT fk_rm_modulo FOREIGN KEY (id_modulo)
        REFERENCES modulo (id_modulo) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- ubicacion
-- ---------------------------------------------------------------------
CREATE TABLE ubicacion (
    id_ubicacion   SERIAL        PRIMARY KEY,
    nombre         VARCHAR(100)  NOT NULL UNIQUE,
    tipo_ubicacion VARCHAR(50)   NOT NULL,
    pabellon      VARCHAR(50)   NULL,
    piso           VARCHAR(20)   NULL,
    descripcion    VARCHAR(255)  NULL
);

-- ---------------------------------------------------------------------
-- tipo_activo
-- ---------------------------------------------------------------------
CREATE TABLE tipo_activo (
    id_tipo_activo          SERIAL        PRIMARY KEY,
    nombre                  VARCHAR(100)  NOT NULL UNIQUE,
    categoria               VARCHAR(20)   NOT NULL
                            CHECK (categoria IN ('EQUIPO', 'INFRAESTRUCTURA')),
    descripcion             VARCHAR(255)  NULL,
    requiere_mantenimiento  BOOLEAN       NOT NULL DEFAULT TRUE,
    vida_util_anios         INTEGER       NULL
);

-- ---------------------------------------------------------------------
-- activo
-- ---------------------------------------------------------------------
CREATE TABLE activo (
    id_activo              SERIAL         PRIMARY KEY,
    id_tipo_activo         INTEGER        NOT NULL,
    id_ubicacion           INTEGER        NOT NULL,
    codigo_patrimonial     VARCHAR(50)    NOT NULL UNIQUE,
    nombre                 VARCHAR(150)   NOT NULL,
    marca                  VARCHAR(100)   NULL,
    modelo                 VARCHAR(100)   NULL,
    numero_serie           VARCHAR(100)   NULL,
    fecha_adquisicion      DATE           NULL,
    fecha_puesta_servicio  DATE           NULL,
    estado_operativo       VARCHAR(25)    NOT NULL DEFAULT 'OPERATIVO'
                           CHECK (estado_operativo IN
                               ('OPERATIVO', 'EN_MANTENIMIENTO', 'FUERA_DE_SERVICIO')),
    valor_adquisicion      DECIMAL(12, 2) NULL,
    observaciones          VARCHAR(500)   NULL,
    fecha_registro         TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_activo_tipo_activo FOREIGN KEY (id_tipo_activo)
        REFERENCES tipo_activo (id_tipo_activo),
    CONSTRAINT fk_activo_ubicacion FOREIGN KEY (id_ubicacion)
        REFERENCES ubicacion (id_ubicacion),
    CONSTRAINT ck_activo_valor CHECK (valor_adquisicion IS NULL OR valor_adquisicion >= 0)
);

-- ---------------------------------------------------------------------
-- incidencia
-- ---------------------------------------------------------------------
CREATE TABLE incidencia (
    id_incidencia               SERIAL        PRIMARY KEY,
    id_activo                   INTEGER       NOT NULL,
    fecha_deteccion_incidencia  TIMESTAMP     NOT NULL,
    descripcion                 VARCHAR(1000) NOT NULL,
    prioridad_incidencia        VARCHAR(10)   NOT NULL
                               CHECK (prioridad_incidencia IN ('ALTA', 'MEDIA', 'BAJA')),
    estado_incidencia           VARCHAR(15)   NOT NULL DEFAULT 'ABIERTA'
                               CHECK (estado_incidencia IN
                                   ('ABIERTA', 'EN_ATENCION', 'CERRADA')),
    fecha_cierre_incidencia     TIMESTAMP     NULL,
    observaciones_incidencia    VARCHAR(500)  NULL,
    CONSTRAINT fk_incidencia_activo FOREIGN KEY (id_activo)
        REFERENCES activo (id_activo)
);

-- ---------------------------------------------------------------------
-- mantenimiento
-- ---------------------------------------------------------------------
CREATE TABLE mantenimiento (
    id_mantenimiento         SERIAL         PRIMARY KEY,
    id_activo                INTEGER        NOT NULL,
    id_responsable           CHAR(8)        NOT NULL,
    id_incidencia            INTEGER        NULL,
    tipo_mantenimiento       VARCHAR(15)    NOT NULL DEFAULT 'CORRECTIVO'
                            CHECK (tipo_mantenimiento IN ('PREVENTIVO', 'CORRECTIVO')),
    fecha_inicio             TIMESTAMP      NOT NULL,
    fecha_fin                TIMESTAMP      NULL,
    diagnostico              VARCHAR(1000)  NULL,
    actividad_realizada      VARCHAR(1000)  NULL,
    resultado                VARCHAR(500)   NULL,
    estado_mantenimiento     VARCHAR(15)    NOT NULL DEFAULT 'EN_PROCESO'
                            CHECK (estado_mantenimiento IN
                                ('EN_PROCESO', 'FINALIZADO', 'CANCELADO')),
    horas_inactividad        NUMERIC(10, 2) NULL,
    costo_mano_obra          DECIMAL(10, 2) NOT NULL DEFAULT 0,
    proxima_fecha_mantenimiento DATE         NULL,
    observaciones_mantenimiento VARCHAR(500) NULL,
    CONSTRAINT fk_mantenimiento_activo FOREIGN KEY (id_activo)
        REFERENCES activo (id_activo),
    CONSTRAINT fk_mantenimiento_responsable FOREIGN KEY (id_responsable)
        REFERENCES responsable (dni),
    CONSTRAINT fk_mantenimiento_incidencia FOREIGN KEY (id_incidencia)
        REFERENCES incidencia (id_incidencia),
    CONSTRAINT ck_mantenimiento_costo CHECK (costo_mano_obra >= 0),
    CONSTRAINT ck_mantenimiento_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    -- PREVENTIVO => sin incidencia; CORRECTIVO => siempre con incidencia
    CONSTRAINT ck_mantenimiento_tipo_incidencia CHECK (
        (tipo_mantenimiento = 'PREVENTIVO' AND id_incidencia IS NULL) OR
        (tipo_mantenimiento = 'CORRECTIVO' AND id_incidencia IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------
-- evidencia_mantenimiento
-- ---------------------------------------------------------------------
CREATE TABLE evidencia_mantenimiento (
    id_evidencia             SERIAL        PRIMARY KEY,
    id_mantenimiento         INTEGER       NOT NULL,
    tipo_archivo             VARCHAR(15)   NOT NULL
                            CHECK (tipo_archivo IN ('FOTO', 'INFORME', 'OTRO')),
    nombre_archivo           VARCHAR(255)  NOT NULL,
    nombre_original          VARCHAR(255)  NOT NULL,
    descripcion_evidencia    VARCHAR(500)  NULL,
    fecha_registro_evidencia TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_evidencia_mantenimiento FOREIGN KEY (id_mantenimiento)
        REFERENCES mantenimiento (id_mantenimiento) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
CREATE INDEX idx_activo_ubicacion      ON activo (id_ubicacion);
CREATE INDEX idx_activo_tipo_activo    ON activo (id_tipo_activo);
CREATE INDEX idx_activo_estado         ON activo (estado_operativo);
CREATE INDEX idx_incidencia_activo     ON incidencia (id_activo);
CREATE INDEX idx_incidencia_estado     ON incidencia (estado_incidencia);
CREATE INDEX idx_mantenimiento_activo  ON mantenimiento (id_activo);
CREATE INDEX idx_mantenimiento_estado  ON mantenimiento (estado_mantenimiento);
CREATE INDEX idx_evidencia_mantenimiento ON evidencia_mantenimiento (id_mantenimiento);

-- ---------------------------------------------------------------------
-- Datos iniciales: los 7 modulos de la V1
-- ---------------------------------------------------------------------
INSERT INTO modulo (codigo, nombre, descripcion) VALUES
    ('USUARIOS',       'Usuarios y permisos',  'Gestion de usuarios y asignacion de modulos'),
    ('UBICACIONES',    'Ubicaciones',          'Catalogo de ubicaciones de la residencia'),
    ('TIPOS_ACTIVO',   'Tipos de activo',      'Catalogo de tipos de activo'),
    ('ACTIVOS',        'Activos',              'Registro y gestion de activos'),
    ('INCIDENCIAS',    'Incidencias',          'Reporte y seguimiento de fallas'),
    ('MANTENIMIENTOS', 'Mantenimiento',        'Mantenimiento correctivo de activos'),
    ('EVIDENCIAS',     'Evidencias',           'Fotos e informes de los mantenimientos');

-- ---------------------------------------------------------------------
-- Datos iniciales: usuario administrador con TODOS los modulos
-- Contraseña inicial: Admin1234  (debe cambiarse al primer ingreso)
-- clave_hash = bcrypt de "Admin1234" con strength 10
-- ---------------------------------------------------------------------
INSERT INTO responsable (dni, nombres, apellidos, correo, clave_hash, telefono, estado)
VALUES (
    '00000000', 'Administrador', 'Sistema', 'admin@unh.edu.pe',
    '$2b$10$ayKary.OEyqKteRrbAC5puPo7IwwH6TO83.9AMSG17nCyu1WOLToG',
    NULL, 'ACTIVO'
);

INSERT INTO responsable_modulo (id_responsable, id_modulo)
SELECT '00000000', id_modulo FROM modulo;
