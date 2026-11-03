package com.sigcat.dao;

import com.sigcat.models.DocumentoLegal;
import com.sigcat.supabase.DatabaseConnectionSupabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la gestión de documentos legales y recibos de validación catastral.
 * Conecta directamente a Supabase PostgreSQL mediante JDBC.
 */
public class DocumentoDAO {

    public boolean insertar(DocumentoLegal doc) {
        String sql = """
            INSERT INTO public.documentos 
                (predio_id, propietario_id, tipo_documento, nombre_archivo, storage_path, storage_url, estado, descripcion)
            VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?)
            RETURNING id;
        """;

        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, doc.getPredioId());
            if (doc.getPropietarioId() != null && !doc.getPropietarioId().isEmpty()) {
                stmt.setString(2, doc.getPropietarioId());
            } else {
                stmt.setNull(2, Types.VARCHAR);
            }
            stmt.setString(3, doc.getTipoDocumento());
            stmt.setString(4, doc.getNombreArchivo());
            stmt.setString(5, doc.getStoragePath());
            stmt.setString(6, doc.getStorageUrl());
            stmt.setString(7, doc.getEstado() != null ? doc.getEstado() : "PENDIENTE_REVISION");
            stmt.setString(8, doc.getDescripcion());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    doc.setId(rs.getString(1));
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al insertar documento: " + e.getMessage());
            return false;
        }
    }

    public List<DocumentoLegal> listarPorPredio(int predioId) {
        List<DocumentoLegal> lista = new ArrayList<>();
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
                    lista.add(mapearDocumento(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al listar documentos: " + e.getMessage());
        }
        return lista;
    }

    public DocumentoLegal obtenerReciboValidacion(int predioId) {
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
                    return mapearDocumento(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al consultar recibo: " + e.getMessage());
        }
        return null;
    }

    public boolean actualizarEstado(String docId, String nuevoEstado, String revisadoPorId) {
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

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al actualizar estado de documento: " + e.getMessage());
            return false;
        }
    }

    public boolean eliminar(String docId) {
        String sql = "DELETE FROM public.documentos WHERE id = ?::uuid;";
        try (Connection conn = DatabaseConnectionSupabase.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, docId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DocumentoDAO] Error al eliminar documento: " + e.getMessage());
            return false;
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
}

