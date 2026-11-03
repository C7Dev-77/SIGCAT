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
        }
        return connection;
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

