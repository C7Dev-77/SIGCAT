package com.sigcat.dao;

import com.sigcat.geometry.SolapamientoValidator;
import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.supabase.DatabaseConnectionSupabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para acceso a datos y operaciones geoespaciales de Predios en Supabase (PostgreSQL).
 * Permite la persistencia remota, consulta de la malla oficial en la nube y detección de linderos.
 */
public class PredioDAOSupabase {

    /**
     * Inserta un nuevo predio con sus vértices en Supabase PostgreSQL de forma transaccional.
     *
     * @param predio               Objeto predio a persistir
     * @param documentoPropietario Cédula/documento del propietario
     * @param nombrePropietario    Nombre del propietario
     * @return El ID generado en Supabase, o -1 si falla
     */
    public int insertarPredio(Predio predio, String documentoPropietario, String nombrePropietario) {
        String sqlPredio = """
            INSERT INTO public.predios
                (codigo_catastral, nombre, direccion, uso_suelo, area_calculada, estado, tipo,
                 observaciones, nombre_propietario, origen_local_id, sincronizado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
            RETURNING id;
        """;

        String sqlVertice = """
            INSERT INTO public.vertices (id_predio, orden, longitud, latitud)
            VALUES (?, ?, ?, ?);
        """;

        Connection conn = null;
        try {
            conn = DatabaseConnectionSupabase.getConnection();
            conn.setAutoCommit(false);

            int idGenerado = -1;
            try (PreparedStatement stmt = conn.prepareStatement(sqlPredio)) {
                stmt.setString(1, predio.getCodigoCatastral() != null ? predio.getCodigoCatastral() : "70001-" + System.currentTimeMillis());
                stmt.setString(2, predio.getNombre() != null && !predio.getNombre().isEmpty() ? predio.getNombre() : "Predio " + predio.getCodigoCatastral());
                stmt.setString(3, predio.getDireccion());
                stmt.setString(4, predio.getUsoSuelo());
                stmt.setDouble(5, predio.getAreaCalculada());
                stmt.setString(6, predio.getEstado() != null ? predio.getEstado() : "PENDIENTE");
                stmt.setString(7, predio.getTipo() != null ? predio.getTipo() : "PRIVADO");
                stmt.setString(8, predio.getObservaciones());
                stmt.setString(9, nombrePropietario != null ? nombrePropietario : predio.getNombrePropietario());
                stmt.setInt(10, predio.getId()); // ID local como origen

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        idGenerado = rs.getInt(1);
                        predio.setIdRemoto(idGenerado);
                    }
                }
            }

            if (idGenerado != -1 && predio.getVertices() != null && !predio.getVertices().isEmpty()) {
                try (PreparedStatement stmtV = conn.prepareStatement(sqlVertice)) {
                    for (Vertice v : predio.getVertices()) {
                        stmtV.setInt(1, idGenerado);
                        stmtV.setInt(2, v.getOrden());
                        // En SIGCAT: coordX = longitud, coordY = latitud (WGS84)
                        stmtV.setDouble(3, v.getCoordX());
                        stmtV.setDouble(4, v.getCoordY());
                        stmtV.addBatch();
                    }
                    stmtV.executeBatch();
                }
            }

