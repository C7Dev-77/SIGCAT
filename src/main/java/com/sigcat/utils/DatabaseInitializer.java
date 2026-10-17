package com.sigcat.utils;

import com.sigcat.security.PasswordUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inicializador del esquema de la base de datos.
 * Crea las tablas si no existen al arrancar la aplicación.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 * Actualizado por: Cristian Morales (Arquitecto) — Migración a BCrypt (RNF-01)
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
            // Estrategia: verificar si existe por id_usuario; si no existe, insertar con password hasheado
            // Esto respeta datos reales existentes y evita corromper contraseñas ya hasheadas.
            seedUsuario(conn, 1, "1001", "Carlos Pérez (Funcionario)", "123", "FUNCIONARIO");
            seedUsuario(conn, 2, "2002", "Ana Gómez (Propietaria)", "123", "PROPIETARIO");

            System.out.println("[DB] Esquema y usuarios de prueba inicializados correctamente.");
        }
    }

    /**
     * Inserta o actualiza un usuario semilla.
     * - Si no existe (por id_usuario): inserta con password hasheado.
     * - Si existe pero tiene password en texto plano (no es hash BCrypt): actualiza a hash BCrypt.
     * - Si existe y ya tiene hash BCrypt: no toca nada (respeta datos reales).
     *
     * @param conn      Conexión a la base de datos.
     * @param id        ID del usuario.
     * @param documento Documento de identidad.
     * @param nombre    Nombre completo.
     * @param password  Contraseña en texto plano (se hasheará).
     * @param rol       Rol del usuario (PROPIETARIO o FUNCIONARIO).
     * @throws SQLException si ocurre un error de base de datos.
     */
    private static void seedUsuario(Connection conn, int id, String documento, String nombre,
                                    String password, String rol) throws SQLException {
        String checkSql = "SELECT password FROM usuarios WHERE id_usuario = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, id);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    String existingPassword = rs.getString("password");
                    // Si ya es un hash BCrypt válido (empieza con $2a$, $2b$ o $2y$), no tocar
                    if (existingPassword != null && existingPassword.matches("^\\$2[aby]\\$\\d{2}\\$.*")) {
                        return; // Ya tiene hash BCrypt, respetar
                    }
                    // Existe pero es texto plano → actualizar a hash BCrypt
                    String updateSql = "UPDATE usuarios SET password = ?, documento = ?, nombre = ?, rol = ? WHERE id_usuario = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setString(1, PasswordUtil.hashPassword(password));
                        updatePs.setString(2, documento);
                        updatePs.setString(3, nombre);
                        updatePs.setString(4, rol);
                        updatePs.setInt(5, id);
                        updatePs.executeUpdate();
                    }
                    return;
                }
            }
        }

        // No existe → insertar con hash BCrypt
        String insertSql = "INSERT INTO usuarios (id_usuario, documento, nombre, password, rol) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            insertPs.setInt(1, id);
            insertPs.setString(2, documento);
            insertPs.setString(3, nombre);
            insertPs.setString(4, PasswordUtil.hashPassword(password));
            insertPs.setString(5, rol);
            insertPs.executeUpdate();
        }
    }
}
