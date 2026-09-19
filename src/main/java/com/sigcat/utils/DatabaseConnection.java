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

    // Ruta portable: guarda sigcat.db en el directorio de trabajo del proyecto
    private static final String DB_URL = "jdbc:sqlite:sigcat.db";

    private static DatabaseConnection instance;
    private Connection connection;

    private DatabaseConnection() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        // Activar integridad referencial en SQLite (desactivada por defecto)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        System.out.println("[DB] Conexión establecida con sigcat.db");
    }

    /**
     * Obtiene la instancia única de la conexión (Singleton).
     */
    public static DatabaseConnection getInstance() throws SQLException {
        if (instance == null || instance.getConnection().isClosed()) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Retorna el objeto Connection de JDBC.
     */
    public Connection getConnection() {
        return connection;
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
