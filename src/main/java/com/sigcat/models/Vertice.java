package com.sigcat.models;

/**
 * Modelo de dominio que representa un vértice del polígono de un predio.
 * Los vértices se ordenan por el campo 'orden' para construir el polígono.
 *
 * Responsable: Aldo Ibañez (Persistencia)
 */
public class Vertice {

    private int id;
    private int idPredio;
    private int orden;
    private double coordX;
    private double coordY;

    public Vertice() {}

    public Vertice(int idPredio, int orden, double coordX, double coordY) {
        this.idPredio = idPredio;
        this.orden = orden;
        this.coordX = coordX;
        this.coordY = coordY;
    }

    // ─── Getters y Setters ───────────────────────────────────────
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdPredio() { return idPredio; }
    public void setIdPredio(int idPredio) { this.idPredio = idPredio; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public double getCoordX() { return coordX; }
    public void setCoordX(double coordX) { this.coordX = coordX; }

    public double getCoordY() { return coordY; }
    public void setCoordY(double coordY) { this.coordY = coordY; }

    @Override
    public String toString() {
        return "Vertice{orden=" + orden + ", x=" + coordX + ", y=" + coordY + "}";
    }
}
