package com.sigcat.controllers;

import com.sigcat.models.Predio;

/**
 * Fila de la tabla de solicitudes del panel del Funcionario.
 * Combina los datos del Predio con el nombre del propietario
 * (que vive en la tabla usuarios, no en predios).
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class SolicitudRow {

    private final Predio predio;
    private final String nombrePropietario;

    public SolicitudRow(Predio predio, String nombrePropietario) {
        this.predio = predio;
        this.nombrePropietario = nombrePropietario;
    }

    public int getId() {
        return predio.getId();
    }

    public String getPropietario() {
        return nombrePropietario;
    }

    public double getArea() {
        return predio.getAreaCalculada();
    }

    public String getEstado() {
        return predio.getEstado().name();
    }

    public String getFecha() {
        return predio.getFechaRegistro().toString();
    }

    public Predio getPredio() {
        return predio;
    }
}