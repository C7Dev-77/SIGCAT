package com.sigcat.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestiona la conexión a la base de datos SQLite embebida.
 * Crea el archivo sigcat.db en el directorio del usuario si no existe.
 * Implementa el patrón Singleton para reutilizar la conexión.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class DatabaseConnection {

    private static final String DEFAULT_DB_URL = "jdbc:sqlite:sigcat.db";

    private static DatabaseConnection instance;
    private Connection connection;

    /**
     * Resuelve la URL de conexión admitiendo parámetros de red para demos multi-PC.
     * Prioridad:
     *   1. Propiedad del sistema JVM: -Dsigcat.db="..."
     *   2. Variable de entorno del sistema operativo: SIGCAT_DB
     *   3. Archivo local por defecto: sigcat.db
     */
    public static String resolverDbUrl() {
        String sysProp = System.getProperty("sigcat.db");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            String p = sysProp.trim();
            return p.startsWith("jdbc:sqlite:") ? p : "jdbc:sqlite:" + p;
        }

        String envVar = System.getenv("SIGCAT_DB");
        if (envVar != null && !envVar.trim().isEmpty()) {
            String p = envVar.trim();
            return p.startsWith("jdbc:sqlite:") ? p : "jdbc:sqlite:" + p;
        }

        return DEFAULT_DB_URL;
    }

    private DatabaseConnection() throws SQLException {
        String dbUrl = resolverDbUrl();
        connection = DriverManager.getConnection(dbUrl);

        // Configuración de concurrencia y fiabilidad para SQLite local y en red
        try (Statement stmt = connection.createStatement()) {
            // Activar integridad referencial (llaves foráneas)
            stmt.execute("PRAGMA foreign_keys = ON;");
            // Activar modo WAL (Write-Ahead Logging) para permitir lecturas y escrituras simultáneas
            try {
                stmt.execute("PRAGMA journal_mode = WAL;");
            } catch (SQLException ignored) {}
            // Tiempo de espera de hasta 5 segundos si otro PC está escribiendo
            try {
                stmt.execute("PRAGMA busy_timeout = 5000;");
            } catch (SQLException ignored) {}
        }
        System.out.println("[DB] Conexión establecida con: " + dbUrl);
    }

    /**
     * Obtiene la instancia única de la conexión (Singleton).
     */
    public static DatabaseConnection getInstance() throws SQLException {
        if (instance == null || instance.connection == null || instance.connection.isClosed()) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Retorna el objeto Connection de JDBC.
     */
    public static Connection getConnection() throws SQLException {
        return getInstance().connection;
    }

    /**
     * Cierra y reinicia la conexión activa (útil para alternar a base de datos de test aislada).
     */
    public static synchronized void reiniciarConexion() throws SQLException {
        if (instance != null && instance.connection != null && !instance.connection.isClosed()) {
            instance.connection.close();
            System.out.println("[DB] Conexión cerrada y reiniciada.");
        }
        instance = null;
    }

    /**
     * Cierra la conexión a la base de datos.
     */
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            System.out.println("[DB] Conexión cerrada.");
        }
    }
}
