package com.sigcat.models;

import java.time.LocalDateTime;

/**
 * Modelo de datos para el Historial de Auditoría Catastral (Mejora #4).
 * Registra cada mutación, radicación, aprobación, rechazo o subida documental
 * conforme a las exigencias jurídicas del catastro multipropósito.
 */
public class AuditLog {

    private String id;
    private String entidad;       // PREDIO, DOCUMENTO, USUARIO
    private String entidadId;
    private String accion;          // CREACION, APROBACION, RECHAZO, MODIFICACION, ELIMINACION, SINCRONIZACION
    private String estadoAnterior;
    private String estadoNuevo;
    private String usuarioId;
    private String usuarioNombre;
    private String detalles;
    private LocalDateTime fecha;

    public AuditLog() {
        this.fecha = LocalDateTime.now();
    }

    public AuditLog(String entidad, String entidadId, String accion, String estadoAnterior, String estadoNuevo,
                    String usuarioId, String usuarioNombre, String detalles) {
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.detalles = detalles;
        this.fecha = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntidad() { return entidad; }
    public void setEntidad(String entidad) { this.entidad = entidad; }

    public String getEntidadId() { return entidadId; }
    public void setEntidadId(String entidadId) { this.entidadId = entidadId; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(String estadoAnterior) { this.estadoAnterior = estadoAnterior; }

    public String getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(String estadoNuevo) { this.estadoNuevo = estadoNuevo; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNombre() { return usuarioNombre; }
    public void setUsuarioNombre(String usuarioNombre) { this.usuarioNombre = usuarioNombre; }

    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return "AuditLog{" +
                "entidad='" + entidad + '\'' +
                ", accion='" + accion + '\'' +
                ", usuario='" + usuarioNombre + '\'' +
                ", fecha=" + fecha +
                '}';
    }
}

