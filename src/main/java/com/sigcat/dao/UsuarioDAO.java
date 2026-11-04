package com.sigcat.dao;

import com.sigcat.models.Usuario;
import com.sigcat.security.PasswordUtil;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO para la gestión de Usuarios (Autenticación, verificación de unicidad de documento,
 * registro autónomo de propietarios con hash BCrypt factor 12 y administración de perfil).
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
                    } else {
                        // Usuario existe pero contraseña no coincide
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error en autenticación de usuario: " + e.getMessage());
        }

        // Si NO se encuentra en SQLite local (ej. usuario registrado en otro PC), validar en Supabase Cloud
        if (com.sigcat.supabase.DatabaseConnectionSupabase.isOnline()) {
            Usuario remoto = autenticarEnSupabase(documento.trim(), passwordPlana);
            if (remoto != null) {
                return remoto;
            }
        }

        return null;
    }

    private Usuario autenticarEnSupabase(String documento, String passwordPlana) {
        String sql = "SELECT * FROM public.usuarios WHERE documento = ? AND activo = TRUE";
        try (Connection conn = com.sigcat.supabase.DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documento);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (PasswordUtil.checkPassword(passwordPlana, hash)) {
                        Usuario u = new Usuario();
                        u.setDocumento(rs.getString("documento"));
                        u.setNombre(rs.getString("nombre"));
                        u.setRol(rs.getString("rol"));
                        u.setEmail(rs.getString("email"));
                        u.setTelefono(rs.getString("telefono"));
                        u.setAvatarUrl(rs.getString("avatar_url"));

                        // Replicar en SQLite local para permitir acceso offline futuro
                        int localId = registrarUsuarioLocal(u, hash);
                        u.setId(localId);
                        return u;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UsuarioDAO] Error autenticando contra Supabase: " + e.getMessage());
        }
        return null;
    }

    private int registrarUsuarioLocal(Usuario u, String hash) {
        String sql = "INSERT OR IGNORE INTO usuarios (documento, nombre, password_hash, password, rol, email, telefono, avatar_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, u.getDocumento());
            stmt.setString(2, u.getNombre());
            stmt.setString(3, hash);
            stmt.setString(4, hash);
            stmt.setString(5, u.getRol());
            stmt.setString(6, u.getEmail());
            stmt.setString(7, u.getTelefono());
            stmt.setString(8, u.getAvatarUrl());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[UsuarioDAO] Error registrando usuario local desde nube: " + e.getMessage());
        }
        return buscarPorDocumento(u.getDocumento()).map(Usuario::getId).orElse(1);
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

        int idGenerado = -1;
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
                            idGenerado = rs.getInt(1);
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
                                idGenerado = rs.getInt(1);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al registrar nuevo propietario: " + e.getMessage());
        }

        if (idGenerado > 0 && com.sigcat.supabase.DatabaseConnectionSupabase.isOnline()) {
            final int idFinal = idGenerado;
            new Thread(() -> {
                String sqlCloud = "INSERT INTO public.usuarios (documento, nombre, password_hash, rol) VALUES (?, ?, ?, 'PROPIETARIO') ON CONFLICT (documento) DO NOTHING";
                try (Connection connCloud = com.sigcat.supabase.DatabaseConnectionSupabase.getConnection();
                     PreparedStatement stmtCloud = connCloud.prepareStatement(sqlCloud)) {
                    stmtCloud.setString(1, documento.trim());
                    stmtCloud.setString(2, nombre.trim());
                    stmtCloud.setString(3, hashSeguro);
                    stmtCloud.executeUpdate();
                } catch (SQLException ex) {
                    System.err.println("[UsuarioDAO] Aviso al replicar registro a Supabase: " + ex.getMessage());
                }
            }).start();
        }

        return idGenerado;
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

    public Optional<Usuario> buscarPorDocumento(String documento) {
        if (documento == null || documento.trim().isEmpty()) return Optional.empty();
        String sql = "SELECT * FROM usuarios WHERE documento = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, documento.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar usuario por documento: " + e.getMessage());
        }
        return Optional.empty();
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

    /**
     * Actualiza el nombre y/o contraseña de un usuario (Perfil de Propietario).
     */
    public boolean actualizarPerfil(int id, String nuevoNombre, String nuevaPasswordPlana) {
        return actualizarPerfilCompleto(id, nuevoNombre, null, null, null, nuevaPasswordPlana);
    }

    /**
     * Actualiza perfil extendido (nombre, correo, teléfono, avatar y contraseña opcional).
     */
    public boolean actualizarPerfilCompleto(int id, String nombre, String email, String telefono, String avatarUrl, String nuevaPasswordPlana) {
        StringBuilder sql = new StringBuilder("UPDATE usuarios SET nombre = ?");
        if (email != null) sql.append(", email = ?");
        if (telefono != null) sql.append(", telefono = ?");
        if (avatarUrl != null) sql.append(", avatar_url = ?");

        String nuevoHash = null;
        if (nuevaPasswordPlana != null && !nuevaPasswordPlana.trim().isEmpty()) {
            nuevoHash = PasswordUtil.hashPassword(nuevaPasswordPlana.trim());
            sql.append(", password = ?, password_hash = ?");
        }
        sql.append(" WHERE id_usuario = ?");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, nombre != null ? nombre.trim() : "");
            if (email != null) stmt.setString(paramIndex++, email.trim());
            if (telefono != null) stmt.setString(paramIndex++, telefono.trim());
            if (avatarUrl != null) stmt.setString(paramIndex++, avatarUrl.trim());

            if (nuevoHash != null) {
                stmt.setString(paramIndex++, nuevoHash);
                stmt.setString(paramIndex++, nuevoHash);
            }
            stmt.setInt(paramIndex, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al actualizar perfil completo de usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retorna todos los usuarios registrados en el sistema (para gestión administrativa).
     */
    public List<Usuario> obtenerTodos() {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT * FROM usuarios ORDER BY id_usuario ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearUsuario(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UsuarioDAO] Error al listar usuarios: " + e.getMessage());
        }
        return lista;
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
        try {
            u.setEmail(rs.getString("email"));
        } catch (SQLException ignored) {}
        try {
            u.setTelefono(rs.getString("telefono"));
        } catch (SQLException ignored) {}
        try {
            u.setAvatarUrl(rs.getString("avatar_url"));
        } catch (SQLException ignored) {}
        return u;
    }
}
