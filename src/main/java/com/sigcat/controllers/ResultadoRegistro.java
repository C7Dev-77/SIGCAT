package com.sigcat.controllers;

/**
 * Resultado del proceso de cerrar y registrar un polígono: cuánto área
 * dio, si hubo solapamiento, y si el guardado en base de datos fue exitoso.
 * Se usa para traer de vuelta el resultado desde el hilo de fondo (Task)
 * hacia el hilo de la interfaz.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class ResultadoRegistro {

    private final double area;
    private final boolean solapa;
    private final boolean guardadoExitoso;
    private final String mensajeError;

    public ResultadoRegistro(double area, boolean solapa, boolean guardadoExitoso, String mensajeError) {
        this.area = area;
        this.solapa = solapa;
        this.guardadoExitoso = guardadoExitoso;
        this.mensajeError = mensajeError;
    }

    public double getArea() { return area; }
    public boolean isSolapa() { return solapa; }
    public boolean isGuardadoExitoso() { return guardadoExitoso; }
    public String getMensajeError() { return mensajeError; }
}