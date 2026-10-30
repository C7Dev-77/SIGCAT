package com.sigcat.geometry;

import com.sigcat.models.Vertice;
import java.util.List;

/**
 * Calculadora geométrica basada en la Fórmula de Gauss (Algoritmo de Shoelace / Lazada)
 * para determinar el área exacta y el perímetro de polígonos catastrales cerrados.
 */
public class ShoelaceCalculator {

    /**
     * Calcula el área de un polígono definido por una lista ordenada de vértices.
     * La fórmula es: Area = 1/2 * |Σ (x_i * y_{i+1} - x_{i+1} * y_i)|
     *
     * @param vertices Lista ordenada de vértices que conforman el predio.
     * @return Área calculada en unidades cuadradas (valor siempre positivo).
     * @throws IllegalArgumentException si el polígono tiene menos de 3 vértices.
     */
    public static double calcularArea(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("Un polígono catastral válido requiere al menos 3 vértices.");
        }

        List<Vertice> ordenados = vertices;
        if (vertices.stream().anyMatch(v -> v.getOrden() > 0)) {
            ordenados = vertices.stream()
                    .sorted(java.util.Comparator.comparingInt(Vertice::getOrden))
                    .toList();
        }

        int n = ordenados.size();
        double suma = 0.0;

        for (int i = 0; i < n; i++) {
            Vertice actual = ordenados.get(i);
            Vertice siguiente = ordenados.get((i + 1) % n); // Cierra el polígono conectando el último con el primero

            suma += (actual.getX() * siguiente.getY()) - (siguiente.getX() * actual.getY());
        }

        return Math.abs(suma) / 2.0;
    }

    /**
     * Calcula el perímetro total del polígono sumando las distancias euclidianas entre vértices consecutivos.
     *
     * @param vertices Lista ordenada de vértices.
     * @return Perímetro total en unidades lineales.
     */
    public static double calcularPerimetro(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 2) {
            return 0.0;
        }

        List<Vertice> ordenados = vertices;
        if (vertices.stream().anyMatch(v -> v.getOrden() > 0)) {
            ordenados = vertices.stream()
                    .sorted(java.util.Comparator.comparingInt(Vertice::getOrden))
                    .toList();
        }

        int n = ordenados.size();
        double perimetro = 0.0;

        for (int i = 0; i < n; i++) {
            Vertice actual = ordenados.get(i);
            Vertice siguiente = ordenados.get((i + 1) % n);

            double dx = siguiente.getX() - actual.getX();
            double dy = siguiente.getY() - actual.getY();
            perimetro += Math.hypot(dx, dy);
        }

        return perimetro;
    }

    /**
     * Calcula el centroide (centro geométrico) de un polígono para ubicar etiquetas en el mapa.
     *
     * @param vertices Lista de vértices del predio.
     * @return Un objeto Vertice que representa el punto central (X, Y).
     */
    public static Vertice calcularCentroide(List<Vertice> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return new Vertice(0, 0, 0);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        for (Vertice v : vertices) {
            sumX += v.getX();
            sumY += v.getY();
        }

        return new Vertice(sumX / vertices.size(), sumY / vertices.size(), 0);
    }
}
