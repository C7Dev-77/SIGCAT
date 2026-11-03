-- ============================================================
-- SIGCAT v2.0 — Esquema PostgreSQL para Supabase
-- Municipio de Sincelejo, Sucre | Código DANE: 70001
-- ============================================================

-- 1. Extensiones requeridas
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 2. TABLA: usuarios (Compatible con roles y perfil)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.usuarios (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    documento        TEXT NOT NULL UNIQUE,
    nombre           TEXT NOT NULL,
    password_hash    TEXT NOT NULL,         -- Hash BCrypt
    rol              TEXT NOT NULL CHECK (rol IN ('PROPIETARIO', 'FUNCIONARIO', 'ADMIN')),
    avatar_url       TEXT,                  -- URL en Supabase Storage
    telefono         TEXT,
    email            TEXT UNIQUE,
    activo           BOOLEAN DEFAULT TRUE,
    creado_en        TIMESTAMPTZ DEFAULT NOW(),
    actualizado_en   TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 3. TABLA: predios (Catastro Principal de Sincelejo)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.predios (
    id                  SERIAL PRIMARY KEY,
    codigo_catastral    TEXT UNIQUE,
    nombre              TEXT NOT NULL,
    direccion           TEXT,
    uso_suelo           TEXT,
    estado              TEXT NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'PUBLICO')),
    tipo                TEXT DEFAULT 'PRIVADO'
                        CHECK (tipo IN ('PRIVADO', 'PUBLICO', 'INSTITUCIONAL', 'COMERCIAL', 'VIAL')),
    area_calculada      DOUBLE PRECISION,
    perimetro           DOUBLE PRECISION,
    observaciones       TEXT,
    propietario_id      UUID REFERENCES public.usuarios(id) ON DELETE SET NULL,
    nombre_propietario  TEXT,
    funcionario_id      UUID REFERENCES public.usuarios(id) ON DELETE SET NULL,
    fecha_radicacion    TIMESTAMPTZ DEFAULT NOW(),
    fecha_revision      TIMESTAMPTZ,
    sincronizado        BOOLEAN DEFAULT TRUE,
    origen_local_id     INTEGER,
    creado_en           TIMESTAMPTZ DEFAULT NOW(),
    actualizado_en      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_predios_estado ON public.predios(estado);
CREATE INDEX IF NOT EXISTS idx_predios_codigo ON public.predios(codigo_catastral);
CREATE INDEX IF NOT EXISTS idx_predios_propietario ON public.predios(propietario_id);

-- ============================================================
-- 4. TABLA: vertices (Linderos georreferenciados WGS84)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.vertices (
    id          SERIAL PRIMARY KEY,
    id_predio   INTEGER NOT NULL REFERENCES public.predios(id) ON DELETE CASCADE,
    orden       INTEGER NOT NULL,
    longitud    DOUBLE PRECISION NOT NULL,
    latitud     DOUBLE PRECISION NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vertices_predio ON public.vertices(id_predio);

-- ============================================================
-- 5. TABLA: documentos (Gestión Documental Legal y Recibos)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.documentos (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    predio_id       INTEGER NOT NULL REFERENCES public.predios(id) ON DELETE CASCADE,
    propietario_id  UUID REFERENCES public.usuarios(id) ON DELETE SET NULL,
    tipo_documento  TEXT NOT NULL
                    CHECK (tipo_documento IN (
                        'ESCRITURA_PUBLICA',
                        'CERTIFICADO_CATASTRO',
                        'RECIBO_VALIDACION',
                        'FOLIO_MATRICULA',
                        'PODER_NOTARIAL',
                        'OTRO'
                    )),
    nombre_archivo  TEXT NOT NULL,
    storage_path    TEXT NOT NULL,
    storage_url     TEXT,
    estado          TEXT DEFAULT 'PENDIENTE_REVISION'
                    CHECK (estado IN ('PENDIENTE_REVISION', 'APROBADO', 'RECHAZADO')),
    descripcion     TEXT,
    subido_en       TIMESTAMPTZ DEFAULT NOW(),
    revisado_en     TIMESTAMPTZ,
    revisado_por    UUID REFERENCES public.usuarios(id)
);

CREATE INDEX IF NOT EXISTS idx_documentos_predio ON public.documentos(predio_id);

-- ============================================================
-- 6. TABLA: predios_publicos_referencia (Capa Base Sincelejo)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.predios_publicos_referencia (
    id              SERIAL PRIMARY KEY,
    nombre_oficial  TEXT NOT NULL UNIQUE,
    categoria       TEXT NOT NULL CHECK (categoria IN (
                        'COMERCIAL', 'INSTITUCIONAL', 'ESPACIO_PUBLICO',
                        'EDUCATIVO', 'SALUD', 'RELIGIOSO', 'VIAL'
                    )),
    entidad_duena   TEXT NOT NULL,
    direccion       TEXT,
    descripcion     TEXT,
    activo          BOOLEAN DEFAULT TRUE
);

-- ============================================================
-- 7. TABLA: notificaciones (Alertas para Propietarios y Admin)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.notificaciones (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id      UUID NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,
    tipo            TEXT NOT NULL CHECK (tipo IN (
                        'PREDIO_APROBADO', 'PREDIO_RECHAZADO', 'CONFLICTO_DETECTADO',
                        'DOCUMENTO_REQUERIDO', 'SISTEMA'
                    )),
    titulo          TEXT NOT NULL,
    mensaje         TEXT NOT NULL,
    leida           BOOLEAN DEFAULT FALSE,
    predio_id       INTEGER REFERENCES public.predios(id) ON DELETE SET NULL,
    creada_en       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario ON public.notificaciones(usuario_id, leida);

-- ============================================================
-- 8. TRIGGERS: Actualización de timestamps
-- ============================================================
CREATE OR REPLACE FUNCTION actualizar_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_predios_updated ON public.predios;
CREATE TRIGGER trg_predios_updated
    BEFORE UPDATE ON public.predios
    FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();

DROP TRIGGER IF EXISTS trg_usuarios_updated ON public.usuarios;
CREATE TRIGGER trg_usuarios_updated
    BEFORE UPDATE ON public.usuarios
    FOR EACH ROW EXECUTE FUNCTION actualizar_timestamp();
