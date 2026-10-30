package com.sigcat.dao;

import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestión transaccional de Predios y linderos.
 */
public class PredioDAO {

    private final VerticeDAO verticeDAO;

    public PredioDAO() {
        this.verticeDAO = new VerticeDAO();
    }

    public int registrarPredio(Predio predio) {
        String sqlPredio = "INSERT INTO predios (codigo_catastral, nombre, direccion, uso_suelo, area, estado, propietario_id, observaciones) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            int predioId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(sqlPredio, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, predio.getCodigoCatastral());
                stmt.setString(2, predio.getNombre());
                stmt.setString(3, predio.getDireccion());
                stmt.setString(4, predio.getUsoSuelo());
                stmt.setDouble(5, predio.getArea());
                stmt.setString(6, predio.getEstado() != null ? predio.getEstado() : "PENDIENTE");
                stmt.setInt(7, predio.getPropietarioId());
                stmt.setString(8, predio.getObservaciones());

                int filas = stmt.executeUpdate();
                if (filas > 0) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            predioId = rs.getInt(1);
                            predio.setId(predioId);
                        }
                    }
                }
            }

            if (predioId != -1 && predio.getVertices() != null && !predio.getVertices().isEmpty()) {
                verticeDAO.guardarVerticesTransaccional(conn, predioId, predio.getVertices());
            }

            conn.commit();
            return predioId;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            System.err.println("Error al registrar predio: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean actualizarEstado(int predioId, String nuevoEstado, String observaciones) {
        String sql = "UPDATE predios SET estado = ?, observaciones = ?, fecha_revision = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nuevoEstado);
            stmt.setString(2, observaciones);
            stmt.setInt(3, predioId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al actualizar estado: " + e.getMessage());
            return false;
        }
    }

    public List<Predio> obtenerPorPropietario(int propietarioId) {
        List<Predio> lista = new ArrayList<>();
        String sql = "SELECT * FROM predios WHERE propietario_id = ? ORDER BY id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, propietarioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Predio p = mapearPredio(rs);
                    p.setVertices(verticeDAO.obtenerPorPredio(p.getId()));
                    lista.add(p);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al listar predios de propietario: " + e.getMessage());
        }
        return lista;
    }

    public List<Predio> obtenerPorEstado(String estado) {
        List<Predio> lista = new ArrayList<>();
        String sql = "SELECT p.*, u.nombre AS nombre_propietario FROM predios p " +
                     "LEFT JOIN usuarios u ON p.propietario_id = u.id " +
                     "WHERE UPPER(p.estado) = UPPER(?) ORDER BY p.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, estado);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Predio p = mapearPredio(rs);
                    p.setVertices(verticeDAO.obtenerPorPredio(p.getId()));
                    lista.add(p);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al listar por estado: " + e.getMessage());
        }
        return lista;
    }

    public List<Predio> obtenerPrediosParaValidacionTopologica() {
        List<Predio> lista = new ArrayList<>();
        // Solo predios APROBADOS conforman la malla catastral oficial.
        // Los PENDIENTES no se renderizan como malla para evitar solapamientos visuales falsos.
        String sql = "SELECT * FROM predios WHERE estado = 'APROBADO'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Predio p = mapearPredio(rs);
                p.setVertices(verticeDAO.obtenerPorPredio(p.getId()));
                lista.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Error al cargar malla topológica: " + e.getMessage());
        }
        return lista;
    }

    private Predio mapearPredio(ResultSet rs) throws SQLException {
        Predio p = new Predio();
        p.setId(rs.getInt("id"));
        p.setCodigoCatastral(rs.getString("codigo_catastral"));
        p.setNombre(rs.getString("nombre"));
        p.setDireccion(rs.getString("direccion"));
        p.setUsoSuelo(rs.getString("uso_suelo"));
        p.setArea(rs.getDouble("area"));
        p.setEstado(rs.getString("estado"));
        p.setPropietarioId(rs.getInt("propietario_id"));
        p.setObservaciones(rs.getString("observaciones"));
        return p;
    }

    // ─── Métodos de compatibilidad con Controladores y Pruebas Previas ───────────

    public int insertarConVertices(Predio predio) throws SQLException {
        int id = registrarPredio(predio);
        if (id == -1) {
            throw new SQLException("Error al registrar predio y sus vértices.");
        }
        return id;
    }

    public List<Predio> listarAprobadosConVertices() throws SQLException {
        return obtenerPorEstado("APROBADO");
    }

    public List<Predio> listarPendientes() throws SQLException {
        return obtenerPorEstado("PENDIENTE");
    }

    public void actualizarEstado(int idPredio, Predio.Estado nuevoEstado, int idFuncionario) throws SQLException {
        actualizarEstado(idPredio, nuevoEstado.name(), "Aprobación/Revisión funcionario ID: " + idFuncionario);
    }
}
