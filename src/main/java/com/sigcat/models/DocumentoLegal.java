package com.sigcat.models;

import java.time.LocalDateTime;

/**
 * Modelo de datos para documentos legales y recibos de validación catastral.
 * Vinculado con la tabla 'documentos' en Supabase y archivos en Supabase Storage.
 */
public class DocumentoLegal {

    private String id;
    private int predioId;
    private String propietarioId;
    private String tipoDocumento; // ESCRITURA_PUBLICA, RECIBO_VALIDACION, CERTIFICADO_CATASTRO, etc.
    private String nombreArchivo;
    private String storagePath;
    private String storageUrl;
    private String estado;        // PENDIENTE_REVISION, APROBADO, RECHAZADO
    private String descripcion;
    private LocalDateTime subidoEn;
    private LocalDateTime revisadoEn;
    private String revisadoPor;

    public DocumentoLegal() {
        this.estado = "PENDIENTE_REVISION";
    }

    public DocumentoLegal(int predioId, String tipoDocumento, String nombreArchivo, String storagePath, String storageUrl) {
        this.predioId = predioId;
        this.tipoDocumento = tipoDocumento;
        this.nombreArchivo = nombreArchivo;
        this.storagePath = storagePath;
        this.storageUrl = storageUrl;
        this.estado = "PENDIENTE_REVISION";
    }

    // Getters y Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPredioId() {
        return predioId;
    }

    public void setPredioId(int predioId) {
        this.predioId = predioId;
    }

    public String getPropietarioId() {
        return propietarioId;
    }

    public void setPropietarioId(String propietarioId) {
        this.propietarioId = propietarioId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDateTime getSubidoEn() {
        return subidoEn;
    }

    public void setSubidoEn(LocalDateTime subidoEn) {
        this.subidoEn = subidoEn;
    }

    public LocalDateTime getRevisadoEn() {
        return revisadoEn;
    }

    public void setRevisadoEn(LocalDateTime revisadoEn) {
        this.revisadoEn = revisadoEn;
    }

    public String getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(String revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    @Override
    public String toString() {
        return "DocumentoLegal{" +
                "predioId=" + predioId +
                ", tipo='" + tipoDocumento + '\'' +
                ", archivo='" + nombreArchivo + '\'' +
                ", estado='" + estado + '\'' +
                '}';
    }
}
