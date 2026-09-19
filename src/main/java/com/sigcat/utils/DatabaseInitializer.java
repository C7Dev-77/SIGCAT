package com.sigcat.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inicializador del esquema de la base de datos.
 * Crea las tablas si no existen al arrancar la aplicación.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class DatabaseInitializer {

    /**
     * Ejecuta el script DDL para crear las tablas del sistema.
     * Es seguro llamarlo múltiples veces (usa CREATE TABLE IF NOT EXISTS).
     */
    public static void initialize() throws SQLException {
        Connection conn = DatabaseConnection.getInstance().getConnection();

        try (Statement stmt = conn.createStatement()) {

            // ─── Tabla: usuarios ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id_usuario  INTEGER PRIMARY KEY AUTOINCREMENT,
                    documento   TEXT    NOT NULL UNIQUE,
                    nombre      TEXT    NOT NULL,
                    password    TEXT    NOT NULL,
                    rol         TEXT    NOT NULL CHECK(rol IN ('PROPIETARIO', 'FUNCIONARIO'))
                );
            """);

            // ─── Tabla: predios ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS predios (
                    id_predio       INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_propietario  INTEGER NOT NULL,
                    id_funcionario  INTEGER,
                    area_calculada  REAL    DEFAULT 0.0,
                    estado          TEXT    NOT NULL DEFAULT 'PENDIENTE'
                                    CHECK(estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
                    fecha_registro  TEXT    NOT NULL,
                    FOREIGN KEY (id_propietario) REFERENCES usuarios(id_usuario),
                    FOREIGN KEY (id_funcionario) REFERENCES usuarios(id_usuario)
                );
            """);

            // ─── Tabla: vertices ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS vertices (
                    id_vertice  INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_predio   INTEGER NOT NULL,
                    orden       INTEGER NOT NULL,
                    coord_x     REAL    NOT NULL,
                    coord_y     REAL    NOT NULL,
                    FOREIGN KEY (id_predio) REFERENCES predios(id_predio)
                        ON DELETE CASCADE
                );
            """);

            // ─── Usuarios de prueba (Semilla inicial) ─────────────
            stmt.execute("""
                INSERT OR IGNORE INTO usuarios (id_usuario, documento, nombre, password, rol) VALUES
                (1, '1001', 'Carlos Pérez (Funcionario)', '123', 'FUNCIONARIO'),
                (2, '2002', 'Ana Gómez (Propietaria)', '123', 'PROPIETARIO');
            """);

            System.out.println("[DB] Esquema y usuarios de prueba inicializados correctamente.");
        }
    }
}
