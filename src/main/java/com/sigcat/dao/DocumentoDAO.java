package com.sigcat.dao;

import com.sigcat.models.DocumentoLegal;
import com.sigcat.supabase.DatabaseConnectionSupabase;
import com.sigcat.utils.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DAO para la gestión de documentos legales y recibos de validación catastral.
 * Implementa arquitectura Offline-First: sincroniza con Supabase PostgreSQL y mantiene
 * réplica local en SQLite para funcionamiento sin conexión.
 */
public class DocumentoDAO {

    public boolean insertar(DocumentoLegal doc) {
        if (doc.getId() == null || doc.getId().isEmpty()) {
            doc.setId(UUID.randomUUID().toString());
        }

        boolean guardadoRemoto = false;
        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = """
                INSERT INTO public.documentos 
                    (id, predio_id, propietario_id, tipo_documento, nombre_archivo, storage_path, storage_url, estado, descripcion)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?);
            """;

            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, doc.getId());
                stmt.setInt(2, doc.getPredioId());
                boolean isUuid = false;
                if (doc.getPropietarioId() != null && !doc.getPropietarioId().isEmpty()) {
                    try {
                        UUID.fromString(doc.getPropietarioId());
                        isUuid = true;
                    } catch (IllegalArgumentException ignored) {}
                }
                if (isUuid) {
                    stmt.setString(3, doc.getPropietarioId());
                } else {
                    stmt.setNull(3, Types.OTHER);
                }
                stmt.setString(4, doc.getTipoDocumento());
                stmt.setString(5, doc.getNombreArchivo());
                stmt.setString(6, doc.getStoragePath());
                stmt.setString(7, doc.getStorageUrl());
                stmt.setString(8, doc.getEstado() != null ? doc.getEstado() : "PENDIENTE_REVISION");
                stmt.setString(9, doc.getDescripcion());

