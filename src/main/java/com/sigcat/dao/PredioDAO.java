package com.sigcat.dao;

import com.sigcat.models.Predio;
import com.sigcat.models.ResumenMensual;
import com.sigcat.models.Vertice;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    public boolean actualizarDatosPredio(int predioId, String nombre, String direccion, String usoSuelo, String observaciones) {
        String sql = "UPDATE predios SET nombre = ?, direccion = ?, uso_suelo = ?, observaciones = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre);
            stmt.setString(2, direccion);
            stmt.setString(3, usoSuelo);
            stmt.setString(4, observaciones);
            stmt.setInt(5, predioId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al actualizar datos de predio: " + e.getMessage());
            return false;
        }
    }

    public boolean eliminarPredio(int predioId) {
        String sqlVertices = "DELETE FROM vertices WHERE id_predio = ?";
        String sqlPredio = "DELETE FROM predios WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmtV = conn.prepareStatement(sqlVertices)) {
                stmtV.setInt(1, predioId);
                stmtV.executeUpdate();
            }

            int filas;
            try (PreparedStatement stmtP = conn.prepareStatement(sqlPredio)) {
                stmtP.setInt(1, predioId);
                filas = stmtP.executeUpdate();
            }

            conn.commit();
            return filas > 0;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            System.err.println("Error al eliminar predio: " + e.getMessage());
            return false;
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

    public List<Predio> obtenerPorPropietario(int propietarioId) {
        return obtenerPorPropietario(propietarioId, null);
    }

    public List<Predio> obtenerPorPropietario(int propietarioId, String nombrePropietario) {
        List<Predio> lista = new ArrayList<>();
        boolean tieneNombre = nombrePropietario != null && !nombrePropietario.trim().isEmpty();
        String sql = tieneNombre
                ? "SELECT * FROM predios WHERE propietario_id = ? OR LOWER(nombre_propietario) = LOWER(?) ORDER BY id DESC"
                : "SELECT * FROM predios WHERE propietario_id = ? ORDER BY id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, propietarioId);
            if (tieneNombre) {
                stmt.setString(2, nombrePropietario.trim());
            }
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

    public List<Predio> obtenerTodos() {
        List<Predio> lista = new ArrayList<>();
        String sql = "SELECT p.*, u.nombre AS nombre_propietario FROM predios p " +
                     "LEFT JOIN usuarios u ON p.propietario_id = u.id_usuario " +
                     "ORDER BY p.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Predio p = mapearPredio(rs);
                p.setVertices(verticeDAO.obtenerPorPredio(p.getId()));
                lista.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Error al listar todos los predios: " + e.getMessage());
        }
        return lista;
    }

    public List<Predio> obtenerPorEstado(String estado) {
        if (estado == null || estado.trim().isEmpty() ||
            estado.toUpperCase().contains("TODO") ||
            estado.toUpperCase().contains("TODA") ||
            estado.equalsIgnoreCase("ALL")) {
            return obtenerTodos();
        }

        List<Predio> lista = new ArrayList<>();
        String sql = "SELECT p.*, u.nombre AS nombre_propietario FROM predios p " +
                     "LEFT JOIN usuarios u ON p.propietario_id = u.id_usuario " +
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
        // Predios APROBADOS y predios PUBLICOS (capa base institucional de Sincelejo)
        String sql = "SELECT p.*, COALESCE(p.nombre_propietario, u.nombre) AS nombre_propietario_join " +
                     "FROM predios p LEFT JOIN usuarios u ON p.propietario_id = u.id_usuario " +
                     "WHERE p.estado IN ('APROBADO', 'PUBLICO')";

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

    /**
     * Busca qué predio de la malla oficial contiene las coordenadas geográficas dadas (clic en mapa).
     */
    public Predio buscarPredioEnCoordenada(double latitud, double longitud) {
        List<Predio> malla = obtenerPrediosParaValidacionTopologica();
        Vertice clickPto = new Vertice(longitud, latitud, 0);
        for (Predio p : malla) {
            if (p.getVertices() != null && p.getVertices().size() >= 3) {
                if (com.sigcat.geometry.SolapamientoValidator.puntoDentroDePoligono(clickPto, p.getVertices())) {
                    return p;
                }
            }
        }
        return null;
    }

    public boolean actualizarReciboValidacion(int predioId, String reciboUrl) {
        String sql = "UPDATE predios SET recibo_validacion_url = ? WHERE id_predio = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reciboUrl);
            stmt.setInt(2, predioId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error al actualizar recibo validacion: " + e.getMessage());
            return false;
        }
    }

    // ─── Resumen mensual (panel del funcionario) ────────────────────────────

    /**
     * Normaliza una columna de fecha a hora local de Colombia. SQLite guarda
     * CURRENT_TIMESTAMP en UTC ("2026-11-05 03:10:00"), así que una solicitud
     * radicada a las 10 p. m. del 31 de octubre quedaría en noviembre si no se
     * convierte. Las fechas sin hora ("2026-10-31") se dejan tal cual.
     */
    private static String fechaLocal(String columna) {
        return "CASE WHEN " + columna + " IS NULL THEN NULL "
             + "WHEN length(" + columna + ") > 10 THEN datetime(" + columna + ", 'localtime') "
             + "ELSE " + columna + " END";
    }

    /**
     * Resumen por mes de la gestión catastral: solicitudes registradas,
     * cuántas de ellas siguen pendientes, y dictámenes de aprobación y
     * rechazo emitidos en cada mes. Excluye los predios públicos.
     *
     * @param anio año a consultar; si es null devuelve todos los meses con
     *             movimiento. Con un año, devuelve sus 12 meses (en cero los
     *             que no tienen movimiento).
     */
    public List<ResumenMensual> resumenMensual(Integer anio) throws SQLException {
        String sql = """
            WITH base AS (
                SELECT UPPER(estado) AS estado,
                       %s AS f_reg,
                       %s AS f_rev
                FROM predios
                WHERE UPPER(estado) IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')
                  AND fecha_registro IS NOT NULL
            ),
            eventos AS (
                SELECT substr(f_reg, 1, 7) AS periodo,
                       1 AS registrados,
                       CASE WHEN estado = 'PENDIENTE' THEN 1 ELSE 0 END AS pendientes,
                       0 AS aprobados,
                       0 AS rechazados
                FROM base
                UNION ALL
                SELECT substr(COALESCE(f_rev, f_reg), 1, 7),
                       0,
                       0,
                       CASE WHEN estado = 'APROBADO'  THEN 1 ELSE 0 END,
                       CASE WHEN estado = 'RECHAZADO' THEN 1 ELSE 0 END
                FROM base
                WHERE estado IN ('APROBADO', 'RECHAZADO')
            )
            SELECT periodo,
                   SUM(registrados) AS registrados,
                   SUM(pendientes)  AS pendientes,
                   SUM(aprobados)   AS aprobados,
                   SUM(rechazados)  AS rechazados
            FROM eventos
            WHERE periodo IS NOT NULL
              AND (? IS NULL OR substr(periodo, 1, 4) = ?)
            GROUP BY periodo
            ORDER BY periodo
            """.formatted(fechaLocal("fecha_registro"), fechaLocal("fecha_revision"));

        Map<String, ResumenMensual> porPeriodo = new TreeMap<>();
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String filtro = (anio == null) ? null : String.format("%04d", anio);
            stmt.setString(1, filtro);
            stmt.setString(2, filtro);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String periodo = rs.getString("periodo"); // "AAAA-MM"
                    try {
                        int a = Integer.parseInt(periodo.substring(0, 4));
                        int m = Integer.parseInt(periodo.substring(5, 7));
                        porPeriodo.put(periodo, new ResumenMensual(a, m,
                            rs.getInt("registrados"), rs.getInt("pendientes"),
                            rs.getInt("aprobados"), rs.getInt("rechazados")));
                    } catch (RuntimeException formatoInvalido) {
                        System.err.println("[RESUMEN] Fecha con formato no reconocido: " + periodo);
                    }
                }
            }
        }

        if (anio != null) {
            for (int mes = 1; mes <= 12; mes++) {
                porPeriodo.putIfAbsent(String.format("%04d-%02d", anio, mes), ResumenMensual.vacio(anio, mes));
            }
        }
        return new ArrayList<>(porPeriodo.values());
    }

    /** Años con al menos una solicitud registrada o dictaminada, del más reciente al más antiguo. */
    public List<Integer> aniosConMovimiento() throws SQLException {
        String sql = """
            SELECT DISTINCT CAST(substr(f, 1, 4) AS INTEGER) AS anio FROM (
                SELECT %s AS f FROM predios WHERE UPPER(estado) IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')
                UNION
                SELECT %s FROM predios WHERE UPPER(estado) IN ('APROBADO', 'RECHAZADO')
            )
            WHERE f IS NOT NULL
            ORDER BY anio DESC
            """.formatted(fechaLocal("fecha_registro"), fechaLocal("fecha_revision"));

        List<Integer> anios = new ArrayList<>();
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int a = rs.getInt("anio");
                if (a > 0) anios.add(a);
            }
        }
        return anios;
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
        try {
            String nom = rs.getString("nombre_propietario_join");
            if (nom != null && !nom.isEmpty()) p.setNombrePropietario(nom);
        } catch (SQLException ignored) {}
        try {
            String nom = rs.getString("nombre_propietario");
            if (nom != null && !nom.isEmpty() && p.getNombrePropietario() == null) p.setNombrePropietario(nom);
        } catch (SQLException ignored) {}
        try {
            String tipo = rs.getString("tipo");
            if (tipo != null) p.setTipo(tipo);
        } catch (SQLException ignored) {}
        try {
            String url = rs.getString("recibo_validacion_url");
            if (url != null) p.setReciboValidacionUrl(url);
        } catch (SQLException ignored) {}
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
