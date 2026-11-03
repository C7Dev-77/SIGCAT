package com.sigcat.sync;

import com.sigcat.dao.PredioDAO;
import com.sigcat.dao.PredioDAOSupabase;
import com.sigcat.dao.VerticeDAO;
import com.sigcat.models.Predio;
import com.sigcat.models.Vertice;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de Sincronización Bidireccional (Offline-First) entre SQLite local y Supabase en la nube.
 *
 * Flujo:
 * 1. Push: Sube predios locales no sincronizados (sincronizado = 0) a Supabase.
 * 2. Pull: Descarga predios oficiales (APROBADOS y PUBLICOS) desde Supabase hacia SQLite.
 */
public class SyncEngine {

    private final PredioDAO localDAO = new PredioDAO();
    private final VerticeDAO verticeDAO = new VerticeDAO();
    private final PredioDAOSupabase remoteDAO = new PredioDAOSupabase();

    public static class SyncResult {
        public final boolean exitoso;
        public final int subidos;
        public final int descargados;
        public final String mensaje;

        public SyncResult(boolean exitoso, int subidos, int descargados, String mensaje) {
            this.exitoso = exitoso;
            this.subidos = subidos;
            this.descargados = descargados;
            this.mensaje = mensaje;
        }

        @Override
        public String toString() {
            return mensaje + " (Subidos: " + subidos + ", Descargados: " + descargados + ")";
        }
    }

    /**
     * Ejecuta el ciclo completo de sincronización bidireccional si hay conexión a Supabase.
     */
    public SyncResult sincronizar() {
        if (!DatabaseConnectionSupabase.isOnline()) {
            return new SyncResult(false, 0, 0, "Sin conexión a internet / Supabase no disponible.");
        }

        int subidos = 0;
        int descargados = 0;

        try {
            // 1. PUSH: Subir predios locales no sincronizados a Supabase
            List<Predio> pendientesSubir = obtenerPrediosLocalesNoSincronizados();
            for (Predio p : pendientesSubir) {
                // Asegurar vértices cargados
                if (p.getVertices() == null || p.getVertices().isEmpty()) {
                    p.setVertices(verticeDAO.obtenerPorPredio(p.getId()));
                }
                int idRemoto = remoteDAO.insertarPredio(p, null, p.getNombrePropietario());
                if (idRemoto > 0) {
                    marcarPredioLocalComoSincronizado(p.getId(), idRemoto);
                    subidos++;
                }
            }

            // 2. PULL: Descargar malla oficial de Supabase (predios aprobados y públicos)
            List<Predio> mallaRemota = remoteDAO.obtenerMallaOficial();
            for (Predio rem : mallaRemota) {
                if (!existePredioLocalPorCodigo(rem.getCodigoCatastral())) {
                    guardarPredioRemotoEnLocal(rem);
                    descargados++;
                }
            }

            return new SyncResult(true, subidos, descargados, "Sincronización completada con éxito.");

        } catch (Exception e) {
            System.err.println("[SyncEngine] Error en proceso de sincronización: " + e.getMessage());
            return new SyncResult(false, subidos, descargados, "Error durante sincronización: " + e.getMessage());
        }
    }

    private List<Predio> obtenerPrediosLocalesNoSincronizados() {
        List<Predio> lista = new ArrayList<>();
        String sql = "SELECT * FROM predios WHERE sincronizado = 0 OR sincronizado IS NULL;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Predio p = new Predio();
                p.setId(rs.getInt("id_predio"));
                p.setCodigoCatastral(rs.getString("codigo_catastral"));
                p.setNombre(rs.getString("nombre"));
                p.setDireccion(rs.getString("direccion"));
                p.setUsoSuelo(rs.getString("uso_suelo"));
                p.setAreaCalculada(rs.getDouble("area"));
                p.setEstado(rs.getString("estado"));
                p.setPropietarioId(rs.getInt("propietario_id"));
                p.setObservaciones(rs.getString("observaciones"));
                lista.add(p);
            }
        } catch (SQLException e) {
            System.err.println("[SyncEngine] Error al consultar pendientes locales: " + e.getMessage());
        }
        return lista;
    }

    private void marcarPredioLocalComoSincronizado(int idLocal, int idRemoto) {
        String sql = "UPDATE predios SET sincronizado = 1, id_remoto = ? WHERE id_predio = ?;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idRemoto);
            stmt.setInt(2, idLocal);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SyncEngine] Error marcando sincronizado: " + e.getMessage());
        }
    }

    private boolean existePredioLocalPorCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) return false;
        String sql = "SELECT 1 FROM predios WHERE codigo_catastral = ? LIMIT 1;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, codigo);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void guardarPredioRemotoEnLocal(Predio p) {
        String sqlPredio = """
            INSERT INTO predios (codigo_catastral, nombre, direccion, uso_suelo, area, estado, propietario_id, observaciones, sincronizado, id_remoto)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, 1, ?);
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlPredio, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, p.getCodigoCatastral());
            stmt.setString(2, p.getNombre());
            stmt.setString(3, p.getDireccion());
            stmt.setString(4, p.getUsoSuelo());
            stmt.setDouble(5, p.getAreaCalculada());
            stmt.setString(6, p.getEstado());
            stmt.setString(7, p.getObservaciones());
            stmt.setInt(8, p.getIdRemoto());

            int filas = stmt.executeUpdate();
            if (filas > 0 && p.getVertices() != null && !p.getVertices().isEmpty()) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int idLocal = rs.getInt(1);
                        verticeDAO.guardarVerticesTransaccional(conn, idLocal, p.getVertices());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SyncEngine] Error al guardar predio remoto en local: " + e.getMessage());
        }
    }
}