                stmt.executeUpdate();
                guardadoRemoto = true;
            } catch (SQLException e) {
                System.err.println("[DocumentoDAO] Error al insertar en Supabase: " + e.getMessage());
            }
        }

        // Siempre guardar o actualizar réplica local en SQLite
        boolean guardadoLocal = insertarLocal(doc);
        return guardadoRemoto || guardadoLocal;
    }

    private boolean insertarLocal(DocumentoLegal doc) {
        String sql = "INSERT OR REPLACE INTO documentos " +
                     "(id, predio_id, propietario_id, tipo_documento, nombre_archivo, storage_path, storage_url, estado, descripcion) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, doc.getId());
            stmt.setInt(2, doc.getPredioId());
            stmt.setString(3, doc.getPropietarioId());
            stmt.setString(4, doc.getTipoDocumento());
            stmt.setString(5, doc.getNombreArchivo());
            stmt.setString(6, doc.getStoragePath());
            stmt.setString(7, doc.getStorageUrl());
            stmt.setString(8, doc.getEstado() != null ? doc.getEstado() : "PENDIENTE_REVISION");
            stmt.setString(9, doc.getDescripcion());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error guardando local: " + e.getMessage());
            return false;
        }
    }

    public List<DocumentoLegal> listarPorPredio(int predioId) {
        List<DocumentoLegal> lista = new ArrayList<>();

        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = """
                SELECT id, predio_id, propietario_id, tipo_documento, nombre_archivo, 
                       storage_path, storage_url, estado, descripcion, subido_en, revisado_en, revisado_por
                FROM public.documentos
                WHERE predio_id = ?
                ORDER BY subido_en DESC;
            """;

            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, predioId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DocumentoLegal doc = mapearDocumento(rs);
                        lista.add(doc);
                        insertarLocal(doc); // Cachear localmente
                    }
                }
                return lista;
            } catch (SQLException e) {
                System.err.println("[DocumentoDAO] Error al listar de Supabase: " + e.getMessage());
            }
        }

        // Fallback local en SQLite
        String sqlLocal = "SELECT * FROM documentos WHERE predio_id = ? ORDER BY subido_en DESC;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlLocal)) {

            stmt.setInt(1, predioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearDocumentoLocal(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al listar local: " + e.getMessage());
        }
        return lista;
    }

    public DocumentoLegal obtenerReciboValidacion(int predioId) {
        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = """
                SELECT id, predio_id, propietario_id, tipo_documento, nombre_archivo, 
                       storage_path, storage_url, estado, descripcion, subido_en, revisado_en, revisado_por
                FROM public.documentos
                WHERE predio_id = ? AND tipo_documento = 'RECIBO_VALIDACION'
                ORDER BY subido_en DESC
                LIMIT 1;
            """;

            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, predioId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        DocumentoLegal doc = mapearDocumento(rs);
                        insertarLocal(doc);
                        return doc;
                    }
                }
            } catch (SQLException e) {
                System.err.println("[DocumentoDAO] Error al consultar recibo en Supabase: " + e.getMessage());
            }
        }

        // Fallback local
        String sqlLocal = "SELECT * FROM documentos WHERE predio_id = ? AND tipo_documento = 'RECIBO_VALIDACION' LIMIT 1;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlLocal)) {

            stmt.setInt(1, predioId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapearDocumentoLocal(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error consultando recibo local: " + e.getMessage());
        }
        return null;
    }

    public boolean actualizarEstado(String docId, String nuevoEstado, String revisadoPorId) {
        boolean remoto = false;
        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = """
                UPDATE public.documentos
                SET estado = ?, revisado_en = NOW(), revisado_por = ?::uuid
                WHERE id = ?::uuid;
            """;

            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, nuevoEstado);
                if (revisadoPorId != null && !revisadoPorId.isEmpty()) {
                    stmt.setString(2, revisadoPorId);
                } else {
                    stmt.setNull(2, Types.VARCHAR);
                }
                stmt.setString(3, docId);

                remoto = stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[DocumentoDAO] Error al actualizar estado remoto: " + e.getMessage());
            }
        }

        String sqlLocal = "UPDATE documentos SET estado = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlLocal)) {
            stmt.setString(1, nuevoEstado);
            stmt.setString(2, docId);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}

        return remoto;
    }

    public boolean eliminar(String docId) {
        boolean remoto = false;
        if (DatabaseConnectionSupabase.isOnline()) {
            String sql = "DELETE FROM public.documentos WHERE id = ?::uuid;";
            try (Connection conn = DatabaseConnectionSupabase.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, docId);
                remoto = stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("[DocumentoDAO] Error al eliminar documento en Supabase: " + e.getMessage());
            }
        }

        String sqlLocal = "DELETE FROM documentos WHERE id = ?;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlLocal)) {
            stmt.setString(1, docId);
            int filas = stmt.executeUpdate();
            return remoto || filas > 0;
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al eliminar documento local: " + e.getMessage());
            return remoto;
        }
    }

    private DocumentoLegal mapearDocumento(ResultSet rs) throws SQLException {
        DocumentoLegal d = new DocumentoLegal();
        d.setId(rs.getString("id"));
        d.setPredioId(rs.getInt("predio_id"));
        d.setPropietarioId(rs.getString("propietario_id"));
        d.setTipoDocumento(rs.getString("tipo_documento"));
        d.setNombreArchivo(rs.getString("nombre_archivo"));
        d.setStoragePath(rs.getString("storage_path"));
        d.setStorageUrl(rs.getString("storage_url"));
        d.setEstado(rs.getString("estado"));
        d.setDescripcion(rs.getString("descripcion"));

        Timestamp subido = rs.getTimestamp("subido_en");
        if (subido != null) d.setSubidoEn(subido.toLocalDateTime());

        Timestamp revisado = rs.getTimestamp("revisado_en");
        if (revisado != null) d.setRevisadoEn(revisado.toLocalDateTime());

        d.setRevisadoPor(rs.getString("revisado_por"));
        return d;
    }

    private DocumentoLegal mapearDocumentoLocal(ResultSet rs) throws SQLException {
        DocumentoLegal d = new DocumentoLegal();
        d.setId(rs.getString("id"));
        d.setPredioId(rs.getInt("predio_id"));
        d.setPropietarioId(rs.getString("propietario_id"));
        d.setTipoDocumento(rs.getString("tipo_documento"));
        d.setNombreArchivo(rs.getString("nombre_archivo"));
        d.setStoragePath(rs.getString("storage_path"));
        d.setStorageUrl(rs.getString("storage_url"));
        d.setEstado(rs.getString("estado"));
        d.setDescripcion(rs.getString("descripcion"));
        return d;
    }
}
