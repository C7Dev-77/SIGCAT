package com.sigcat.dao;

import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object para la entidad Predio.
 * Inserta el predio junto con sus vértices en una sola transacción.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class PredioDAO {

    private final VerticeDAO verticeDAO = new VerticeDAO();

    /**
     * Inserta un predio nuevo junto con todos sus vértices, de forma
     * transaccional (si algo falla, no queda nada guardado a medias).
     *
     * @return el id generado para el predio.
     */
    public int insertarConVertices(Predio predio) throws SQLException {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "INSERT INTO predios (id_propietario, area_calculada, estado, fecha_registro) "
                    + "VALUES (?, ?, ?, ?)";

        try {
            conn.setAutoCommit(false);

            int idGenerado;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, predio.getIdPropietario());
                ps.setDouble(2, predio.getAreaCalculada());
                ps.setString(3, predio.getEstado().name());
                ps.setString(4, predio.getFechaRegistro().toString());
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (!keys.next()) {
                    throw new SQLException("No se pudo obtener el ID generado para el predio.");
                }
                idGenerado = keys.getInt(1);
            }

            for (Vertice v : predio.getVertices()) {
                v.setIdPredio(idGenerado);
                verticeDAO.insertar(conn, v);
            }

            conn.commit();
            return idGenerado;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Lista todos los predios en estado APROBADO, con sus vértices ya
     * cargados. Este es el conjunto que se usa para validar solapamiento
     * contra un predio nuevo.
     */
    public List<Predio> listarAprobadosConVertices() throws SQLException {
        String sql = "SELECT * FROM predios WHERE estado = 'APROBADO'";
        List<Predio> resultado = new ArrayList<>();

        Connection conn = DatabaseConnection.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Predio predio = mapearResultado(rs);
                predio.setVertices(verticeDAO.obtenerPorPredio(predio.getId()));
                resultado.add(predio);
            }
        }
        return resultado;
    }

    /**
     * Lista todos los predios en estado PENDIENTE (para el panel del
     * funcionario catastral).
     */
    public List<Predio> listarPendientes() throws SQLException {
        String sql = "SELECT * FROM predios WHERE estado = 'PENDIENTE'";
        List<Predio> resultado = new ArrayList<>();

        Connection conn = DatabaseConnection.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Predio predio = mapearResultado(rs);
                predio.setVertices(verticeDAO.obtenerPorPredio(predio.getId()));
                resultado.add(predio);
            }
        }
        return resultado;
    }

    /**
     * Actualiza el estado de un predio (aprobar/rechazar) y asigna el
     * funcionario que tomó la decisión.
     */
    public void actualizarEstado(int idPredio, Predio.Estado nuevoEstado, int idFuncionario) throws SQLException {
        String sql = "UPDATE predios SET estado = ?, id_funcionario = ? WHERE id_predio = ?";

        Connection conn = DatabaseConnection.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.name());
            ps.setInt(2, idFuncionario);
            ps.setInt(3, idPredio);
            ps.executeUpdate();
        }
    }

    private Predio mapearResultado(ResultSet rs) throws SQLException {
        Predio predio = new Predio();
        predio.setId(rs.getInt("id_predio"));
        predio.setIdPropietario(rs.getInt("id_propietario"));
        predio.setIdFuncionario(rs.getInt("id_funcionario"));
        predio.setAreaCalculada(rs.getDouble("area_calculada"));
        predio.setEstado(Predio.Estado.valueOf(rs.getString("estado")));
        predio.setFechaRegistro(LocalDate.parse(rs.getString("fecha_registro")));
        return predio;
    }
}