package com.sigcat.dao;

import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object para la entidad Vertice.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class VerticeDAO {

    /**
     * Inserta una lista de vértices en una transacción activa.
     */
    public void guardarVerticesTransaccional(Connection conn, int predioId, List<Vertice> vertices) throws SQLException {
        if (vertices == null || vertices.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO vertices (id_predio, orden, coord_x, coord_y) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int orden = 1;
            for (Vertice v : vertices) {
                ps.setInt(1, predioId);
                ps.setInt(2, v.getOrden() > 0 ? v.getOrden() : orden++);
                ps.setDouble(3, v.getCoordX());
                ps.setDouble(4, v.getCoordY());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Inserta un vértice asociado a un predio.
     */
    public void insertar(Connection conn, Vertice vertice) throws SQLException {
        String sql = "INSERT INTO vertices (id_predio, orden, coord_x, coord_y) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vertice.getIdPredio());
            ps.setInt(2, vertice.getOrden());
            ps.setDouble(3, vertice.getCoordX());
            ps.setDouble(4, vertice.getCoordY());
            ps.executeUpdate();
        }
    }

    /**
     * Obtiene todos los vértices de un predio, ordenados.
     */
    public List<Vertice> obtenerPorPredio(int idPredio) throws SQLException {
        String sql = "SELECT * FROM vertices WHERE id_predio = ? ORDER BY orden";
        List<Vertice> resultado = new ArrayList<>();

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPredio);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                resultado.add(mapearResultado(rs));
            }
        }
        return resultado;
    }

    private Vertice mapearResultado(ResultSet rs) throws SQLException {
        Vertice v = new Vertice(
            rs.getInt("id_predio"),
            rs.getInt("orden"),
            rs.getDouble("coord_x"),
            rs.getDouble("coord_y")
        );
        try {
            v.setId(rs.getInt("id_vertice"));
        } catch (SQLException e) {
            v.setId(rs.getInt("id"));
        }
        return v;
    }
}
