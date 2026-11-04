-- =====================================================================
-- V2: Sufijo UNH en las tablas + columna 'estado' para los activos
--
-- Motivo del renombrado: el servidor PostgreSQL es compartido con otros
-- proyectos (cobranza, sigai_db, admision_db). Los nombres genericos
-- ('activo', 'modulo', 'incidencia') se Prestaban a confusion. El sufijo
-- UNH deja explicito de que proyecto es cada tabla.
--
-- Las clases Java siguen la misma convencion (ActivoUnh, ModuloUnh, ...).
-- Las columnas NO cambian de nombre: solo las tablas.
--
-- Tambien se anade 'activo_unh.estado' (ACTIVO | INACTIVO): la regla de
-- negocio dice que los registros con datos relacionados no se borran, se
-- desactivan. 'estado_operativo' es otra cosa (estado TECNICO del equipo:
-- OPERATIVO | EN_MANTENIMIENTO | FUERA_DE_SERVICIO).
-- =====================================================================

-- --- Renombrado de tablas (PostgreSQL actualiza solo las FK y los indices) ---
ALTER TABLE responsable              RENAME TO responsable_unh;
ALTER TABLE modulo                  RENAME TO modulo_unh;
ALTER TABLE responsable_modulo      RENAME TO responsable_modulo_unh;
ALTER TABLE ubicacion               RENAME TO ubicacion_unh;
ALTER TABLE tipo_activo             RENAME TO tipo_activo_unh;
ALTER TABLE activo                  RENAME TO activo_unh;
ALTER TABLE incidencia              RENAME TO incidencia_unh;
ALTER TABLE mantenimiento           RENAME TO mantenimiento_unh;
ALTER TABLE evidencia_mantenimiento RENAME TO evidencia_mantenimiento_unh;

-- --- Estado de gestion del activo (activo / inactivo) ---
ALTER TABLE activo_unh
    ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO';

ALTER TABLE activo_unh
    ADD CONSTRAINT ck_activo_unh_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'));

CREATE INDEX idx_activo_unh_estado ON activo_unh (estado);
