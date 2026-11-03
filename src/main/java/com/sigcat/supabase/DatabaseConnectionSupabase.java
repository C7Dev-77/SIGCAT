package com.sigcat.supabase;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexión JDBC directa a Supabase (PostgreSQL).
 * Las credenciales se leen de forma segura desde el archivo .env
 * usando la librería dotenv-java. Nunca se hardcodean en el código.
 *
 * Uso: DatabaseConnectionSupabase.getConnection()
 */
public class DatabaseConnectionSupabase {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()   // No explota si .env no existe (modo producción)
            .load();

    private static Connection connection;
    private static boolean parchesAplicados = false;

    private DatabaseConnectionSupabase() {}

    /**
     * Retorna la conexión activa a Supabase PostgreSQL.
     * Crea una nueva si está cerrada o es nula.
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url  = dotenv.get("SUPABASE_JDBC_URL",  System.getenv("SUPABASE_JDBC_URL"));
            String user = dotenv.get("SUPABASE_DB_USER",   System.getenv("SUPABASE_DB_USER"));
            String pass = dotenv.get("SUPABASE_DB_PASSWORD", System.getenv("SUPABASE_DB_PASSWORD"));

            if (url == null || user == null || pass == null) {
                throw new SQLException("[SIGCAT] Las variables de entorno de Supabase no están configuradas. " +
                        "Verifica el archivo .env en la raíz del proyecto.");
            }

            connection = DriverManager.getConnection(url, user, pass);
            System.out.println("[SUPABASE] Conexión establecida exitosamente con PostgreSQL en la nube.");

            if (!parchesAplicados) {
                parchesAplicados = true;
                aplicarParchesSchema(connection);
            }
        }
        return connection;
    }

    private static void aplicarParchesSchema(Connection conn) {
        try (var stmt = conn.createStatement()) {
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
        } catch (SQLException e) {
            System.err.println("[SUPABASE] Aviso al verificar esquema: " + e.getMessage());
        }
    }

    /**
     * Verifica si hay conexión activa con Supabase (y por tanto, con internet).
     * No lanza excepciones — retorna false si falla.
     */
    public static boolean isOnline() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed() && conn.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Cierra la conexión abierta. Llamar al cerrar la aplicación.
     */
    public static void cerrar() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[SUPABASE] Conexión cerrada correctamente.");
            }
        } catch (SQLException e) {
            System.err.println("[SUPABASE] Error al cerrar conexión: " + e.getMessage());
        }
    }
}

