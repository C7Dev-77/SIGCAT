package com.sigcat.supabase;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Ejecutor automatizado de migraciones DDL y semillas hacia Supabase PostgreSQL.
 * Lee scripts SQL desde src/main/resources/database/ y los ejecuta con JDBC.
 */
public class SupabaseMigrationRunner {

    public static void main(String[] args) {
        ejecutarMigracionCompleta();
    }

    public static boolean ejecutarMigracionCompleta() {
        System.out.println("[MIGRACION] Iniciando migracion hacia Supabase...");

        // Aplicar parches incrementales DDL
        aplicarParchesIncrementales();

        boolean schemaOk = ejecutarScript("/database/supabase_schema.sql", "Esquema DDL de Tablas");
        if (!schemaOk) {
            System.err.println("[MIGRACION] Error aplicando el esquema DDL.");
            return false;
        }

        boolean seedsOk = ejecutarScript("/database/supabase_seeds.sql", "Datos Semilla de Predios Publicos de Sincelejo");
        if (!seedsOk) {
            System.err.println("[MIGRACION] Error aplicando semillas.");
            return false;
        }

        System.out.println("[MIGRACION] >>> Migracion completada con exito en Supabase. <<<");
        return true;
    }

    public static void aplicarParchesIncrementales() {
        if (!DatabaseConnectionSupabase.isOnline()) return;
        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE public.documentos ADD COLUMN IF NOT EXISTS hash_sha256 TEXT;");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS public.audit_log (
                    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                    entidad         TEXT NOT NULL,
                    entidad_id      TEXT NOT NULL,
                    accion          TEXT NOT NULL,
                    estado_anterior TEXT,
                    estado_nuevo    TEXT,
                    usuario_id      TEXT,
                    usuario_nombre  TEXT,
                    detalles        TEXT,
                    fecha           TIMESTAMPTZ DEFAULT NOW()
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_entidad ON public.audit_log(entidad, entidad_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_fecha ON public.audit_log(fecha DESC);");
            System.out.println("[MIGRACION] ✔ Parches incrementales aplicados en Supabase.");
        } catch (Exception e) {
            System.err.println("[MIGRACION] Aviso en parches incrementales: " + e.getMessage());
        }
    }

    private static boolean ejecutarScript(String rutaRecurso, String descripcion) {
        System.out.println("[MIGRACION] Ejecutando: " + descripcion + " (" + rutaRecurso + ")...");
        try (InputStream is = SupabaseMigrationRunner.class.getResourceAsStream(rutaRecurso)) {
            if (is == null) {
                System.err.println("[MIGRACION] No se encontro el archivo de script: " + rutaRecurso);
                return false;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    // Ignorar comentarios simples de linea completa
                    String trim = linea.trim();
                    if (!trim.startsWith("--") && !trim.isEmpty()) {
                        sb.append(linea).append("\n");
                    }
                }
            }

            String sqlCompleto = sb.toString();
            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sqlCompleto);
                System.out.println("[MIGRACION] ✔ " + descripcion + " ejecutado exitosamente.");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[MIGRACION] ❌ Error en " + descripcion + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

