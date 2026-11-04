package com.sigcat.dao;

import com.sigcat.models.AuditLog;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DAO para persistencia y consulta del Historial de Auditoría Catastral (Mejora #4).
 * Funciona bajo arquitectura Offline-First sincronizando con Supabase PostgreSQL y guardando en SQLite.
 */
public class AuditLogDAO {

    public boolean registrar(AuditLog log) {
        if (log.getId() == null || log.getId().isEmpty()) {
            log.setId(UUID.randomUUID().toString());
        }

        boolean guardadoRemoto = false;
        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = """
                INSERT INTO public.audit_log
                    (id, entidad, entidad_id, accion, estado_anterior, estado_nuevo, usuario_id, usuario_nombre, detalles, fecha)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, NOW());
            """;
            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, log.getId());
                stmt.setString(2, log.getEntidad());
                stmt.setString(3, log.getEntidadId());
                stmt.setString(4, log.getAccion());
                stmt.setString(5, log.getEstadoAnterior());
                stmt.setString(6, log.getEstadoNuevo());
                stmt.setString(7, log.getUsuarioId());
                stmt.setString(8, log.getUsuarioNombre());
                stmt.setString(9, log.getDetalles());

                stmt.executeUpdate();
                guardadoRemoto = true;
            } catch (SQLException e) {
                System.err.println("[AuditLogDAO] Error al insertar en Supabase: " + e.getMessage());
            }
        }

        boolean guardadoLocal = registrarLocal(log);
        return guardadoRemoto || guardadoLocal;
    }

    private boolean registrarLocal(AuditLog log) {
        String sql = """
            INSERT OR REPLACE INTO audit_log
                (id, entidad, entidad_id, accion, estado_anterior, estado_nuevo, usuario_id, usuario_nombre, detalles, fecha)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, log.getId());
            stmt.setString(2, log.getEntidad());
            stmt.setString(3, log.getEntidadId());
            stmt.setString(4, log.getAccion());
            stmt.setString(5, log.getEstadoAnterior());
            stmt.setString(6, log.getEstadoNuevo());
            stmt.setString(7, log.getUsuarioId());
            stmt.setString(8, log.getUsuarioNombre());
            stmt.setString(9, log.getDetalles());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuditLogDAO] Error guardando log en SQLite: " + e.getMessage());
            return false;
        }
    }

    public List<AuditLog> listarRecientes(int limite) {
        List<AuditLog> lista = new ArrayList<>();

        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = "SELECT * FROM public.audit_log ORDER BY fecha DESC LIMIT ?";
            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limite);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapearLog(rs));
                    }
                }
                return lista;
            } catch (SQLException e) {
                System.err.println("[AuditLogDAO] Error listando logs remotos: " + e.getMessage());
            }
        }

        // Fallback SQLite
        String sqlLocal = "SELECT * FROM audit_log ORDER BY fecha DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlLocal)) {

            stmt.setInt(1, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearLog(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuditLogDAO] Error listando logs locales: " + e.getMessage());
        }
        return lista;
    }

    private AuditLog mapearLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getString("id"));
        log.setEntidad(rs.getString("entidad"));
        log.setEntidadId(rs.getString("entidad_id"));
        log.setAccion(rs.getString("accion"));
        log.setEstadoAnterior(rs.getString("estado_anterior"));
        log.setEstadoNuevo(rs.getString("estado_nuevo"));
        log.setUsuarioId(rs.getString("usuario_id"));
        log.setUsuarioNombre(rs.getString("usuario_nombre"));
        log.setDetalles(rs.getString("detalles"));

        Timestamp ts = null;
        try {
            ts = rs.getTimestamp("fecha");
        } catch (Exception ignored) {}

        if (ts != null) {
            log.setFecha(ts.toLocalDateTime());
        } else {
            log.setFecha(LocalDateTime.now());
        }
        return log;
    }
}

