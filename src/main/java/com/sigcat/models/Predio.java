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
    private Estado estado;
    private LocalDate fechaRegistro;
    private List<Vertice> vertices;

    public Predio() {}

    public Predio(int idPropietario) {
        this.idPropietario = idPropietario;
        this.estado = Estado.PENDIENTE;
        this.fechaRegistro = LocalDate.now();
    }

    // ─── Getters y Setters ───────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdPropietario() { return idPropietario; }
    public void setIdPropietario(int idPropietario) { this.idPropietario = idPropietario; }

    public int getIdFuncionario() { return idFuncionario; }
    public void setIdFuncionario(int idFuncionario) { this.idFuncionario = idFuncionario; }

    public double getAreaCalculada() { return areaCalculada; }
    public void setAreaCalculada(double areaCalculada) { this.areaCalculada = areaCalculada; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public List<Vertice> getVertices() { return vertices; }
    public void setVertices(List<Vertice> vertices) { this.vertices = vertices; }

    @Override
    public String toString() {
        return "Predio{id=" + id + ", area=" + areaCalculada + ", estado=" + estado + "}";
    }
}
