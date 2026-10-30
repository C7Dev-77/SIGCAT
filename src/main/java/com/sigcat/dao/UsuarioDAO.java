package com.sigcat.dao;

import com.sigcat.models.Usuario;
import com.sigcat.security.PasswordUtil;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.Optional;

/**
 * DAO para la gestión de Usuarios (Autenticación, verificación de unicidad de documento
 * y registro autónomo de propietarios con hash BCrypt factor 12).
 */
public class UsuarioDAO {

    /**
     * Autentica un usuario validando su documento/username y su contraseña contra el hash almacenado.
     */
    public Usuario autenticar(String documento, String passwordPlana) {
        if (documento == null || passwordPlana == null) {
            return null;
        }

        String sql = "SELECT * FROM usuarios WHERE documento = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, documento.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = null;
                    try {
                        storedHash = rs.getString("password_hash");
                    } catch (SQLException ignored) {}
                    if (storedHash == null || storedHash.isEmpty()) {
                        storedHash = rs.getString("password");
                    }

                    if (PasswordUtil.checkPassword(passwordPlana, storedHash)) {
                        return mapearUsuario(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error en autenticación de usuario: " + e.getMessage());
        }
        return null;
    }

    /**
     * Verifica si ya existe un usuario registrado con el mismo documento de identidad.
     */
    public boolean existeDocumento(String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT COUNT(1) FROM usuarios WHERE documento = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, documento.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al verificar existencia de documento: " + e.getMessage());
        }
        return false;
    }

    /**
     * Registra un nuevo propietario hasheando su contraseña con factor de costo 12.
     *
     * @param documento     Cédula o NIT único.
     * @param nombre        Nombre completo del propietario.
     * @param passwordPlana Contraseña en texto plano que será hasheada antes de persistir.
     * @return El ID autogenerado del nuevo usuario o -1 si falla.
     */
    public int registrarPropietario(String documento, String nombre, String passwordPlana) {
        if (existeDocumento(documento)) {
            return -1;
        }

        String hashSeguro = PasswordUtil.hashPassword(passwordPlana);
        String sqlWithBoth = "INSERT INTO usuarios (documento, nombre, password_hash, password, rol) VALUES (?, ?, ?, ?, 'PROPIETARIO')";
        String sqlLegacy = "INSERT INTO usuarios (documento, nombre, password, rol) VALUES (?, ?, ?, 'PROPIETARIO')";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlWithBoth, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, documento.trim());
                stmt.setString(2, nombre.trim());
                stmt.setString(3, hashSeguro);
                stmt.setString(4, hashSeguro);
                int filas = stmt.executeUpdate();
                if (filas > 0) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            } catch (SQLException e) {
                // Fallback si la columna password_hash no existe
                try (PreparedStatement stmt = conn.prepareStatement(sqlLegacy, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, documento.trim());
                    stmt.setString(2, nombre.trim());
                    stmt.setString(3, hashSeguro);
                    int filas = stmt.executeUpdate();
                    if (filas > 0) {
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                return rs.getInt(1);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al registrar nuevo propietario: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Sobrecarga para insertar un objeto Usuario directamente.
     */
    public boolean crearUsuario(Usuario usuario, String passwordPlana) {
        int id = registrarPropietario(usuario.getDocumento(), usuario.getNombre(), passwordPlana);
        if (id > 0) {
            usuario.setId(id);
            return true;
        }
        return false;
    }

    /**
     * Inserta un usuario con contraseña ya incluida en el modelo.
     */
    public int insertar(Usuario usuario) throws SQLException {
        int id = registrarPropietario(usuario.getDocumento(), usuario.getNombre(), usuario.getPassword());
        if (id > 0) {
            usuario.setId(id);
            return id;
        }
        throw new SQLException("No se pudo registrar el usuario o documento duplicado.");
    }

    /**
     * Busca un usuario por su identificador único.
     */
    public Optional<Usuario> buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE id_usuario = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Usuario mapearUsuario(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        try {
            u.setId(rs.getInt("id"));
        } catch (SQLException e) {
            u.setId(rs.getInt("id_usuario"));
        }
        u.setDocumento(rs.getString("documento"));
        u.setNombre(rs.getString("nombre"));
        String storedHash = null;
        try {
            storedHash = rs.getString("password_hash");
        } catch (SQLException ignored) {}
        if (storedHash == null || storedHash.isEmpty()) {
            storedHash = rs.getString("password");
        }
        u.setPasswordHash(storedHash);
        u.setRol(rs.getString("rol"));
        return u;
    }
}
