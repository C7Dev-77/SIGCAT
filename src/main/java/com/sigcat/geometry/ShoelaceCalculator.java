package com.sigcat.geometry;

import com.sigcat.models.Vertice;

import java.util.Comparator;
import java.util.List;

/**
 * Calculadora geométrica basada en el algoritmo de Shoelace (Gauss).
 * Calcula el área de un polígono simple a partir de sus vértices ordenados.
 *
 * Responsable: Andrés Diaz (Desarrollador Core)
 */
public class ShoelaceCalculator {

    /**
     * Calcula el área de un polígono usando la fórmula de Shoelace.
     *
     * @param vertices Lista de vértices del polígono (no necesitan venir
     *                 pre-ordenados; se ordenan internamente por el campo 'orden').
     * @return El área en las mismas unidades cuadradas de las coordenadas.
     * @throws IllegalArgumentException si hay menos de 3 vértices.
     */
    public static double calcularArea(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException(
                "Se necesitan al menos 3 vértices para formar un polígono.");
        }

        List<Vertice> ordenados = vertices.stream()
            .sorted(Comparator.comparingInt(Vertice::getOrden))
            .toList();

        double sumatoria = 0.0;
        int n = ordenados.size();

        for (int i = 0; i < n; i++) {
            Vertice actual = ordenados.get(i);
            Vertice siguiente = ordenados.get((i + 1) % n); // vuelve al primero al final

            sumatoria += (actual.getCoordX() * siguiente.getCoordY())
                       - (siguiente.getCoordX() * actual.getCoordY());
        }

        return Math.abs(sumatoria) / 2.0;
    }

    /**
     * Calcula el perímetro sumando la distancia entre vértices consecutivos.
     */
    public static double calcularPerimetro(List<Vertice> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException(
                "Se necesitan al menos 3 vértices para formar un polígono.");
        }

        List<Vertice> ordenados = vertices.stream()
            .sorted(Comparator.comparingInt(Vertice::getOrden))
            .toList();

        double perimetro = 0.0;
        int n = ordenados.size();

        for (int i = 0; i < n; i++) {
            Vertice actual = ordenados.get(i);
            Vertice siguiente = ordenados.get((i + 1) % n);

            double dx = siguiente.getCoordX() - actual.getCoordX();
            double dy = siguiente.getCoordY() - actual.getCoordY();
            perimetro += Math.sqrt(dx * dx + dy * dy);
        }

        return perimetro;
    }
}