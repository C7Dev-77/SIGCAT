package com.sigcat.dao;

import com.sigcat.models.Usuario;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.Optional;

/**
 * Data Access Object para la entidad Usuario.
 * Implementa operaciones CRUD básicas y autenticación.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class UsuarioDAO {

    /**
     * Autentica un usuario por documento y contraseña.
     *
     * @param documento Número de documento de identidad.
     * @param password  Contraseña del usuario.
     * @return Optional con el Usuario si las credenciales son válidas, vacío si no.
     */
    public Optional<Usuario> autenticar(String documento, String password) throws SQLException {
        String sql = "SELECT * FROM usuarios WHERE documento = ? AND password = ?";

        Connection conn = DatabaseConnection.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, documento);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Usuario usuario = mapearResultado(rs);
                return Optional.of(usuario);
            }
        }
        return Optional.empty();
    }

    /**
     * Inserta un nuevo usuario en la base de datos.
     *
     * @param usuario Objeto Usuario a insertar (sin id).
     * @return El id generado automáticamente.
     */
    public int insertar(Usuario usuario) throws SQLException {
        String sql = "INSERT INTO usuarios (documento, nombre, password, rol) VALUES (?, ?, ?, ?)";

        Connection conn = DatabaseConnection.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario.getDocumento());
            ps.setString(2, usuario.getNombre());
            ps.setString(3, usuario.getPassword());
            ps.setString(4, usuario.getRol().name());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("No se pudo obtener el ID generado para el usuario.");
    }

    /**
     * Mapea una fila de ResultSet a un objeto Usuario.
     */
    private Usuario mapearResultado(ResultSet rs) throws SQLException {
        return new Usuario(
            rs.getInt("id_usuario"),
            rs.getString("documento"),
            rs.getString("nombre"),
            rs.getString("password"),
            Usuario.Rol.valueOf(rs.getString("rol"))
        );
    }
}