            conn.commit();
            return idGenerado;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            System.err.println("[PredioDAOSupabase] Error al insertar predio: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Obtiene todos los predios almacenados en Supabase, ordenados de más reciente a más antiguo.
     */
    public List<Predio> obtenerTodos() {
        List<Predio> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.codigo_catastral, p.nombre, p.direccion, p.uso_suelo,
                   p.area_calculada, p.estado, p.tipo, p.observaciones, p.nombre_propietario,
                   p.fecha_radicacion, p.origen_local_id,
                   d.storage_url AS recibo_url
            FROM public.predios p
            LEFT JOIN public.documentos d ON d.predio_id = p.id AND d.tipo_documento = 'RECIBO_VALIDACION'
            ORDER BY p.id DESC;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Predio p = mapearPredio(rs);
                p.setVertices(obtenerVerticesPorPredio(conn, p.getId()));
                lista.add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al listar todos: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Obtiene predios filtrados por estado (o todos si es comodín).
     */
    public List<Predio> obtenerPorEstado(String estado) {
        if (estado == null || estado.trim().isEmpty() ||
            estado.toUpperCase().contains("TODO") ||
            estado.toUpperCase().contains("TODA") ||
            estado.equalsIgnoreCase("ALL")) {
            return obtenerTodos();
        }

        List<Predio> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.codigo_catastral, p.nombre, p.direccion, p.uso_suelo,
                   p.area_calculada, p.estado, p.tipo, p.observaciones, p.nombre_propietario,
                   p.fecha_radicacion, p.origen_local_id,
                   d.storage_url AS recibo_url
            FROM public.predios p
            LEFT JOIN public.documentos d ON d.predio_id = p.id AND d.tipo_documento = 'RECIBO_VALIDACION'
            WHERE UPPER(p.estado) = UPPER(?)
            ORDER BY p.id DESC;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, estado);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Predio p = mapearPredio(rs);
                    p.setVertices(obtenerVerticesPorPredio(conn, p.getId()));
                    lista.add(p);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al listar por estado: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Obtiene la malla catastral oficial (predios APROBADOS y PUBLICOS) para validación de solapamientos.
     */
    public List<Predio> obtenerMallaOficial() {
        List<Predio> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.codigo_catastral, p.nombre, p.direccion, p.uso_suelo,
                   p.area_calculada, p.estado, p.tipo, p.observaciones, p.nombre_propietario,
                   p.fecha_radicacion, p.origen_local_id,
                   d.storage_url AS recibo_url
            FROM public.predios p
            LEFT JOIN public.documentos d ON d.predio_id = p.id AND d.tipo_documento = 'RECIBO_VALIDACION'
            WHERE p.estado IN ('APROBADO', 'PUBLICO')
            ORDER BY p.id ASC;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Predio p = mapearPredio(rs);
                p.setVertices(obtenerVerticesPorPredio(conn, p.getId()));
                lista.add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al obtener malla oficial: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Actualiza el dictamen de un predio en Supabase.
     */
    public boolean actualizarEstado(int predioId, String nuevoEstado, String observaciones) {
        String sql = """
            UPDATE public.predios
            SET estado = ?, observaciones = ?, fecha_revision = NOW()
            WHERE id = ?;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nuevoEstado);
            stmt.setString(2, observaciones);
            stmt.setInt(3, predioId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al actualizar estado: " + e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el dictamen de un predio en Supabase usando su código catastral universal.
     */
    public boolean actualizarEstadoPorCodigo(String codigoCatastral, String nuevoEstado, String observaciones) {
        if (codigoCatastral == null || codigoCatastral.trim().isEmpty()) return false;
        String sql = """
            UPDATE public.predios
            SET estado = ?, observaciones = ?, fecha_revision = NOW()
            WHERE codigo_catastral = ?;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nuevoEstado);
            stmt.setString(2, observaciones);
            stmt.setString(3, codigoCatastral.trim());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al actualizar estado por código: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un predio de Supabase por su código catastral universal.
     */
    public boolean eliminarPredioPorCodigo(String codigoCatastral) {
        if (codigoCatastral == null || codigoCatastral.trim().isEmpty()) return false;
        String sql = "DELETE FROM public.predios WHERE codigo_catastral = ?;";
        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codigoCatastral.trim());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al eliminar predio por código: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca qué predio oficial de Supabase contiene el punto geográfico consultado (clic en mapa).
     *
     * @param latitud  Latitud WGS84
     * @param longitud Longitud WGS84
     * @return El Predio encontrado con sus datos y recibo de validación, o null si está en terreno libre
     */
    public Predio buscarPredioEnCoordenada(double latitud, double longitud) {
        List<Predio> oficiales = obtenerMallaOficial();
        Vertice pto = new Vertice(0, 0, longitud, latitud);
        for (Predio p : oficiales) {
            if (p.getVertices() != null && p.getVertices().size() >= 3) {
                if (SolapamientoValidator.puntoDentroDePoligono(pto, p.getVertices())) {
                    return p;
                }
            }
        }
        return null;
    }

    private List<Vertice> obtenerVerticesPorPredio(Connection conn, int predioId) {
        List<Vertice> vertices = new ArrayList<>();
        String sql = "SELECT orden, longitud, latitud FROM public.vertices WHERE id_predio = ? ORDER BY orden ASC;";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, predioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    vertices.add(new Vertice(
                        predioId,
                        rs.getInt("orden"),
                        rs.getDouble("longitud"),
                        rs.getDouble("latitud")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[PredioDAOSupabase] Error al cargar vértices: " + e.getMessage());
        }
        return vertices;
    }

    private Predio mapearPredio(ResultSet rs) throws SQLException {
        Predio p = new Predio();
        p.setId(rs.getInt("id"));
        p.setIdRemoto(rs.getInt("id"));
        p.setCodigoCatastral(rs.getString("codigo_catastral"));
        p.setNombre(rs.getString("nombre"));
        p.setDireccion(rs.getString("direccion"));
        p.setUsoSuelo(rs.getString("uso_suelo"));
        p.setAreaCalculada(rs.getDouble("area_calculada"));
        p.setEstado(rs.getString("estado"));
        p.setTipo(rs.getString("tipo"));
        p.setObservaciones(rs.getString("observaciones"));
        p.setNombrePropietario(rs.getString("nombre_propietario"));
        p.setReciboValidacionUrl(rs.getString("recibo_url"));
        p.setSincronizado(true);
        return p;
    }
}
