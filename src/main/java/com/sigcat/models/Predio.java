package com.sigcat.models;

import java.time.LocalDate;
import java.util.List;

/**
 * Modelo de dominio que representa un predio catastral.
 * Estados posibles: PENDIENTE, APROBADO, RECHAZADO.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class Predio {

    public enum Estado {
        PENDIENTE,
        APROBADO,
        RECHAZADO
    }

    private int id;
    private int idPropietario;
    private int idFuncionario;
    private double areaCalculada;
    private String estado = "PENDIENTE";
    private LocalDate fechaRegistro;
    private List<Vertice> vertices;

    // Campos Hito 2
    private String codigoCatastral;
    private String nombre;
    private String direccion;
    private String usoSuelo;
    private String observaciones;
    private String fechaRevision;

    public Predio() {
        this.fechaRegistro = LocalDate.now();
    }

    public Predio(int idPropietario) {
        this.idPropietario = idPropietario;
        this.estado = "PENDIENTE";
        this.fechaRegistro = LocalDate.now();
    }

    // ─── Getters y Setters ───────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdPropietario() { return idPropietario; }
    public void setIdPropietario(int idPropietario) { this.idPropietario = idPropietario; }

    public int getPropietarioId() { return idPropietario; }
    public void setPropietarioId(int propietarioId) { this.idPropietario = propietarioId; }

    public int getIdFuncionario() { return idFuncionario; }
    public void setIdFuncionario(int idFuncionario) { this.idFuncionario = idFuncionario; }

    public double getAreaCalculada() { return areaCalculada; }
    public void setAreaCalculada(double areaCalculada) { this.areaCalculada = areaCalculada; }

    public double getArea() { return areaCalculada; }
    public void setArea(double area) { this.areaCalculada = area; }

    public String getEstado() { return estado != null ? estado : "PENDIENTE"; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setEstado(Estado estado) { this.estado = estado != null ? estado.name() : "PENDIENTE"; }

    public Estado getEstadoEnum() {
        try {
            return estado != null ? Estado.valueOf(estado.toUpperCase()) : Estado.PENDIENTE;
        } catch (IllegalArgumentException e) {
            return Estado.PENDIENTE;
        }
    }

    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public List<Vertice> getVertices() { return vertices; }
    public void setVertices(List<Vertice> vertices) { this.vertices = vertices; }

    public String getCodigoCatastral() { return codigoCatastral; }
    public void setCodigoCatastral(String codigoCatastral) { this.codigoCatastral = codigoCatastral; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getUsoSuelo() { return usoSuelo; }
    public void setUsoSuelo(String usoSuelo) { this.usoSuelo = usoSuelo; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(String fechaRevision) { this.fechaRevision = fechaRevision; }

    @Override
    public String toString() {
        return "Predio{id=" + id + ", area=" + areaCalculada + ", estado=" + estado + "}";
    }
}
